package projecte.gameObjs.container.slots.transmutation;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import projecte.gameObjs.ObjHandler;
import projecte.gameObjs.container.inventory.TransmutationInventory;
import projecte.utils.EMCHelper;

public class SlotConsume extends Slot
{
	private TransmutationInventory inv;

	public SlotConsume(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null)
		{
			return;
		}

		ItemStack cache = stack.copy();

		double toAdd = 0;

		while (!inv.hasMaxedEmc() && stack.stackSize > 0)
		{
			toAdd += EMCHelper.getEmcValue(stack);
			stack.stackSize--;
		}

		inv.addEmc(toAdd);
		this.onSlotChanged();
		inv.handleKnowledge(cache);
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return !inv.hasMaxedEmc() && (EMCHelper.doesItemHaveEmc(stack) || stack.getItem() == ObjHandler.tome);
	}
}
