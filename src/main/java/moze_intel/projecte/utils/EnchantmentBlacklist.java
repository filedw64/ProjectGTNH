package moze_intel.projecte.utils;

import moze_intel.projecte.emc.SimpleStack;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class EnchantmentBlacklist {
    private static final Set<SimpleStack> blacklist = new HashSet<>();

    public static boolean add(ItemStack stack) {
        SimpleStack ss = SimpleStack.getFor(stack);
        if (!ss.isValid()) return false;
		return blacklist.add(ss);
    }

    public static boolean contains(ItemStack stack) {
		SimpleStack ss = SimpleStack.getFor(stack);
        if (!ss.isValid()) return false;
		return blacklist.contains(ss);
    }
}
