package moze_intel.projecte.utils;

import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.item.ItemStack;

/** Server-only bridge between exact player balances and legacy double item storage. */
public final class LegacyEMCTransfer {
    private LegacyEMCTransfer() {}

    public static void charge(TransmutationInventory inv, ItemStack stack, IItemEmc item) {
        transfer(inv, stack, item, true);
    }

    public static void discharge(TransmutationInventory inv, ItemStack stack, IItemEmc item) {
        transfer(inv, stack, item, false);
    }

    private static void transfer(TransmutationInventory inv, ItemStack stack, IItemEmc item,
                                 boolean charging) {
        if (inv.player.worldObj.isRemote) return;
        Transmutation.requireServer(inv.player);
        ExactEMC balance = inv.getEmcExact();
        ItemStack candidate = stack.copy();
        double before = item.getStoredEmc(candidate);
        if (!finiteNonnegative(before)) return;
        ExactEMC stored = ExactEMC.fromLegacyDouble(before);
        double maximum = 0;
        double requested;
        if (charging) {
            maximum = item.getMaximumEmc(candidate);
            if (!finiteNonnegative(maximum) || maximum <= before || balance.signum() <= 0) return;
            ExactEMC room = ExactEMC.fromLegacyDouble(maximum).subtract(stored);
            ExactEMC budget = balance.compareTo(room) < 0 ? balance : room;
            requested = budget.toLegacyDouble();
            // The legacy compatibility conversion can round upward. Never request more than the budget.
            while (requested > 0 && ExactEMC.fromLegacyDouble(requested).compareTo(budget) > 0)
                requested = Math.nextAfter(requested, 0.0);
        } else {
            requested = before;
        }
        if (requested <= 0) return;

        // Never mutate the real stack until the measured transfer and resulting balance are valid.
        if (charging) item.addEmc(candidate, requested);
        else item.extractEmc(candidate, requested);
        if (candidate.getItem() != stack.getItem() || candidate.stackSize != stack.stackSize) return;
        double after = item.getStoredEmc(candidate);
        if (!finiteNonnegative(after) || (charging && after > maximum)) return;
        ExactEMC newStored = ExactEMC.fromLegacyDouble(after);
        ExactEMC moved = charging ? newStored.subtract(stored) : stored.subtract(newStored);
        // Measure the actual storage delta, not the requested amount or the legacy return value.
        if (moved.signum() <= 0) return;
        if (charging && moved.compareTo(balance) > 0) return;
        ExactEMC nextBalance = charging ? balance.subtract(moved) : balance.add(moved);
        ExactEMCCodec.validateBalance(nextBalance);
        Transmutation.setEmcExact(inv.player, nextBalance);
        stack.setItemDamage(candidate.getItemDamage());
        stack.setTagCompound(candidate.hasTagCompound()
            ? (net.minecraft.nbt.NBTTagCompound) candidate.stackTagCompound.copy() : null);
    }

    private static boolean finiteNonnegative(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }
}
