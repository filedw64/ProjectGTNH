package moze_intel.projecte.integration.ae2;

import moze_intel.projecte.math.ExactEMC;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PEGeneralPurposeUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.storage.IMEInventoryHandler", modid = "appliedenergistics2")
// AppliedE persistent recovery v4
public class EMCInventoryHandler implements IMEInventoryHandler<IAEItemStack> {
    private final TileMEEMCLink tile;
    private UUID ownerUUID;
    private String ownerName;
    private AccessRestriction access = AccessRestriction.READ_WRITE;
    private int priority;
    private int lastIteration = Integer.MIN_VALUE;


    // AppliedE core hardening v3
    private java.lang.ref.WeakReference<appeng.api.storage.ISaveProvider> storageHost = new java.lang.ref.WeakReference<>(null);
    private volatile java.util.List<IAEItemStack> legacySnapshot = java.util.Collections.emptyList();
    public void setStorageHost(appeng.api.storage.ISaveProvider host) { storageHost = new java.lang.ref.WeakReference<>(host); }
    private boolean budgetSuppressed() {
        appeng.api.storage.ISaveProvider host = storageHost.get();
        if (!(host instanceof appeng.api.networking.IGridHost)) return false;
        appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) host)
            .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
        if (node == null || node.getGrid() == null) return false;
        EMCKnowledgeGridCache cache = node.getGrid().getCache(EMCKnowledgeGridCache.class);
        return cache != null && cache.ownsBudget(ownerUUID);
    }
    public void refreshLegacySnapshot() {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        if (budgetSuppressed() || !canRead()) { legacySnapshot = java.util.Collections.emptyList(); return; }
        EntityPlayer player = getPlayer();
        if (player == null) { legacySnapshot = java.util.Collections.emptyList(); return; }
        java.util.List<IAEItemStack> entries = new java.util.ArrayList<>();
        IItemList<IAEItemStack> unique = AEApi.instance().storage().createItemList();
        ExactEMC balance = Transmutation.getEmcExact(player);
        for (ItemStack learned : Transmutation.getKnowledge(player)) {
            if (!filterMatches(learned)) continue;
            ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(learned);
            if (cost.signum() <= 0) continue;
            long count = balance.affordableUnits(cost).min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
            IAEItemStack entry = AEApi.instance().storage().createItemStack(learned);
            if (count > 0 && entry != null && unique.findPrecise(entry) == null) {
                entry.setStackSize(count); unique.add(entry); entries.add(entry);
            }
        }
        legacySnapshot = java.util.Collections.unmodifiableList(entries);
    }
    private IAEItemStack simulatedLegacyExtraction(IAEItemStack request) {
        if (request == null || request.getStackSize() <= 0) return null;
        for (IAEItemStack entry : legacySnapshot) if (entry.isSameType(request)) {
            IAEItemStack result = request.copy(); result.setStackSize(Math.min(request.getStackSize(), entry.getStackSize()));
            return result;
        }
        return null;
    }

    public EMCInventoryHandler() { this(null); }
    public EMCInventoryHandler(TileMEEMCLink tile) { this.tile = tile; }

    public void setOwner(UUID uuid, String name) {
        if (!Objects.equals(ownerUUID, uuid)) lastIteration = Integer.MIN_VALUE;
        ownerUUID = uuid;
        ownerName = name;
    }
    public UUID getOwnerUUID() { return ownerUUID; }
    public EntityPlayer getPlayer() { return resolveOnlinePlayer(ownerUUID); }

    public static EntityPlayer resolveOnlinePlayer(UUID uuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (uuid == null || server == null || server.getConfigurationManager() == null) return null;
        for (Object raw : server.getConfigurationManager().playerEntityList) {
            if (raw instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) raw;
                if (uuid.equals(player.getUniqueID()) && !player.isDead && player.worldObj != null
                        && !player.worldObj.isRemote) return player;
            }
        }
        return null;
    }
    public void setAccess(AccessRestriction value) { access = value == null ? AccessRestriction.READ_WRITE : value; }
    public void setPriority(int value) { priority = value; }

    public static boolean matchesPrecision(ItemStack filter, ItemStack target, int precision) {
        if (filter == null || target == null) return false;
        if (precision == 1) return filter.getItem() == target.getItem();
        boolean exact = filter.getItem() == target.getItem() && filter.getItemDamage() == target.getItemDamage()
            && ItemStack.areItemStackTagsEqual(filter, target);
        if (precision != 2 || exact) return exact;
        for (int a : OreDictionary.getOreIDs(filter)) for (int b : OreDictionary.getOreIDs(target))
            if (a == b) return true;
        return false;
    }

    public boolean filterMatches(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (tile == null || tile.getFilterMode() == 0) return true;
        boolean found = false;
        for (ItemStack filter : tile.getFilterSlots()) {
            if (matchesPrecision(filter, stack, tile.getFilterPrecision())) { found = true; break; }
        }
        return tile.getFilterMode() == 1 ? found : !found;
    }

    private boolean canRead() { return access == AccessRestriction.READ || access == AccessRestriction.READ_WRITE; }
    private boolean canWrite() { return access == AccessRestriction.WRITE || access == AccessRestriction.READ_WRITE; }

    // Strict learned-stack identity prevents constructing arbitrary NBT variants when ProjectE NBT matching is disabled.
    private boolean knowsExactly(EntityPlayer player, ItemStack stack) {
        for (ItemStack learned : Transmutation.getKnowledge(player))
            if (matchesPrecision(learned, stack, 0)) return true;
        return false;
    }


    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource source) {
        if (input == null || input.getStackSize() <= 0 || !canWrite()) return input;
        if (!moze_intel.projecte.events.TickEvents.isServerThread() || budgetSuppressed()) return input;
        EntityPlayer player = getPlayer();
        if (player == null) return input;
        if (source instanceof appeng.api.networking.security.MachineSource
                && ((appeng.api.networking.security.MachineSource) source).via instanceof TileMEEMCLink) return input;
        ItemStack stack = input.getItemStack();
        if (stack == null || stack.getItem() == AE2Integration.itemEMCResource
                || stack.getItem() == AE2Integration.itemEMCTransmutationPattern
                || stack.getItem() == AE2Integration.itemEMCRecoveryBundle || !filterMatches(stack)) return input;
        ExactEMC value = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(stack);
        if (value.signum() <= 0) return input;
        Transmutation.requireServer(player);
        synchronized (PEGeneralPurposeUtils.getPlayerLock(ownerUUID)) {
            ExactEMC before = Transmutation.getEmcExact(player);
            ExactEMC after = before.add(value.multiply(input.getStackSize()));
            try { moze_intel.projecte.math.ExactEMCCodec.validateBalance(after); }
            catch (IllegalArgumentException ex) { return input; }
            if (mode == Actionable.MODULATE) {
                String intent = EMCRecoveryLedger.get().prepareKnowledge(ownerUUID, stack);
                EMCRecoveryLedger.commitBalances(java.util.Collections.singletonList(player),
                    java.util.Collections.singletonList(before), java.util.Collections.singletonList(after));
                EMCRecoveryLedger.get().commitKnowledge(intent);
            }
        }
        if (mode == Actionable.MODULATE) {
            AE2Integration.notifyHandlersForPlayer(ownerUUID);
            try { refreshLegacySnapshot(); }
            catch (RuntimeException failure) {
                moze_intel.projecte.utils.PELogger.logWarn("Legacy EMC deposit committed; snapshot refresh deferred");
            }
        }
        return null;
    }
    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource source) {
        if (request == null || request.getStackSize() <= 0 || !canRead()) return null;
        if (mode == Actionable.SIMULATE) {
            if (moze_intel.projecte.events.TickEvents.isServerThread()) refreshLegacySnapshot();
            return simulatedLegacyExtraction(request);
        }
        moze_intel.projecte.events.TickEvents.requireServerThread();
        if (budgetSuppressed()) return null;
        EntityPlayer player = getPlayer();
        if (player == null) return null;
        ItemStack stack = request.getItemStack();
        if (!filterMatches(stack) || !knowsExactly(player, stack)) return null;
        ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(stack);
        if (cost.signum() <= 0) return null;
        Transmutation.requireServer(player);
        IAEItemStack result;
        synchronized (PEGeneralPurposeUtils.getPlayerLock(ownerUUID)) {
            ExactEMC before = Transmutation.getEmcExact(player);
            long count = before.affordableUnits(cost).min(java.math.BigInteger.valueOf(request.getStackSize())).longValue();
            if (count <= 0) return null;
            EMCRecoveryLedger.commitBalances(java.util.Collections.singletonList(player),
                java.util.Collections.singletonList(before),
                java.util.Collections.singletonList(before.subtract(cost.multiply(count))));
            result = request.copy(); result.setStackSize(count);
        }
        AE2Integration.notifyHandlersForPlayer(ownerUUID);
        try { refreshLegacySnapshot(); }
        catch (RuntimeException failure) {
            moze_intel.projecte.utils.PELogger.logWarn("Legacy EMC extraction committed; snapshot refresh deferred");
        }
        return result;
    }
    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        if (!moze_intel.projecte.events.TickEvents.isServerThread()) {
            for (IAEItemStack entry : legacySnapshot) out.add(entry.copy());
            return out;
        }
        if (budgetSuppressed()) return out;
        // A single cell handler may appear in multiple slots: report its balance only once per AE2 query.
        if (lastIteration == iteration) return out;
        lastIteration = iteration;
        if (!canRead()) return out;
        EntityPlayer player = getPlayer();
        if (player == null) return out;
        ExactEMC balance = Transmutation.getEmcExact(player);
        if (balance.signum() <= 0) return out;
        IItemList<IAEItemStack> unique = AEApi.instance().storage().createItemList();
        for (ItemStack stack : Transmutation.getKnowledge(player)) {
            if (!filterMatches(stack)) continue;
            ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(stack);
            if (cost.signum() <= 0) continue;
            long count = balance.affordableUnits(cost).min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
            if (count <= 0) continue;
            IAEItemStack entry = AEApi.instance().storage().createItemStack(stack);
            if (entry != null && unique.findPrecise(entry) == null) {
                entry.setStackSize(count);
                unique.add(entry);
            }
        }
        for (IAEItemStack entry : unique) out.add(entry);
        return out;
    }

    @Override public IAEItemStack getAvailableItem(IAEItemStack request, int iteration) {
        if (request == null || !canRead()) return null;
        if (!moze_intel.projecte.events.TickEvents.isServerThread()) {
            for (IAEItemStack entry : legacySnapshot) if (entry.isSameType(request)) return entry.copy();
            return null;
        }
        if (budgetSuppressed()) return null;
        EntityPlayer player = getPlayer();
        if (player == null) return null;
        ItemStack stack = request.getItemStack();
        if (!filterMatches(stack) || !knowsExactly(player, stack)) return null;
        ExactEMC cost = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(stack);
        if (cost.signum() <= 0) return null;
        long count = Transmutation.getEmcExact(player).affordableUnits(cost)
            .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
        if (count <= 0) return null;
        IAEItemStack result = request.copy();
        result.setStackSize(count);
        return result;
    }
    @Override public StorageChannel getChannel() { return StorageChannel.ITEMS; }
    @Override public AccessRestriction getAccess() { return access; }
    @Override public boolean isPrioritized(IAEItemStack stack) { return false; }
    @Override public boolean canAccept(IAEItemStack stack) {
        if (!moze_intel.projecte.events.TickEvents.isServerThread() || budgetSuppressed()) return false;
        return stack != null && stack.getItemStack().getItem() != AE2Integration.itemEMCRecoveryBundle
            && stack.getItemStack().getItem() != AE2Integration.itemEMCResource
            && stack.getItemStack().getItem() != AE2Integration.itemEMCTransmutationPattern
            && getPlayer() != null && canWrite() && filterMatches(stack.getItemStack())
            && moze_intel.projecte.utils.EMCHelper.getEmcValueExact(stack.getItemStack()).signum() > 0;
    }
    @Override public int getPriority() { return priority; }
    @Override public int getSlot() { return 0; }
    @Override public boolean validForPass(int pass) { return true; }
}
