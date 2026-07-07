package projectgtnh.gameObjs.container.slots.condenser;

import projectgtnh.gameObjs.tiles.CondenserTile;
import projectgtnh.utils.EMCHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

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
