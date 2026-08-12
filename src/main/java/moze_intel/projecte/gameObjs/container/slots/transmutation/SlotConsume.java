package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;

public class SlotConsume extends Slot
{
	private final TransmutationInventory inv;

	public SlotConsume(TransmutationInventory inv, int par2, int par3, int par4)
	{
		super(inv, par2, par3, par4);
		this.inv = inv;
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null) return;

		// 没有 super.putStack(stack)，这就是为什么物品放进来就消失了？

		//if (stack.getItem() != ObjHandler.tome) // 通常而言，知识之书没有 emc，但如果开放了合成，知识之书也应当转化为 emc
		// 或者不？毕竟是添加了知识，相当于用 emc 去换知识了
		inv.addEmc(EMCHelper.getEmcValue(stack) * stack.stackSize);
		inv.handleKnowledge(stack); // 处理知识放在 addEmc 之后，这样 emc 增加后会刷新输出
		this.onSlotChanged();
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return !inv.hasMaxedEmc() && (EMCHelper.doesItemHaveEmc(stack) || stack.getItem() == ObjHandler.tome);
	}
}
