package moze_intel.projecte.utils;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Comparator;

public final class Comparators {
	public static final Comparator<ItemStack> ITEMSTACK_EMC_DESCENDING = (s1, s2) -> {
        return EMCHelper.getEmcValueExact(s2).compareTo(EMCHelper.getEmcValueExact(s1));
    };

	public static final Comparator<ItemStack> ITEMSTACK_ASCENDING = (o1, o2) -> {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return 1;
        if (o2 == null) return -1;

        if (ItemHelper.basicAreStacksEqual(o1, o2))
            return o1.stackSize - o2.stackSize; // Same item id, same meta

		if (o1.getItem() != o2.getItem())
			return Item.getIdFromItem(o1.getItem()) - Item.getIdFromItem(o2.getItem());// Different item

		return o1.getItemDamage() - o2.getItemDamage();// Different meta
	};

	public static final Comparator<SimpleStack> SIMPLESTACK_ASCENDING = (s1, s2) -> {
        return EMCMapper.getEmcValueExact(s1).compareTo(EMCMapper.getEmcValueExact(s2));
    };

//	public static final Comparator<AbstractPage> PAGE_HEADER = (o1, o2) -> StatCollector.translateToLocal(o1.getHeaderText()).compareToIgnoreCase(StatCollector.translateToLocal(o2.getHeaderText()));
}
