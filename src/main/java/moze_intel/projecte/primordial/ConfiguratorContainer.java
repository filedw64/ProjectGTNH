package moze_intel.projecte.primordial;

import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.integration.helpers.GTItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

public final class ConfiguratorContainer extends Container {
    public MachineConfig config;
    public NBTTagList available = new NBTTagList();
    public int selected, side, page;
    private final EntityPlayer player;
    private final boolean inventory;
    private final int heldSlot;
    private final ItemStack tool;
    private NBTTagCompound last;
    public ConfiguratorContainer(EntityPlayer p, int x, int y, int z, boolean inventory) {
        this.player = p; this.inventory = inventory; this.heldSlot = p.inventory.currentItem; this.tool = p.getHeldItem();
        config = new MachineConfig(p.getUniqueID(), p.dimension, x, y, z);
        if (inventory) config.block = "@player";
        if (!p.worldObj.isRemote) {
            PrimordialSavedData data = PrimordialSavedData.get(); MachineConfig existing = data.machines.get(config.key());
            if (existing != null) config = existing;
            else if (inventory) { data.machines.put(config.key(), config); data.markDirty(); }
        }
        InventoryBasic ghost = new InventoryBasic("samples", false, 36);
        for (int i = 0; i < 36; i++) addSlotToContainer(new Slot(ghost, i, 8 + i % 9 * 18, 32 + i / 9 * 18) { @Override public boolean isItemValid(ItemStack s) { return false; } @Override public boolean canTakeStack(EntityPlayer p) { return false; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(p.inventory, 9 + row * 9 + col, 8 + col * 18, 182 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(p.inventory, col, 8 + col * 18, 240));
    }
    @Override public boolean canInteractWith(EntityPlayer p) {
        ItemStack held = p.getHeldItem();
        if (held == null || held != tool || p.inventory.currentItem != heldSlot || !(held.getItem() instanceof ConfiguratorItem)) return false;
        return p.worldObj.isRemote || config.owner.equals(p.getUniqueID()) && (inventory || p.dimension == config.dimension && p.worldObj.blockExists(config.x, config.y, config.z)
            && config.block.equals(net.minecraft.block.Block.blockRegistry.getNameForObject(p.worldObj.getBlock(config.x, config.y, config.z))));
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer p, int slot) { return null; }
    @Override public ItemStack slotClick(int slot, int button, int mode, EntityPlayer p) {
        if (slot >= 0 && slot < 36) {
            if (!p.worldObj.isRemote && canInteractWith(p) && mode == 0) {
                selected = slot; MachineConfig.Rule r = config.rules[slot]; ItemStack carried = p.inventory.getItemStack();
                if (button == 1) { r.sample = null; r.fluid = null; r.enabled = false; }
                else if (carried != null) {
                    r.sample = carried.copy(); r.sample.stackSize = 1; r.fluid = FluidContainerRegistry.getFluidForFilledItem(carried);
                    if (GTItemHelper.isGTfluidDisplay(carried)) {
                        Fluid fluid = FluidRegistry.getFluid(carried.getItemDamage());
                        r.fluid = fluid == null ? null : new FluidStack(fluid, 1);
                    }
                    if (carried.getItem() instanceof IFluidContainerItem) r.fluid = ((IFluidContainerItem) carried.getItem()).getFluid(carried);
                    if (r.fluid != null) r.fluid.amount = 1;
                }
                changed();
            }
            return null;
        }
        if (!canInteractWith(p) || mode != 0 || slot == 36 + 27 + heldSlot) return null;
        return super.slotClick(slot, button, mode, p);
    }
    public void action(int action, int value) {
        if (!canInteractWith(player)) return;
        MachineConfig.Rule r = config.rules[selected];
        switch (action) {
            case 0: selected = Math.max(0, Math.min(inventory ? 35 : 40, value)); page = 0; break;
            case 1: r.enabled = !r.enabled; break;
            case 2: r.keep = !r.keep; break;
            case 3: r.amount = Math.max(1, value); break;
            case 4: r.interval = Math.max(1, Math.min(72000, value)); break;
            case 5: side = Math.max(0, Math.min(6, value)); page = 0; break;
            case 6: page = Math.max(0, Math.min(9, value)); break;
            case 7:
                refreshPorts(); int index = page * 27 + value;
                if (value < 0 || value >= 27 || index >= available.tagCount()) break;
                NBTTagCompound target = available.getCompoundTagAt(index); NBTTagList next = new NBTTagList(); boolean found = false;
                for (int i = 0; i < r.ports.tagCount(); i++) { NBTTagCompound old = r.ports.getCompoundTagAt(i); if (old.equals(target)) found = true; else next.appendTag(old.copy()); }
                if (!found && next.tagCount() < 256) next.appendTag(target.copy()); r.ports = next; break;
            case 8: r.ports = new NBTTagList(); break;
        }
        changed();
    }
    private void refreshPorts() {
        if (!inventory && !player.worldObj.isRemote && player.worldObj.blockExists(config.x, config.y, config.z))
            available = selected >= 36 ? MachinePorts.resourcePorts(player.worldObj.getTileEntity(config.x, config.y, config.z), side, selected < 38 ? 0 : selected < 40 ? 1 : 2)
                : MachinePorts.list(player.worldObj.getTileEntity(config.x, config.y, config.z), side, selected >= 18);
    }
    private void changed() { PrimordialSavedData.get().markDirty(); last = null; }
    @Override public void detectAndSendChanges() {
        for (int i = 0; i < 36; i++) getSlot(i).putStack(config.rules[i].sample == null ? null : config.rules[i].sample.copy());
        super.detectAndSendChanges();
        if (player.worldObj.isRemote || !(player instanceof EntityPlayerMP)) return;
        refreshPorts(); NBTTagCompound t = config.write(); t.setInteger("selected", selected); t.setInteger("side", side); t.setInteger("page", page); t.setTag("available", available.copy());
        if (!t.equals(last)) { last = t; PacketHandler.sendTo(new PrimordialSyncPacket(windowId, t), (EntityPlayerMP) player); }
    }
    public void receive(NBTTagCompound t) { config = MachineConfig.read(t); selected = t.getInteger("selected"); side = t.getInteger("side"); page = t.getInteger("page"); available = t.getTagList("available", 10); }
}
