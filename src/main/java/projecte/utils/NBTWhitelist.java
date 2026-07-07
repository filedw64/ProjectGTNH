package projecte.utils;

import com.google.common.collect.Lists;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import projecte.emc.SimpleStack;

import java.util.List;

public final class NBTWhitelist
{
	private static final List<SimpleStack> LIST = Lists.newArrayList();

	public static boolean register(ItemStack stack)
	{
		SimpleStack s = new SimpleStack(stack);

		if (!s.isValid())
		{
			return false;
		}

		s.qnty = 1;
		s.damage = OreDictionary.WILDCARD_VALUE;

		if (!LIST.contains(s))
		{
			LIST.add(s);
			return true;
		}

		return false;
	}

	public static boolean shouldDupeWithNBT(ItemStack stack)
	{
		SimpleStack s = new SimpleStack(stack);

		if (!s.isValid())
		{
			return false;
		}

		return LIST.contains(s);
	}
}
