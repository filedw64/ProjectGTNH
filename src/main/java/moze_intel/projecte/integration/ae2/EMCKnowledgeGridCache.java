package moze_intel.projecte.integration.ae2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** No balances live here: UUIDs are deduplicated, balances remain in ProjectE player properties. */
public final class EMCKnowledgeGridCache implements IGridCache {
    private final IGrid grid;
    private final Map<TileMEEMCLink, IGridNode> nodes = new LinkedHashMap<>();
    private final EMCResourceInventoryHandler storage = new EMCResourceInventoryHandler(this);
    private final Map<UUID, ExactEMC> balances = new HashMap<>();
    private TileMEEMCLink leader;
    // AppliedE core refinement v2
    private boolean dirty = true;
    private boolean storageDirty = true;
    private boolean patternEventsNeeded = true;
    private List<TileMEEMCLink> previousActive = Collections.emptyList();
    private Map<IAEItemStack, BigInteger> patternCosts = Collections.emptyMap();
    private int ticks;
    private List<EMCTransmutationPattern> patterns = Collections.emptyList();

    // AppliedE core hardening v3
    public static final class ResourceSnapshot {
        public final ExactEMC total;
        public final boolean refunds;
        private ResourceSnapshot(ExactEMC total, boolean refunds) { this.total = total; this.refunds = refunds; }
    }
    private volatile ResourceSnapshot resourceSnapshot = new ResourceSnapshot(ExactEMC.ZERO, false);
    public ResourceSnapshot snapshot() { return resourceSnapshot; }
    public void refreshSnapshot() {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        resourceSnapshot = new ResourceSnapshot(total(), !players(true, true).isEmpty());
    }
    /** Presence, not just available balance, reserves this owner's legacy budget in this grid. */
    public boolean ownsBudget(UUID uuid) {
        if (uuid == null) return false;
        for (TileMEEMCLink tile : activeTiles())
            if (uuid.equals(tile.getOwnerUUID())) return true;
        return false;
    }
    /** Execution-time authorization. Rejected inputs remain inside CPU (verified in local 695). */
    public boolean authorizes(TileMEEMCLink tile, EMCTransmutationPattern requested) {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        if (storageFor(tile) == null) return false;
        appeng.api.storage.data.IAEItemStack[] outputs = requested.getCondensedOutputs();
        if (outputs.length != 1) return false;
        ItemStack target = outputs[0].getItemStack();
        int tier = ItemEMCResource.tier(target);
        if (tier > 0) return tier < ItemEMCResource.MAX_TIERS
            && requested.equals(EMCTransmutationPattern.split(tier + 1, requested.getPriority()));
        for (TileMEEMCLink provider : activeTiles()) {
            if (provider.getAccessMode() == 2 || !provider.acceptsTransmutationItem(target)) continue;
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(provider.getOwnerUUID());
            if (player == null) continue;
            boolean known = false;
            for (ItemStack learned : Transmutation.getKnowledge(player))
                if (EMCInventoryHandler.matchesPrecision(learned, target, 0)) { known = true; break; }
            if (!known) continue;
            ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(target);
            if (cost.signum() > 0 && cost.getDenominator().equals(BigInteger.ONE)
                    && cost.getNumerator().bitLength() <= 8 * ItemEMCResource.MAX_TIERS
                    && requested.equals(EMCTransmutationPattern.item(target, cost.getNumerator(), requested.getPriority())))
                return true;
        }
        return false;
    }

    public EMCKnowledgeGridCache(IGrid grid) { this.grid = grid; }
    public void invalidatePatterns() { dirty = true; storageDirty = true; }
    public void invalidateStorage() { storageDirty = true; }
    private List<TileMEEMCLink> activeTiles() {
        List<TileMEEMCLink> result = new ArrayList<>();
        for (Map.Entry<TileMEEMCLink, IGridNode> entry : nodes.entrySet())
            if (!entry.getKey().isInvalid() && entry.getValue().isActive() && entry.getKey().getOwnerUUID() != null)
                result.add(entry.getKey());
        return result;
    }
    public List<EntityPlayer> players(boolean deposit, boolean refund) {
        Map<UUID, EntityPlayer> unique = new HashMap<>();
        for (TileMEEMCLink tile : activeTiles()) {
            if (!refund && (deposit ? tile.getAccessMode() == 1 : tile.getAccessMode() == 2)) continue;
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(tile.getOwnerUUID());
            if (player != null) unique.put(player.getUniqueID(), player);
        }
        List<EntityPlayer> result = new ArrayList<>(unique.values());
        Collections.sort(result, new Comparator<EntityPlayer>() {
            @Override public int compare(EntityPlayer a, EntityPlayer b) { return a.getUniqueID().compareTo(b.getUniqueID()); }
        });
        return result;
    }
    public ExactEMC total() {
        ExactEMC result = ExactEMC.ZERO;
        for (EntityPlayer player : players(false, false)) result = result.add(Transmutation.getEmcExact(player));
        return result;
    }
    public EMCResourceInventoryHandler storageFor(TileMEEMCLink tile) {
        return tile == leader && nodes.containsKey(tile) && nodes.get(tile).isActive() ? storage : null;
    }
    public int priority() { return leader == null ? 0 : leader.getPriority(); }
    public EntityPlayer itemRecipient(ItemStack stack) {
        for (TileMEEMCLink tile : activeTiles()) {
            if (tile.getAccessMode() == 1 || !tile.acceptsTransmutationItem(stack)) continue;
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(tile.getOwnerUUID());
            if (player != null) return player;
        }
        return null;
    }
    public void provide(TileMEEMCLink tile, ICraftingProviderHelper helper) {
        if (storageFor(tile) == null) return;
        // Rebuild only during onUpdateTick, not recursively inside AE provider callbacks.
        for (EMCTransmutationPattern pattern : patterns) helper.addCraftingOption(tile, pattern);
    }

