package moze_intel.projecte.primordial;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/** Forge 1.7.10 ports. Fingerprints make stale slot/tank bindings fail closed. */
public final class MachinePorts {
    public static String fingerprint(TileEntity tile, int side) {
        if (tile == null) return "";
        int slots = tile instanceof IInventory ? ((IInventory) tile).getSizeInventory() : 0;
        int tanks = 0;
        if (tile instanceof IFluidHandler) {
            FluidTankInfo[] info = ((IFluidHandler) tile).getTankInfo(ForgeDirection.getOrientation(side));
            tanks = info == null ? 0 : info.length;
        }
        return tile.getClass().getName() + "/" + slots + "/" + tanks + "/" + tile.getBlockMetadata();
    }
    public static NBTTagList list(TileEntity tile, int side, boolean fluid) {
        NBTTagList result = new NBTTagList();
        if (tile == null || side < 0 || side > 6) return result;
        String key = fingerprint(tile, side);
        if (fluid && tile instanceof IFluidHandler) {
            // Forge 1.7.10 transfers are face-wide, never addressable by tank index.
            // Expose one aggregate port to avoid pretending an internal tank can be selected.
            FluidTankInfo[] tanks = ((IFluidHandler) tile).getTankInfo(ForgeDirection.getOrientation(side));
            if (tanks != null && tanks.length > 0) result.appendTag(port(side, 0, key));
        } else if (!fluid && tile instanceof IInventory) {
            IInventory inv = (IInventory) tile;
            int[] slots;
            if (inv instanceof ISidedInventory && side < 6) slots = ((ISidedInventory) inv).getAccessibleSlotsFromSide(side);
            else { slots = new int[Math.min(256, inv.getSizeInventory())]; for (int i = 0; i < slots.length; i++) slots[i] = i; }
            if (slots != null) for (int i = 0; i < Math.min(256, slots.length); i++) {
                if (slots[i] >= 0 && slots[i] < inv.getSizeInventory()) result.appendTag(port(side, slots[i], key));
            }
        }
        return result;
    }
    private static NBTTagCompound port(int side, int index, String key) {
        NBTTagCompound t = new NBTTagCompound(); t.setInteger("side", side); t.setInteger("index", index); t.setString("key", key); return t;
    }
    public static boolean valid(TileEntity tile, NBTTagCompound port) {
        int side = port.getInteger("side");
        return tile != null && !tile.isInvalid() && side >= 0 && side <= 6 && port.getString("key").equals(fingerprint(tile, side));
    }
    public static boolean matches(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }
    public static boolean accessible(IInventory inv, int index, int side, ItemStack stack, boolean insert) {
        if (index < 0 || index >= inv.getSizeInventory()) return false;
        if (insert && !inv.isItemValidForSlot(index, stack)) return false;
        if (inv instanceof ISidedInventory && side < 6) {
            ISidedInventory sided = (ISidedInventory) inv;
            int[] slots = sided.getAccessibleSlotsFromSide(side);
            boolean found = false; if (slots != null) for (int slot : slots) if (slot == index) found = true;
            return found && (insert ? sided.canInsertItem(index, stack, side) : sided.canExtractItem(index, stack, side));
        }
        return true;
    }
    public static boolean hasResource(TileEntity tile, int type) {
        if (tile == null) return false;
        String[] names = type == 0 ? new String[] { "receiveEnergy", "extractEnergy" } : type == 2 ? new String[] { "injectEnergyUnits" } : new String[] { "recieveMana" };
        for (String name : names) {
            try { tile.getClass().getMethod(name, type == 0 ? new Class<?>[] { ForgeDirection.class, int.class, boolean.class } : type == 2 ? new Class<?>[] { ForgeDirection.class, long.class, long.class } : new Class<?>[] { int.class }); return true; }
            catch (NoSuchMethodException ignored) { }
        }
        return false;
    }
    public static long call(TileEntity tile, String name, Class<?>[] signature, Object... args) {
        try { Object value = tile.getClass().getMethod(name, signature).invoke(tile, args); return value instanceof Number ? ((Number) value).longValue() : 0; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Resource port " + name, failure); }
    }
    public static NBTTagList resourcePorts(TileEntity tile, int side, int type) {
        NBTTagList list = new NBTTagList();
        if (hasResource(tile, type)) list.appendTag(port(side, 0, fingerprint(tile, side)));
        return list;
    }
    private MachinePorts() {}
}
