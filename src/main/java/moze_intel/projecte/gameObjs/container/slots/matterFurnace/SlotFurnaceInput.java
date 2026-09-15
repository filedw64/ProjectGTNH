package moze_intel.projecte.gameObjs.container.slots.matterFurnace;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

public class SlotFurnaceInput extends Slot {
	public SlotFurnaceInput(IInventory inv, int slotId, int xPos, int yPos) {
		super(inv, slotId, xPos, yPos);
	}

	private static final FurnaceRecipes furnaceRecipes = FurnaceRecipes.smelting();

	@Override
	public boolean isItemValid(ItemStack stack) {
		return furnaceRecipes.getSmeltingResult(stack) != null;
	}
}
