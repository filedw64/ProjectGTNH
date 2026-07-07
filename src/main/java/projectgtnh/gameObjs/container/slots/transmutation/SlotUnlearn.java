package projectgtnh.gameObjs.container.slots.transmutation;

import projectgtnh.gameObjs.container.inventory.TransmutationInventory;
import projectgtnh.utils.EMCHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotUnlearn extends Slot
{
	private TransmutationInventory inv;
	
	public SlotUnlearn(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}
	
	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return !this.getHasStack() && EMCHelper.doesItemHaveEmc(stack);
	}
	
	@Override
	public void putStack(ItemStack stack)
	{
		if (stack != null)
		{
			inv.handleUnlearn(stack.copy());
		}

		super.putStack(stack);
	}
	
	@Override
	public int getSlotStackLimit()
	{
		return 1;
	}
}
