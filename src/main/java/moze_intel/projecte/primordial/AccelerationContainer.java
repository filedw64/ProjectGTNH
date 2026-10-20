package moze_intel.projecte.primordial;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import moze_intel.projecte.network.PacketHandler;

public final class AccelerationContainer extends Container {
    private final EntityPlayer player;
    private final ItemStack tool;
    private final int slot;
    public NBTTagCompound state = new NBTTagCompound();
    private NBTTagCompound last;
    public AccelerationContainer(EntityPlayer p) { player = p; tool = p.getHeldItem(); slot = p.inventory.currentItem; if (tool != null) state = (NBTTagCompound) AccelerationSettings.tag(tool).copy(); }
    @Override public boolean canInteractWith(EntityPlayer p) { return p.inventory.currentItem == slot && p.getHeldItem() == tool && tool != null && tool.getItem() instanceof EmcAcceleratorItem; }
    @Override public ItemStack transferStackInSlot(EntityPlayer p, int slot) { return null; }
    public void action(int action, int value, String token) {
        if (!canInteractWith(player)) return;
        switch (action) {
            case 0: AccelerationSettings.stop(tool); AccelerationSettings.tag(tool).setInteger("kind", Math.max(0, Math.min(6, value))); break;
            case 1: AccelerationService.setRate(tool, value); break;
            case 2: AccelerationService.toggle(tool, player); break;
            case 3: AccelerationService.confirm(tool, player, token); break;
            case 4: AccelerationSettings.stop(tool); AccelerationSettings.tag(tool).removeTag("targets"); break;
            case 5:
                NBTTagList list = AccelerationSettings.targets(tool), next = new NBTTagList();
                for (int i = 0; i < list.tagCount(); i++) if (i != value) next.appendTag(list.getCompoundTagAt(i).copy());
                AccelerationSettings.tag(tool).setTag("targets", next); AccelerationSettings.tag(tool).removeTag("confirmation"); break;
        }
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges(); if (player.worldObj.isRemote || !canInteractWith(player)) return;
        NBTTagCompound current = (NBTTagCompound) AccelerationSettings.tag(tool).copy();
        current.setDouble("costCoefficient", PrimordialConfig.accelerationCost);
        current.setLong("now", player.worldObj.getTotalWorldTime());
        if (!current.equals(last)) { last = current; PacketHandler.sendTo(new PrimordialSyncPacket(windowId, current), (EntityPlayerMP) player); }
    }
}
