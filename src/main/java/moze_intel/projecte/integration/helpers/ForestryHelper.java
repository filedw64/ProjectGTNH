package moze_intel.projecte.integration.helpers;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ForestryHelper {
	public static boolean isForestryBag(Item item) {
		if (item == null) return false;
		String id = Item.itemRegistry.getNameForObject(item);
		if (id == null) return false;
		return (id.endsWith("Bag") || id.endsWith("BagT2")) && id.startsWith("Forestry:");
	}

	public static boolean isForestryBag(ItemStack is) {
		if (is == null || is.getItem() == null) return false;
		return isForestryBag(is.getItem());
	}

	public static double ForestryBagEMC(ItemStack is) {
		SimpleStack ss = new SimpleStack(is);
		if (!EMCMapper.mapContains(ss))
			return 0.0;
		double res = EMCMapper.getEmcValue(ss);
		if (!is.hasTagCompound() || is.stackTagCompound.hasNoTags()) return res;
		NBTTagCompound nbt = is.stackTagCompound.getCompoundTag("Slots");
		for (String key : nbt.func_150296_c()) {
			NBTTagCompound slot = nbt.getCompoundTag(key);
			if (slot.hasNoTags()) continue;
			ItemStack stack = ItemStack.loadItemStackFromNBT(slot);
			if (stack == null || stack.getItem() == null) continue;
			double value = EMCHelper.getEmcValue(stack);
			if (value == 0) return 0.0;
			res += value * stack.stackSize;
		}
		return res;
	}
}
