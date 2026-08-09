package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;

public class SlotLock extends Slot
{
	private final TransmutationInventory inv;

	public SlotLock(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return EMCHelper.doesItemHaveEmc(stack);
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null)
			return;

		if (!ItemStack.areItemStacksEqual(stack, getStack()))
			inv.searchpage = 0; // 只有当放入的物品改变时才刷新页码

		super.putStack(stack);

		if (stack.getItem() instanceof IItemEmc itemEmc)
		{
			double toRemove = Math.min(Constants.TILE_MAX_EMC - inv.emc, itemEmc.getStoredEmc(stack));
			itemEmc.extractEmc(stack, toRemove);
			inv.addEmc(toRemove);
		}

		if (stack.getItem() != ObjHandler.tome)
			inv.handleKnowledge(stack);
		else inv.updateOutputs(); // 能来到这里，则知识之书也有 emc，应该以知识之书的 emc 来筛选物品
	}

	@Override
	public void onPickupFromSlot(EntityPlayer par1EntityPlayer, ItemStack par2ItemStack)
	{
		super.onPickupFromSlot(par1EntityPlayer, par2ItemStack);
		inv.searchpage = 0;
		inv.updateOutputs();
	}

	@Override
	public int getSlotStackLimit()
	{
		return 1;
	}
}
