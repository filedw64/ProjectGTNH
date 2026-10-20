package moze_intel.projecte.primordial;

import java.util.*;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

public final class MachineTasks {
    private static int cursor;
    public static void tick() {
        PrimordialSavedData data = PrimordialSavedData.get();
        List<MachineConfig> configs = new ArrayList<>(data.machines.values());
        int total = configs.size() * 41;
        if (total == 0) return;
        long deadline = System.nanoTime() + 2000000L;
        for (int n = 0; n < Math.min(total, PrimordialConfig.machineRules) && System.nanoTime() < deadline; n++) {
            int index = Math.floorMod(cursor++, total); MachineConfig c = configs.get(index / 41);
            EntityPlayerMP p = PrimordialEvents.player(c.owner);
            if (p == null) continue;
            MachineConfig.Rule r = c.rules[index % 41]; long now = p.worldObj.getTotalWorldTime();
            if (!r.enabled || now < r.next) continue;
            r.next = now + r.interval;
            try {
                if (c.playerInventory()) { if (index % 41 < 36) inventory(p, r, index % 41); continue; }
                WorldServer w = DimensionManager.getWorld(c.dimension);
                if (w == null || !w.blockExists(c.x, c.y, c.z) || !c.block.equals(Block.blockRegistry.getNameForObject(w.getBlock(c.x, c.y, c.z)))) continue;
                TileEntity tile = w.getTileEntity(c.x, c.y, c.z);
                if (tile instanceof BlackholeTile) continue;
                int remaining = r.amount;
                for (int k = 0; k < r.ports.tagCount() && remaining > 0; k++) {
                    NBTTagCompound port = r.ports.getCompoundTagAt(k);
                    if (!MachinePorts.valid(tile, port)) continue;
                    int rule = index % 41;
                    int moved = rule >= 36 ? resource(p, tile, r, port, rule, remaining)
                        : rule < 18 ? item(p, tile, r, port, rule >= 9, remaining)
                        : fluid(p, tile, r, port, rule >= 27, remaining);
                    if (!r.keep) remaining -= moved;
                }
            } catch (RuntimeException failure) {
                r.enabled = false; data.markDirty();
                moze_intel.projecte.utils.PELogger.logWarn("Disabled failing configurator rule at %s: %s", c.key(), failure.toString());
            }
        }
    }
    private static void inventory(EntityPlayerMP p, MachineConfig.Rule r, int slot) {
        if (r.sample == null || !Transmutation.hasKnowledgeForStack(r.sample, p)) return;
        insert(p, p.inventory, slot, 6, r, r.amount);
    }
    private static int insert(EntityPlayerMP p, IInventory inv, int slot, int side, MachineConfig.Rule r, int limit) {
        ItemStack old = inv.getStackInSlot(slot);
        if (old != null && !MachinePorts.matches(old, r.sample)) return 0;
        int current = old == null ? 0 : old.stackSize;
        int count = Math.min(r.keep ? Math.max(0, r.amount - current) : limit,
            Math.min(inv.getInventoryStackLimit(), r.sample.getMaxStackSize()) - current);
        double price = EmcTransactions.itemPrice(r.sample); count = EmcTransactions.affordable(p, price, count);
        if (count <= 0 || !MachinePorts.accessible(inv, slot, side, r.sample, true) || !EmcTransactions.spend(p, count * price)) return 0;
        ItemStack next = r.sample.copy(); next.stackSize = current + count;
        inv.setInventorySlotContents(slot, next); inv.markDirty(); return count;
    }
    private static int item(EntityPlayerMP p, TileEntity tile, MachineConfig.Rule r, NBTTagCompound port, boolean output, int limit) {
        if (!(tile instanceof IInventory) || r.sample == null) return 0;
        IInventory inv = (IInventory) tile; int slot = port.getInteger("index"), side = port.getInteger("side");
        if (slot < 0 || slot >= inv.getSizeInventory()) return 0;
        if (!output) return Transmutation.hasKnowledgeForStack(r.sample, p) ? insert(p, inv, slot, side, r, limit) : 0;
        ItemStack old = inv.getStackInSlot(slot);
        if (!MachinePorts.matches(old, r.sample) || !MachinePorts.accessible(inv, slot, side, old, false)) return 0;
        int count = r.keep ? Math.max(0, old.stackSize - r.amount) : Math.min(limit, old.stackSize);
        double price = EmcTransactions.itemPrice(old);
        if (count <= 0 || price <= 0 || !PrimordialSavedData.get().canAcceptCredit(p.getUniqueID(), count * price)) return 0;
        // Remove first; use the actual returned stack, never a simulated count.
        ItemStack taken = inv.decrStackSize(slot, count);
        if (taken == null) return 0;
        if (!EmcTransactions.credit(p.getUniqueID(), EmcTransactions.itemPrice(taken) * taken.stackSize)) {
            ItemStack left = inv.getStackInSlot(slot);
            if (left == null) inv.setInventorySlotContents(slot, taken);
            else if (MachinePorts.matches(left, taken)) { left.stackSize += taken.stackSize; inv.setInventorySlotContents(slot, left); }
            else p.entityDropItem(taken, 0);
            return 0;
        }
        inv.markDirty(); return taken.stackSize;
    }
    private static int fluid(EntityPlayerMP p, TileEntity tile, MachineConfig.Rule r, NBTTagCompound port, boolean output, int limit) {
        if (!(tile instanceof IFluidHandler) || r.fluid == null) return 0;
        IFluidHandler handler = (IFluidHandler) tile; ForgeDirection side = ForgeDirection.getOrientation(port.getInteger("side"));
        FluidTankInfo[] tanks = handler.getTankInfo(side); int index = port.getInteger("index");
        if (tanks == null || index != 0 || tanks.length == 0) return 0;
        long totalStored = 0;
        for (FluidTankInfo tank : tanks) if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(r.fluid)) totalStored += Math.max(0, tank.fluid.amount);
        int current = (int) Math.min(Integer.MAX_VALUE, totalStored);
        int count = r.keep ? Math.max(0, output ? current - r.amount : r.amount - current) : limit;
        count = Math.min(count, 1000000); double price = EmcTransactions.fluidPrice(r.fluid);
        if (count <= 0 || price <= 0) return 0;
        FluidStack offer = r.fluid.copy(); offer.amount = count;
        if (output) {
            if (!PrimordialSavedData.get().canAcceptCredit(p.getUniqueID(), offer.amount * price)) return 0;
            FluidStack simulated = handler.drain(side, offer, false);
            if (simulated == null || !simulated.isFluidEqual(offer) || simulated.amount <= 0) return 0;
            offer.amount = Math.min(count, simulated.amount);
            FluidStack taken = handler.drain(side, offer, true);
            if (taken == null || taken.amount <= 0) return 0;
            if (!EmcTransactions.credit(p.getUniqueID(), EmcTransactions.fluidPrice(taken) * taken.amount)) {
                handler.fill(side, taken, true); return 0;
            }
            return taken.amount;
        }
        offer.amount = EmcTransactions.affordable(p, price, count);
        if (offer.amount <= 0) return 0;
        offer.amount = Math.max(0, Math.min(offer.amount, handler.fill(side, offer, false)));
        if (offer.amount <= 0 || !EmcTransactions.spend(p, offer.amount * price)) return 0;
        int moved = Math.max(0, Math.min(offer.amount, handler.fill(side, offer, true)));
        if (moved < offer.amount) EmcTransactions.refund(p, (offer.amount - moved) * price);
        return moved;
    }
    private static int resource(EntityPlayerMP p, TileEntity tile, MachineConfig.Rule r, NBTTagCompound port, int rule, int limit) {
        boolean rf = rule < 38, output = (rule & 1) == 1;
        int count = Math.min(limit, 1000000);
        double price = rf ? 1D / PrimordialConfig.rfPerEmc : (PrimordialConfig.manaPerEmc > 0 ? 1D / PrimordialConfig.manaPerEmc : 0);
        if (rule == 41) return 0; // Reject legacy EU recovery rules even if their enabled flag survives.
        if (rule == 40) return eu(p, tile, r, port, count);
        if (price <= 0) return 0;
        ForgeDirection side = ForgeDirection.getOrientation(port.getInteger("side"));
        if (rf) {
            Class<?>[] sig = { ForgeDirection.class, int.class, boolean.class };
            if (r.keep) {
                long stored = MachinePorts.call(tile, "getEnergyStored", new Class<?>[] { ForgeDirection.class }, side);
                count = (int) Math.min(count, Math.max(0, output ? stored - r.amount : r.amount - stored));
            }
            String method = output ? "extractEnergy" : "receiveEnergy";
            if (!output) count = EmcTransactions.affordable(p, price, count);
            count = (int) Math.max(0, Math.min(count, MachinePorts.call(tile, method, sig, side, count, true)));
            if (output && !PrimordialSavedData.get().canAcceptCredit(p.getUniqueID(), count * price)) return 0;
            if (count <= 0 || !output && !EmcTransactions.spend(p, count * price)) return 0;
            int actual = (int) Math.max(0, Math.min(count, MachinePorts.call(tile, method, sig, side, count, false)));
            if (output) EmcTransactions.credit(p.getUniqueID(), actual * price);
            else if (actual < count) EmcTransactions.refund(p, (count - actual) * price);
            return actual;
        }
        long stored = MachinePorts.call(tile, "getCurrentMana", new Class<?>[0]);
        long max = MachinePorts.call(tile, "getMaxMana", new Class<?>[0]);
        count = (int) Math.min(count, Math.max(0, output ? (r.keep ? stored - r.amount : stored) : (r.keep ? r.amount - stored : max - stored)));
        if (!output) count = EmcTransactions.affordable(p, price, count);
        if (output && !PrimordialSavedData.get().canAcceptCredit(p.getUniqueID(), count * price)) return 0;
        if (count <= 0 || !output && !EmcTransactions.spend(p, count * price)) return 0;
        MachinePorts.call(tile, "recieveMana", new Class<?>[] { int.class }, output ? -count : count);
        long after = MachinePorts.call(tile, "getCurrentMana", new Class<?>[0]);
        int actual = (int) Math.max(0, Math.min(count, output ? stored - after : after - stored));
        if (output) EmcTransactions.credit(p.getUniqueID(), actual * price);
        else if (actual < count) EmcTransactions.refund(p, (count - actual) * price);
        return actual;
    }
    private static int eu(EntityPlayerMP p, TileEntity tile, MachineConfig.Rule r, NBTTagCompound port, int limit) {
        if (PrimordialConfig.euPerEmc <= 0 || tile instanceof BlackholeTile) return 0;
        double price = 1D / PrimordialConfig.euPerEmc;
        long stored = MachinePorts.call(tile, "getStoredEU", new Class<?>[0]);
        long capacity = MachinePorts.call(tile, "getEUCapacity", new Class<?>[0]);
        int count = (int) Math.min(limit, Math.max(0, r.keep ? r.amount - stored : capacity - stored));
        count = EmcTransactions.affordable(p, price, count);
        long voltage = MachinePorts.call(tile, "getInputVoltage", new Class<?>[0]);
        if (voltage <= 0 || count < voltage || voltage > Integer.MAX_VALUE) return 0;
        count = (int) voltage;
        if (!EmcTransactions.spend(p, count * price)) return 0;
        MachinePorts.call(tile, "injectEnergyUnits", new Class<?>[] { ForgeDirection.class, long.class, long.class }, ForgeDirection.getOrientation(port.getInteger("side")), (long) count, 1L);
        long after = MachinePorts.call(tile, "getStoredEU", new Class<?>[0]);
        int actual = (int) Math.max(0, Math.min(count, after - stored));
        if (actual < count) EmcTransactions.refund(p, (count - actual) * price);
        return actual;
    }
    public static void clear() { cursor = 0; }
    private MachineTasks() {}
}
