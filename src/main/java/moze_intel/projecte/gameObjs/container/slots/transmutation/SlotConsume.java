package moze_intel.projecte.gameObjs.container.slots.transmutation;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.config.ProjectEConfig;

import java.util.Map;

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

		double totalEmc = 0;

		// 动态 NBT 转换为 EMC
		if (stack.hasTagCompound()) {
			NBTTagCompound nbt = stack.getTagCompound();
			for (Map.Entry<String, Double> entry : ProjectEConfig.dynamicEmcNbt.entrySet()) {
				String key = entry.getKey();
				if (nbt.hasKey(key)) {
					// 这样写应该能安全地读取大多数数值类型的 NBT (Int, Float, Double 等)
					double val = nbt.getDouble(key);
					totalEmc += val * entry.getValue() * stack.stackSize;
				}
			}
		}

		// 加上物品自身的 EMC
		totalEmc += EMCHelper.getEmcValue(stack) * stack.stackSize;

		inv.addEmc(totalEmc);
		inv.handleKnowledge(stack);
		this.onSlotChanged();
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return !inv.hasMaxedEmc() && (EMCHelper.doesItemHaveEmc(stack) || stack.getItem() == ObjHandler.tome);
	}
}
