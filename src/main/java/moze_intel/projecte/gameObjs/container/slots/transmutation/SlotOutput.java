package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;

public class SlotOutput extends Slot
{
	private final TransmutationInventory inv;

	public SlotOutput(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public ItemStack decrStackSize(int amount)
	{
		ItemStack stack = getStack().copy();
		stack.stackSize = amount;
        double emcValue = amount * EMCHelper.getEmcValue(stack);
		if (emcValue > inv.emc) {
			//Requesting more emc than available
			//Can not return `null` here or NPE in Container! Container expects stacksize=0-Itemstack for 'nothing'
			stack.stackSize = 0;
			return stack;
		}
		inv.removeEmc(emcValue);
		//inv.checkForUpdates(); // 买不起页面里价值最高的物品才刷新？nonono
		inv.updateOutputs(); // emc 减少了就该刷新输出
		return stack;
	}

	@Override
	public void putStack(ItemStack stack) {}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return false;
	}

	@Override
	public boolean canTakeStack(EntityPlayer player)
	{
		if (getHasStack()) {
			return EMCHelper.getEmcValue(getStack()) <= inv.emc;
		}
		return true;
	}
}
