package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.config.ProjectEConfig;

import java.util.Map;

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
		return EMCHelper.doesItemHaveEmc(stack);
	}

	@Override
	public void putStack(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null) {
			super.putStack(null);
			return;
		}

		// 如果放入的物品是同一个对象引用，跳过学习和EMC结算逻辑
		if (stack == this.getStack()) {
			super.putStack(stack);
			return;
		}

		boolean isSame = ItemStack.areItemStacksEqual(stack, this.getStack());

		// 结算并抹除动态 NBT EMC
		if (stack.hasTagCompound()) {
			NBTTagCompound nbt = stack.getTagCompound();
			double bonusEmc = 0;
			for (Map.Entry<String, Double> entry : ProjectEConfig.dynamicEmcNbt.entrySet()) {
				String key = entry.getKey();
				if (nbt.hasKey(key)) {
					double val = nbt.getDouble(key);
					bonusEmc += val * entry.getValue() * stack.stackSize;
					nbt.removeTag(key); // 结算完毕后立即抹除动态 NBT
				}
			}
			if (bonusEmc > 0) {
				inv.addEmc(bonusEmc);
			}
			if (nbt.hasNoTags()) {
				stack.setTagCompound(null);
			}
		}

		super.putStack(stack);

		if (stack.getItem() instanceof IItemEmc itemEmc)
		{
			double toAdd = Math.min(itemEmc.getMaximumEmc(stack) - itemEmc.getStoredEmc(stack), inv.emc);
			itemEmc.addEmc(stack, toAdd);
			inv.removeEmc(toAdd);
		}

		if (!isSame && stack.getItem() != ObjHandler.tome)
			inv.handleKnowledge(stack);
	}

	@Override
	public int getSlotStackLimit()
	{
		return 1;
	}
}
