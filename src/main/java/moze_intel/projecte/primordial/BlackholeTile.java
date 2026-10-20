package moze_intel.projecte.primordial;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/** Input-only inventory/fluid sink. No IC2 or GregTech energy interface is implemented. */
@cpw.mods.fml.common.Optional.Interface(iface = "cofh.api.energy.IEnergyReceiver", modid = "CoFHCore")
public final class BlackholeTile extends TileEntity implements ISidedInventory, IFluidHandler, cofh.api.energy.IEnergyReceiver {
    private UUID owner;
    private final ItemStack[] buffer = new ItemStack[64];
    private double pending;
    private long budgetTick = Long.MIN_VALUE;
    private int acceptedItems, acceptedFluids, acceptedRf;
    public void setOwner(UUID value) { if (owner != null && !owner.equals(value)) settle(); owner = value; markDirty(); }
    private void budget() {
        long now = worldObj.getTotalWorldTime();
        if (now != budgetTick) { budgetTick = now; acceptedItems = acceptedFluids = acceptedRf = 0; }
    }
    private boolean active() { return owner != null && worldObj != null && !worldObj.isRemote; }
    @Override public void updateEntity() {
        if (!active()) return;
        budget();
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord - 1.5, yCoord + 1, zCoord - 1.5, xCoord + 2.5, yCoord + 2.1, zCoord + 2.5);
        List<Entity> entities = worldObj.getEntitiesWithinAABBExcludingEntity(null, box);
        int count = 0;
        for (Entity e : entities) {
            if (count >= PrimordialConfig.entities) break;
            if (e.isDead || (int) Math.floor(e.posX) != xCoord || (int) Math.floor(e.posY) != yCoord + 1 || (int) Math.floor(e.posZ) != zCoord) continue;
            if (e instanceof EntityItem) {
                ItemStack s = ((EntityItem) e).getEntityItem(); double price = EmcTransactions.itemPrice(s);
                int amount = Math.min(s.stackSize, PrimordialConfig.items - acceptedItems);
                if (amount <= 0 || price <= 0 || !queue(price * amount)) continue;
                s = s.copy(); s.stackSize -= amount; acceptedItems += amount;
                if (s.stackSize == 0) e.setDead(); else ((EntityItem) e).setEntityItemStack(s);
                count++;
            } else {
                double price = EntityEmc.price(e);
                if (price > 0 && queue(price)) { e.setDead(); count++; }
            }
        }
        for (int i = 0; i < buffer.length; i++) {
            ItemStack stack = buffer[i]; if (stack == null) continue;
            double price = EmcTransactions.itemPrice(stack);
            int amount = Math.min(stack.stackSize, PrimordialConfig.items - acceptedItems);
            if (amount > 0 && price > 0 && queue(price * amount)) {
                acceptedItems += amount; stack.stackSize -= amount; if (stack.stackSize <= 0) buffer[i] = null; markDirty();
            }
        }
        flush();
    }
    private boolean queue(double amount) {
        double next = pending + amount;
        if (!Double.isFinite(amount) || amount <= 0 || !Double.isFinite(next) || next <= pending) return false;
        pending = next; markDirty(); return true;
    }
    private void flush() { if (pending > 0 && EmcTransactions.credit(owner, pending)) { pending = 0; markDirty(); } }
    @Override public void onChunkUnload() { if (active()) flush(); super.onChunkUnload(); }
    @Override public void invalidate() { if (active()) flush(); super.invalidate(); }
    public void settle() {
        if (!active()) return;
        flush();
        // Accepted EMC is moved to world saved data before this tile can disappear.
        for (int i = 0; i < buffer.length; i++) if (buffer[i] != null) {
            worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + .5, yCoord + 1, zCoord + .5, buffer[i])); buffer[i] = null;
        }
        markDirty();
    }
    @Override @cpw.mods.fml.common.Optional.Method(modid = "CoFHCore")
    public boolean canConnectEnergy(ForgeDirection from) { return active() && PrimordialConfig.rfPerEmc > 0; }
    @Override @cpw.mods.fml.common.Optional.Method(modid = "CoFHCore")
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (!active() || PrimordialConfig.rfPerEmc <= 0 || maxReceive <= 0) return 0;
        budget(); int amount = Math.min(maxReceive, 4000000 - acceptedRf);
        double value = amount / PrimordialConfig.rfPerEmc;
        if (amount <= 0 || !Double.isFinite(value) || pending + value <= pending) return 0;
        if (!simulate) { if (!queue(value)) return 0; acceptedRf += amount; }
        return amount;
    }
    @Override @cpw.mods.fml.common.Optional.Method(modid = "CoFHCore")
    public int getEnergyStored(ForgeDirection from) { return 0; }
    @Override @cpw.mods.fml.common.Optional.Method(modid = "CoFHCore")
    public int getMaxEnergyStored(ForgeDirection from) { return 4000000; }
    @Override public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!active() || resource == null || resource.amount <= 0) return 0;
        budget(); double price = EmcTransactions.fluidPrice(resource);
        int amount = Math.min(resource.amount, PrimordialConfig.fluids - acceptedFluids);
        if (price <= 0 || amount <= 0 || !Double.isFinite(price * amount) || pending + price * amount <= pending) return 0;
        if (doFill) { if (!queue(price * amount)) return 0; acceptedFluids += amount; }
        return amount;
    }
    @Override public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) { return null; }
    @Override public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) { return null; }
    @Override public boolean canFill(ForgeDirection from, Fluid fluid) { return active() && fluid != null && EmcTransactions.fluidPrice(new FluidStack(fluid, 1)) > 0; }
    @Override public boolean canDrain(ForgeDirection from, Fluid fluid) { return false; }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection from) { return new FluidTankInfo[] { new FluidTankInfo(null, PrimordialConfig.fluids) }; }
    @Override public int getSizeInventory() { return buffer.length; }
    @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < buffer.length ? buffer[slot] : null; }
    @Override public ItemStack decrStackSize(int slot, int amount) { return null; }
    @Override public ItemStack getStackInSlotOnClosing(int slot) { return null; }
    @Override public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= buffer.length) return;
        buffer[slot] = stack; markDirty();
    }
    @Override public String getInventoryName() { return "tile.pe_primordial_blackhole.name"; }
    @Override public boolean hasCustomInventoryName() { return false; }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUseableByPlayer(EntityPlayer player) { return false; }
    @Override public void openInventory() {}
    @Override public void closeInventory() {}
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return active() && EmcTransactions.itemPrice(stack) > 0; }
    @Override public int[] getAccessibleSlotsFromSide(int side) { int[] slots = new int[64]; for (int i = 0; i < 64; i++) slots[i] = i; return slots; }
    @Override public boolean canInsertItem(int slot, ItemStack stack, int side) { return isItemValidForSlot(slot, stack); }
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) { return false; }
    @Override public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t); Arrays.fill(buffer, null);
        try { owner = UUID.fromString(t.getString("owner")); } catch (IllegalArgumentException ignored) { owner = null; }
        pending = t.getDouble("pending"); if (!Double.isFinite(pending) || pending < 0) pending = 0;
        NBTTagList list = t.getTagList("buffer", 10);
        for (int i = 0; i < Math.min(64, list.tagCount()); i++) { NBTTagCompound e = list.getCompoundTagAt(i); int slot = e.getInteger("slot"); if (slot >= 0 && slot < 64) buffer[slot] = ItemStack.loadItemStackFromNBT(e); }
    }
    @Override public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t); if (owner != null) t.setString("owner", owner.toString()); t.setDouble("pending", pending);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < 64; i++) if (buffer[i] != null) { NBTTagCompound e = buffer[i].writeToNBT(new NBTTagCompound()); e.setInteger("slot", i); list.appendTag(e); }
        t.setTag("buffer", list);
    }
}
