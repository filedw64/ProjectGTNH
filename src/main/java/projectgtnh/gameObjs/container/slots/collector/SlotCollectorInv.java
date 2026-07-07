package projectgtnh.gameObjs.container.slots.collector;

import projectgtnh.api.item.IItemEmc;
import projectgtnh.emc.FuelMapper;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotCollectorInv extends Slot 
{
	public SlotCollectorInv(IInventory inventory, int slotIndex, int xPos, int yPos) 
	{
		super(inventory, slotIndex, xPos, yPos);
	}
	
	@Override
	public boolean isItemValid(ItemStack stack)
	{
		if (stack == null)
		{
			return false;
		}
		
		return stack.getItem() instanceof IItemEmc || (FuelMapper.isStackFuel(stack) && !FuelMapper.isStackMaxFuel(stack));
	}
}
