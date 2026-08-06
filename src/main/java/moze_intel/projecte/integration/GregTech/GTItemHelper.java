package moze_intel.projecte.integration.GregTech;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FluidSimpleStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class GTItemHelper {

    public static boolean isGTtool(Item item) {
        if (item == null) return false;
		String id = Item.itemRegistry.getNameForObject(item);
		if (id == null) return false;
        return id.startsWith("gregtech:gt.metatool");
    }

    public static boolean isGTtool(ItemStack is) {
        if (is == null) return false;
        return isGTtool(is.getItem());
    }

	public static boolean isNullGTtool(ItemStack is) {
		return isGTtool(is) && (is.stackTagCompound == null || is.stackTagCompound.hasNoTags());
	}
	
	public static boolean isGTfluidDisplay(Item item) {
		if (item == null) return false;
		String id = Item.itemRegistry.getNameForObject(item);
		if (id == null) return false;
		return id.equals("gregtech:gt.GregTech_FluidDisplay");
	}
	
	public static boolean isGTfluidDisplay(ItemStack is) {
		if (is == null) return false;
		return isGTfluidDisplay(is.getItem());
	}

    public static double GTtoolEMC(ItemStack is) {
        GTSimpleStack ss = new GTSimpleStack(is);
        if (!EMCMapper.mapContains(ss))
            return 0.0;
        double res = EMCMapper.getEmcValue(ss);
        if (!is.hasTagCompound() || is.stackTagCompound.hasNoTags()) return res;
        NBTTagCompound nbt = is.stackTagCompound.getCompoundTag("GT.ToolStats");
        long damage = nbt.getLong("Damage"), maxdamage = nbt.getLong("MaxDamage");
        if (damage == maxdamage || maxdamage == 0) return 0.0;
        return res * (maxdamage - damage) / maxdamage;
    }
	
	public static boolean isAsh(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return false;
		int[] oreIDs = OreDictionary.getOreIDs(stack);
		for (int id : oreIDs) {
			String oreName = OreDictionary.getOreName(id);
			if ("dustTinyAsh".equals(oreName) || "dustTinyDarkAsh".equals(oreName) ||
				"dustAsh".equals(oreName) || "dustDarkAsh".equals(oreName))
				return true;
		}
		return false;
	}
	
	public static double GTfluidDisplayEMC(ItemStack is) {
		if (!isGTfluidDisplay(is)) return 0.0;
		if (is.stackTagCompound == null || is.stackTagCompound.hasNoTags()) return 0.0;
		NBTTagCompound nbt = is.getTagCompound();
		long amount = nbt.getLong("mFluidDisplayAmount");
		if (amount <= 0) return 0.0;
		FluidSimpleStack ss = new FluidSimpleStack(is.getItemDamage(), 1);
		if (!EMCMapper.mapContains(ss)) return 0.0;
		return EMCMapper.getEmcValue(ss) * amount;
	}
}