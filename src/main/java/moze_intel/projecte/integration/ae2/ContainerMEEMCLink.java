package moze_intel.projecte.integration.ae2;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerMEEMCLink extends Container {

	private final IInventory inventory;
	private final TileMEEMCLink tile;
	private final EntityPlayer player;

	private int lastAccessMode = -1, lastPriority = -9999, lastFilterMode = -1, lastFilterPrecision = -1;

	public ContainerMEEMCLink(InventoryPlayer playerInv, TileMEEMCLink tile) {
		this.tile = tile;
		this.player = playerInv.player;
		this.inventory = tile;

		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) {
				addSlotToContainer(new GhostSlot(inventory, col + row * 4, 80 + col * 18, 22 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 160));
		}
	}

	public TileMEEMCLink getTile() { return tile; }
	public int getAccessMode() { return tile != null ? tile.getAccessMode() : 0; }
	public int getPriority() { return tile != null ? tile.getPriority() : 0; }
	public int getFilterMode() { return tile != null ? tile.getFilterMode() : 0; }
	public int getFilterPrecision() { return tile != null ? tile.getFilterPrecision() : 0; }
	public String getOwnerName() { return tile != null ? tile.getOwnerName() : ""; }

	@Override public boolean canInteractWith(EntityPlayer player) { return inventory != null && inventory.isUseableByPlayer(player); }

	@Override public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
		if (slotId >= 0 && slotId < 16) {
			Slot slot = inventorySlots.get(slotId);
			ItemStack cursor = player.inventory.getItemStack();
			if (cursor != null) {
				ItemStack copy = cursor.copy(); copy.stackSize = 1; slot.putStack(copy);
			} else slot.putStack(null);
			return null;
		}
		return super.slotClick(slotId, clickedButton, mode, player);
	}

	@Override public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
		Slot slot = inventorySlots.get(slotIndex);
		if (slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			if (slotIndex >= 16) {
				for (int i = 0; i < 16; i++) {
					Slot filterSlot = inventorySlots.get(i);
					if (!filterSlot.getHasStack()) {
						ItemStack copy = stack.copy(); copy.stackSize = 1; filterSlot.putStack(copy);
						break;
					}
				}
			}
		}
		return null;
	}

	@Override public void detectAndSendChanges() {
		super.detectAndSendChanges();
		for (ICrafting ic : crafters) {
			if (lastAccessMode != getAccessMode()) ic.sendProgressBarUpdate(this, 0, getAccessMode());
			if (lastPriority != getPriority()) ic.sendProgressBarUpdate(this, 1, getPriority());
			if (lastFilterMode != getFilterMode()) ic.sendProgressBarUpdate(this, 2, getFilterMode());
			if (lastFilterPrecision != getFilterPrecision()) ic.sendProgressBarUpdate(this, 3, getFilterPrecision());
		}
		lastAccessMode = getAccessMode(); lastPriority = getPriority();
		lastFilterMode = getFilterMode(); lastFilterPrecision = getFilterPrecision();
	}

	@Override @SideOnly(Side.CLIENT) public void updateProgressBar(int id, int data) {
		if (tile == null) return;
		if (id == 0) tile.setAccessMode(data);
		else if (id == 1) tile.setPriority(data);
		else if (id == 2) tile.setFilterMode(data);
		else if (id == 3) tile.setFilterPrecision(data);
	}

	public static class GhostSlot extends Slot {
		public GhostSlot(IInventory inv, int index, int x, int y) { super(inv, index, x, y); }
		@Override public boolean isItemValid(ItemStack stack) { return true; }
		@Override public boolean canTakeStack(EntityPlayer player) { return false; }
		@Override public int getSlotStackLimit() { return 1; }
	}
}
