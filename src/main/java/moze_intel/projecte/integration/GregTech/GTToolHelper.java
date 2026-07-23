package moze_intel.projecte.integration.GregTech;

import moze_intel.projecte.emc.EMCMapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class GTToolHelper {
    public static boolean isGTtool(Item item) {
        if (item == null) return false;
        return Item.itemRegistry.getNameForObject(item).startsWith("gregtech:gt.metatool");
    }

    public static boolean isGTtool(ItemStack is) {
        if (is == null) return false;
        return isGTtool(is.getItem());
    }

    public static boolean isGTtool(int id) {
        if (id < 0) return false;
        return isGTtool(Item.getItemById(id));
    }

    public static boolean isNullGTtool(ItemStack is) {
        return isGTtool(is) && (!is.hasTagCompound() || is.getTagCompound().hasNoTags());
    }

    public static double GTtoolEMC(ItemStack is) {
        GTSimpleStack ss = new GTSimpleStack(is);
        if (!EMCMapper.mapContains(ss))
            return 0.0;
        double res = EMCMapper.getEmcValue(ss);
        if (!is.hasTagCompound() || is.getTagCompound().hasNoTags()) return res;
        NBTTagCompound nbt = is.getTagCompound().getCompoundTag("GT.ToolStats");
        long damage = nbt.getLong("Damage"), maxdamage = nbt.getLong("MaxDamage");
        if (damage == maxdamage || maxdamage == 0) return 0.0;
        return res * (maxdamage - damage) / maxdamage;
    }
}
