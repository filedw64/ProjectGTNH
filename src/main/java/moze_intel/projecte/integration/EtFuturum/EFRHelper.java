package moze_intel.projecte.integration.EtFuturum;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class EFRHelper {
    public static boolean isShulkerBox(Item item) {
        if (item == null) return false;
        return "etfuturum:shulker_box".equals(Item.itemRegistry.getNameForObject(item));
    }

    public static boolean isShulkerBox(ItemStack is) {
        if (is == null || is.getItem() == null) return false;
        return isShulkerBox(is.getItem());
    }

    public static double ShulkerBoxEMC(ItemStack is) {
        SimpleStack ss = new SimpleStack(is);
        if (!EMCMapper.mapContains(ss))
            return 0.0;
        double res = EMCMapper.getEmcValue(ss);
        if (!is.hasTagCompound() || is.stackTagCompound.hasNoTags()) return res;
        NBTTagList nbtlist = is.stackTagCompound.getTagList("Items", 10);
        for (int i = 0; i < nbtlist.tagCount(); i++) {
            NBTTagCompound tag = nbtlist.getCompoundTagAt(i);
            if (tag.hasNoTags())
                continue;
            ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
            if (stack == null || stack.getItem() == null)
                continue;
            double value = EMCHelper.getEmcValue(stack);
            if (value == 0)
                return 0.0;
            res += value * stack.stackSize;
        }
        return res;
    }
}