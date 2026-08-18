package moze_intel.projecte.gameObjs.container.slots.matterFurnace;

import moze_intel.projecte.api.item.IItemEmc;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

public class SlotFurnaceFuel extends Slot {
	public SlotFurnaceFuel(IInventory inv, int slotId, int xPos, int yPos) {
		super(inv, slotId, xPos, yPos);
	}

	@Override
	public boolean isItemValid(ItemStack stack) {
		return stack != null && (TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc);
	}
}
