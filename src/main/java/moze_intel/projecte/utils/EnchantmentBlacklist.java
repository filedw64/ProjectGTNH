package moze_intel.projecte.utils;

import moze_intel.projecte.emc.SimpleStack;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class EnchantmentBlacklist {
    private static final Set<SimpleStack> blacklist = new HashSet<>();

    public static boolean add(ItemStack stack) {
        SimpleStack ss = new SimpleStack(stack);
        if (!ss.isValid()) return false;
        ss.qnty = 1;
        if (blacklist.contains(ss)) return false;
        blacklist.add(ss);
        return true;
    }

    public static boolean contains(ItemStack stack) {
        SimpleStack ss = new SimpleStack(stack);
        if (!ss.isValid()) return false;
        ss.qnty = 1;
        return blacklist.contains(ss);
    }
}
