package moze_intel.projecte.primordial;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FluidSimpleStack;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class EmcTransactions {
    private static final Set<UUID> dirty = new HashSet<>();
    public static double itemPrice(ItemStack s) {
        if (s == null || moze_intel.projecte.integration.helpers.GTItemHelper.isGTfluidDisplay(s)) return 0;
        return positive(EMCHelper.getEmcValue(s));
    }
    public static double fluidPrice(FluidStack f) {
        if (f == null || f.getFluid() == null) return 0;
        FluidSimpleStack key = new FluidSimpleStack(f);
        return EMCMapper.mapContains(key) ? positive(EMCMapper.getEmcValue(key)) : 0;
    }
    private static double positive(double d) { return Double.isFinite(d) && d > 0 ? d : 0; }
    public static int affordable(EntityPlayer p, double unit, int limit) {
        if (unit <= 0 || !Double.isFinite(unit) || limit <= 0) return 0;
        double balance = Transmutation.getEmc(p);
        if (!Double.isFinite(balance) || balance <= 0) return 0;
        return (int) Math.min(limit, Math.floor(balance / unit));
    }
    public static boolean spend(EntityPlayer p, double amount) {
        if (!Double.isFinite(amount) || amount < 0 || p.worldObj.isRemote) return false;
        if (amount == 0) return true;
        double old = Transmutation.getEmc(p), next = old - amount;
        if (!Double.isFinite(old) || next < 0 || next >= old) return false;
        Transmutation.setEmc(p, next); dirty.add(p.getUniqueID()); return true;
    }
    public static boolean creditOnline(EntityPlayer p, double amount) {
        double old = Transmutation.getEmc(p), next = old + amount;
        if (p.worldObj.isRemote || !Double.isFinite(amount) || amount <= 0 || !Double.isFinite(next) || next <= old) return false;
        Transmutation.setEmc(p, next); dirty.add(p.getUniqueID()); return true;
    }
    public static void refund(EntityPlayer p, double value) {
        if (value > 0 && !creditOnline(p, value)) credit(p.getUniqueID(), value);
    }
    public static boolean credit(UUID owner, double value) { return PrimordialSavedData.get().credit(owner, value); }
    public static void sync(EntityPlayerMP p) { if (dirty.remove(p.getUniqueID())) Transmutation.sync(p); }
    public static void clear() { dirty.clear(); }
    private EmcTransactions() {}
}
