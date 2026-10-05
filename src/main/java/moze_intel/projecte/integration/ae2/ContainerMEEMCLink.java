package moze_intel.projecte.integration.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Ghost filters never consume or return items. Configuration uses vanilla enchant packets. */
public class ContainerMEEMCLink extends Container {
    public final TileMEEMCLink tile;
    public int accessMode, filterMode, filterPrecision, priority;

    public ContainerMEEMCLink(InventoryPlayer inventory, TileMEEMCLink tile) {
        this.tile = tile;
        for (int i = 0; i < 16; i++) addSlotToContainer(new Slot(tile, i, 16 + i % 8 * 18, 64 + i / 8 * 18) {
            @Override public boolean canTakeStack(EntityPlayer player) { return false; }
            @Override public boolean isItemValid(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 132 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col, 8 + col * 18, 190));
        copySettings();
    }

    private void copySettings() {
        accessMode = tile.getAccessMode();
        filterMode = tile.getFilterMode();
        filterPrecision = tile.getFilterPrecision();
        priority = tile.getPriority();
    }

    @Override public boolean canInteractWith(EntityPlayer player) { return tile.isUseableByPlayer(player); }

    @Override
    public ItemStack slotClick(int index, int button, int mode, EntityPlayer player) {
        if (!canInteractWith(player)) return null;
        if (index >= 0 && index < 16) {
            if (!player.worldObj.isRemote && mode == 0) {
                ItemStack held = player.inventory.getItemStack();
                tile.setInventorySlotContents(index, button == 1 || held == null ? null : held);
            }
            return null;
        }
        return super.slotClick(index, button, mode, player);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return null; }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (player.worldObj.isRemote || !canInteractWith(player)) return false;
        switch (action) {
            case 0: tile.setAccessMode((tile.getAccessMode() + 1) % 3); break;
            case 1: tile.setFilterMode((tile.getFilterMode() + 1) % 3); break;
            case 2: tile.setFilterPrecision((tile.getFilterPrecision() + 1) % 3); break;
            case 3: if (tile.getPriority() > Integer.MIN_VALUE) tile.setPriority(tile.getPriority() - 1); break;
            case 4: if (tile.getPriority() < Integer.MAX_VALUE) tile.setPriority(tile.getPriority() + 1); break;
            default: return false;
        }
        return true;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        copySettings();
        for (Object raw : crafters) {
            ICrafting crafter = (ICrafting) raw;
            crafter.sendProgressBarUpdate(this, 0, accessMode);
            crafter.sendProgressBarUpdate(this, 1, filterMode);
            crafter.sendProgressBarUpdate(this, 2, filterPrecision);
            crafter.sendProgressBarUpdate(this, 3, priority & 65535);
            crafter.sendProgressBarUpdate(this, 4, priority >>> 16);
        }
    }

    @Override
    public void updateProgressBar(int id, int value) {
        switch (id) {
            case 0: accessMode = value; break;
            case 1: filterMode = value; break;
            case 2: filterPrecision = value; break;
            case 3: priority = (priority & 0xffff0000) | (value & 65535); break;
            case 4: priority = (priority & 65535) | (value & 65535) << 16; break;
            default: break;
        }
    }
}
