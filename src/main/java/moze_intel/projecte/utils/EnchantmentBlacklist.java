package moze_intel.projecte.utils;

import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.integration.GregTech.GTHelper;
import moze_intel.projecte.integration.GregTech.GTSimpleStack;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public final class EnchantmentBlacklist {
    private static final Set<SimpleStack> blacklist = new HashSet<>();

    public static boolean add(ItemStack stack) {
        SimpleStack ss;
        if (GTHelper.isGTtool(stack))
            ss = new GTSimpleStack(stack);
        else ss = new SimpleStack(stack);
        if (!ss.isValid()) return false;
        ss.qnty = 1;
        return blacklist.add(ss);
    }

    public static boolean contains(ItemStack stack) {
        SimpleStack ss;
        if (GTHelper.isGTtool(stack))
            ss = new GTSimpleStack(stack);
        else ss = new SimpleStack(stack);
        if (!ss.isValid()) return false;
        ss.qnty = 1;
        return blacklist.contains(ss);
    }
}
