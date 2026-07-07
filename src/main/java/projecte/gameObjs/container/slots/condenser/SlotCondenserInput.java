package projecte.gameObjs.container.slots.condenser;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import projecte.gameObjs.tiles.CondenserTile;
import projecte.utils.EMCHelper;

public class SlotCondenserInput extends Slot
{
	public SlotCondenserInput(CondenserTile inventory, int slotIndex, int xPos, int yPos)
	{
		super(inventory, slotIndex, xPos, yPos);
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return EMCHelper.doesItemHaveEmc(stack);
	}
}
