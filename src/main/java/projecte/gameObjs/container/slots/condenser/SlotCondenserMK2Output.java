package projecte.gameObjs.container.slots.condenser;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import projecte.gameObjs.tiles.CondenserMK2Tile;

public class SlotCondenserMK2Output extends Slot
{
	public SlotCondenserMK2Output(CondenserMK2Tile inventory, int slotIndex, int xPos, int yPos)
	{
		super(inventory, slotIndex, xPos, yPos);
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return false;
	}
}