    private void rebuildPatterns() {
        Map<IAEItemStack, BigInteger> costs = new LinkedHashMap<>();
        for (TileMEEMCLink tile : activeTiles()) {
            if (tile.getAccessMode() == 2) continue;
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(tile.getOwnerUUID());
            if (player == null) continue;
            for (ItemStack learned : Transmutation.getKnowledge(player)) {
                if (!tile.acceptsTransmutationItem(learned)) continue;
                ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(learned);
                if (cost.signum() <= 0 || !cost.getDenominator().equals(BigInteger.ONE)
                        || cost.getNumerator().bitLength() > 8 * ItemEMCResource.MAX_TIERS) continue;
                IAEItemStack key = AEApi.instance().storage().createItemStack(learned);
                if (key == null) continue;
                key.setStackSize(1);
                costs.put(key, cost.getNumerator());
            }
        }
        boolean priorityChanged = !patterns.isEmpty() && patterns.get(0).getPriority() != priority();
        if (costs.equals(patternCosts) && !priorityChanged && !patterns.isEmpty()) return;
        List<EMCTransmutationPattern> result = new ArrayList<>();
        for (Map.Entry<IAEItemStack, BigInteger> entry : costs.entrySet())
            result.add(EMCTransmutationPattern.item(entry.getKey().getItemStack(), entry.getValue(), priority()));
        for (int tier = 2; tier <= ItemEMCResource.MAX_TIERS; tier++)
            result.add(EMCTransmutationPattern.split(tier, priority()));
        patternCosts = costs;
        patterns = result;
        patternEventsNeeded = true;
    }
    @Override public void onUpdateTick() {
        List<TileMEEMCLink> active = activeTiles();
        TileMEEMCLink nextLeader = active.isEmpty() ? null : active.get(0);
        if (!active.equals(previousActive) || nextLeader != leader) {
            previousActive = new ArrayList<>(active);
            leader = nextLeader;
            dirty = true; storageDirty = true; patternEventsNeeded = true;
        }
        if (++ticks % 20 == 0) {
            Map<UUID, ExactEMC> current = new HashMap<>();
            for (EntityPlayer player : players(false, true)) current.put(player.getUniqueID(), Transmutation.getEmcExact(player));
            if (!current.keySet().equals(balances.keySet())) { dirty = true; patternEventsNeeded = true; }
            if (!current.equals(balances)) { storageDirty = true; balances.clear(); balances.putAll(current); }
            // Also detect knowledge/EMC-value reloads without constructing patterns unnecessarily.
            dirty = true;
        }
        refreshSnapshot();
        if (dirty) { dirty = false; rebuildPatterns(); }
        if (storageDirty) {
            storageDirty = false;
            grid.postEvent(new MENetworkCellArrayUpdate());
        }
        if (patternEventsNeeded) {
            patternEventsNeeded = false;
            for (Map.Entry<TileMEEMCLink, IGridNode> entry : new ArrayList<>(nodes.entrySet()))
                grid.postEvent(new MENetworkCraftingPatternChange(entry.getKey(), entry.getValue()));
        }
    }
    @Override public void addNode(IGridNode node, IGridHost host) {
        if (host instanceof TileMEEMCLink) { nodes.put((TileMEEMCLink) host, node); invalidatePatterns(); patternEventsNeeded = true; }
    }
    @Override public void removeNode(IGridNode node, IGridHost host) {
        if (host instanceof TileMEEMCLink) { nodes.remove(host); invalidatePatterns(); patternEventsNeeded = true; }
    }
    @Override public void onSplit(IGridStorage destination) { invalidatePatterns(); patternEventsNeeded = true; }
    @Override public void onJoin(IGridStorage source) { invalidatePatterns(); patternEventsNeeded = true; }
    @Override public void populateGridStorage(IGridStorage destination) { /* Player properties own balances. */ }
}
