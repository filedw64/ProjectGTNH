package moze_intel.projecte.gameObjs.container.slots.collector;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.emc.FuelMapper;

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
