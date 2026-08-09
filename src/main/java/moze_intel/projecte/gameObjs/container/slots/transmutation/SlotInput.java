package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;

public class SlotInput extends Slot
{
	private final TransmutationInventory inv;

	public SlotInput(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return /*!this.getHasStack() && */EMCHelper.doesItemHaveEmc(stack);
		// 即使输入槽有物品也应当允许其它物品与之交换
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null)
			return;

		super.putStack(stack);

		if (stack.getItem() instanceof IItemEmc itemEmc)
		{
			double toAdd = Math.min(itemEmc.getMaximumEmc(stack) - itemEmc.getStoredEmc(stack), inv.emc);
			itemEmc.addEmc(stack, toAdd);
			inv.removeEmc(toAdd);
		}

		if (stack.getItem() != ObjHandler.tome)
			inv.handleKnowledge(stack); // 若知识之书有 emc，被放入输入槽的时候不处理知识
	}

	@Override
	public int getSlotStackLimit()
	{
		return 1;
	}
}
