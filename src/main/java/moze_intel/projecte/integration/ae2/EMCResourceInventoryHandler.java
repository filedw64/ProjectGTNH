package moze_intel.projecte.integration.ae2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PEGeneralPurposeUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Canonical radix digits, not multiple overlapping representations of the same budget. */
// AppliedE persistent recovery v4
public final class EMCResourceInventoryHandler implements IMEInventoryHandler<IAEItemStack> {
    private final EMCKnowledgeGridCache cache;
    // AppliedE core hardening v3
    private final ThreadLocal<Integer> lastIteration = new ThreadLocal<>();
    public EMCResourceInventoryHandler(EMCKnowledgeGridCache cache) { this.cache = cache; }

    // AppliedE core refinement v2
    private static long bounded(BigInteger value) {
        return value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
    private static BigInteger whole(ExactEMC value) {
        return value.getNumerator().divide(value.getDenominator());
    }
    /** All query and extraction paths use these exact same canonical digits. */
    private static long digit(ExactEMC value, int tier) {
        BigInteger shifted = whole(value).shiftRight(8 * (tier - 1));
        return tier == ItemEMCResource.MAX_TIERS ? bounded(shifted)
            : shifted.and(BigInteger.valueOf(ItemEMCResource.RADIX - 1)).longValue();
    }
    @Override public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        if (Integer.valueOf(iteration).equals(lastIteration.get())) return out;
        lastIteration.set(iteration);
        ExactEMC available = cache.snapshot().total;
        for (int tier = 1; tier <= ItemEMCResource.MAX_TIERS; tier++) {
            long count = digit(available, tier);
            if (count <= 0) continue;
            IAEItemStack stack = AEApi.instance().storage().createItemStack(ItemEMCResource.stack(tier));
            stack.setStackSize(count);
            out.add(stack);
        }
        return out;
    }
    @Override public IAEItemStack getAvailableItem(IAEItemStack request, int iteration) {
        if (request == null) return null;
        int tier = ItemEMCResource.tier(request.getItemStack());
        if (tier == 0) return null;
        long count = digit(cache.snapshot().total, tier);
        if (count <= 0) return null;
        IAEItemStack result = request.copy(); result.setStackSize(count); return result;
    }
    private interface LockedOperation<T> { T run(); }
    private void refreshAfterCommit() {
        try { cache.refreshSnapshot(); }
        catch (RuntimeException failure) {
            moze_intel.projecte.utils.PELogger.logWarn("EMC committed; snapshot refresh deferred to grid tick");
        }
    }
    /** cache.players returns UUID-sorted, deduplicated players. Never acquire in another order. */
    private static <T> T withLocks(List<EntityPlayer> players, int index, LockedOperation<T> operation) {
        if (index == players.size()) return operation.run();
        synchronized (PEGeneralPurposeUtils.getPlayerLock(players.get(index).getUniqueID())) {
            return withLocks(players, index + 1, operation);
        }
    }
    @Override public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode,
            BaseActionSource source) {
        if (request == null || request.getStackSize() <= 0) return null;
        final int tier = ItemEMCResource.tier(request.getItemStack());
        if (tier == 0) return null;
        if (mode == Actionable.SIMULATE) {
            long count = Math.min(digit(cache.snapshot().total, tier), request.getStackSize());
            if (count <= 0) return null;
            IAEItemStack result = request.copy(); result.setStackSize(count); return result;
        }
        moze_intel.projecte.events.TickEvents.requireServerThread();
        final List<EntityPlayer> players = cache.players(false, false);
        if (players.isEmpty()) return null;
        for (EntityPlayer player : players) Transmutation.requireServer(player);
        IAEItemStack extracted = withLocks(players, 0, new LockedOperation<IAEItemStack>() {
            @Override public IAEItemStack run() {
                List<ExactEMC> before = new ArrayList<>();
                ExactEMC available = ExactEMC.ZERO;
                for (EntityPlayer player : players) {
                    ExactEMC balance = Transmutation.getEmcExact(player);
                    before.add(balance); available = available.add(balance);
                }
                long count = Math.min(digit(available, tier), request.getStackSize());
                if (count <= 0) return null;
                ExactEMC remaining = ExactEMC.of(ItemEMCResource.unit(tier)).multiply(count);
                List<ExactEMC> after = new ArrayList<>();
                for (ExactEMC balance : before) {
                    ExactEMC debit = balance.compareTo(remaining) < 0 ? balance : remaining;
                    after.add(ExactEMCCodec.validateBalance(balance.subtract(debit)));
                    remaining = remaining.subtract(debit);
                }
                if (remaining.signum() != 0) return null;
                if (mode == Actionable.MODULATE) {
                    EMCRecoveryLedger.commitBalances(players, before, after);
                    cache.invalidateStorage();
                }
                IAEItemStack result = request.copy(); result.setStackSize(count); return result;
            }
        });
        if (extracted != null) refreshAfterCommit();
        return extracted;
    }
    @Override public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode,
            BaseActionSource source) {
        if (input == null || input.getStackSize() <= 0) return input;
        final ItemStack item = input.getItemStack();
        if (item == null || item.getItem() == null) return input;
        final int tier = ItemEMCResource.tier(item);
        if (tier == 0 && item.getItem() == AE2Integration.itemEMCResource) return input;
        if (item.getItem() == AE2Integration.itemEMCTransmutationPattern
                || item.getItem() == AE2Integration.itemEMCRecoveryBundle) return input;
        if (mode == Actionable.SIMULATE) {
            // Refund simulation uses immutable snapshot. Ordinary item deposit simulation stays main-thread only.
            if (tier > 0) return cache.snapshot().refunds ? null : input;
            if (!moze_intel.projecte.events.TickEvents.isServerThread()) return input;
        }
        moze_intel.projecte.events.TickEvents.requireServerThread();
        final List<EntityPlayer> players;
        ExactEMC unit;
        if (tier > 0) {
            // Already-paid resources may be returned even while normal item deposits are disabled.
            players = cache.players(true, true);
            unit = ExactEMC.of(ItemEMCResource.unit(tier));
        } else {
            if (source instanceof appeng.api.networking.security.MachineSource
                    && ((appeng.api.networking.security.MachineSource) source).via instanceof TileMEEMCLink)
                return input;
            EntityPlayer recipient = cache.itemRecipient(item);
            if (recipient == null) return input;
            players = java.util.Collections.singletonList(recipient);
            unit = moze_intel.projecte.utils.EMCHelper.getEmcValueExact(item);
            if (unit.signum() <= 0) return input;
        }
        if (players.isEmpty()) return input;
        final ExactEMC addition = unit.multiply(input.getStackSize());
        for (EntityPlayer player : players) Transmutation.requireServer(player);
        IAEItemStack rejected = withLocks(players, 0, new LockedOperation<IAEItemStack>() {
            @Override public IAEItemStack run() {
                for (EntityPlayer player : players) {
                    // Re-read inside the lock; the balance computed before locking is never committed.
                    ExactEMC next = Transmutation.getEmcExact(player).add(addition);
                    try { ExactEMCCodec.validateBalance(next); }
                    catch (IllegalArgumentException ex) { continue; }
                    if (mode == Actionable.MODULATE) {
                        String knowledgeIntent = tier == 0 ? EMCRecoveryLedger.get().prepareKnowledge(player.getUniqueID(), item) : null;
                        EMCRecoveryLedger.commitBalances(java.util.Collections.singletonList(player),
                            java.util.Collections.singletonList(Transmutation.getEmcExact(player)),
                            java.util.Collections.singletonList(next));
                        if (knowledgeIntent != null) EMCRecoveryLedger.get().commitKnowledge(knowledgeIntent);
                        cache.invalidateStorage();
                    }
                    return null;
                }
                return input;
            }
        });
        if (rejected == null && mode == Actionable.MODULATE) {
            refreshAfterCommit();
            // Ordinary item deposits have exactly one selected recipient; resource refunds do not learn knowledge.
            if (tier == 0) {
                cache.invalidatePatterns();
            }
        }
        return rejected;
    }
    @Override public StorageChannel getChannel() { return StorageChannel.ITEMS; }
    @Override public AccessRestriction getAccess() { return AccessRestriction.READ_WRITE; }
    @Override public boolean isPrioritized(IAEItemStack stack) {
        return stack != null && ItemEMCResource.tier(stack.getItemStack()) > 0;
    }
    @Override public boolean canAccept(IAEItemStack stack) {
        if (stack == null) return false;
        ItemStack item = stack.getItemStack();
        if (item == null || item.getItem() == null) return false;
        if (ItemEMCResource.tier(item) > 0) return cache.snapshot().refunds;
        if (!moze_intel.projecte.events.TickEvents.isServerThread()) return false;
        return item.getItem() != AE2Integration.itemEMCResource
            && item.getItem() != AE2Integration.itemEMCTransmutationPattern
            && item.getItem() != AE2Integration.itemEMCRecoveryBundle
            && cache.itemRecipient(item) != null
            && moze_intel.projecte.utils.EMCHelper.getEmcValueExact(item).signum() > 0;
    }
    @Override public int getPriority() { return cache.priority(); }
    @Override public int getSlot() { return 0; }
    @Override public boolean validForPass(int pass) { return true; }
}
