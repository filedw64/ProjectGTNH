package moze_intel.projecte.emc;

import com.google.common.collect.Lists;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.utils.Comparators;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class FuelMapper
{
	private static final List<SimpleStack> FUEL_MAP = Lists.newArrayList();

	public static void loadMap()
	{
		FUEL_MAP.clear(); // 直接 clear，不需要判空

		addToMap(new ItemStack(Items.coal, 1, 1));
		addToMap(new ItemStack(Items.redstone));
		addToMap(new ItemStack(Blocks.redstone_block));
		addToMap(new ItemStack(Items.coal));
		addToMap(new ItemStack(Blocks.coal_block));
		addToMap(new ItemStack(Items.gunpowder));
		addToMap(new ItemStack(Items.glowstone_dust));
		addToMap(new ItemStack(ObjHandler.fuels, 1, 0));
		addToMap(new ItemStack(ObjHandler.fuelBlock, 1, 0));
		addToMap(new ItemStack(Items.blaze_powder));
		addToMap(new ItemStack(Blocks.glowstone));
		addToMap(new ItemStack(ObjHandler.fuels, 1, 1));
		addToMap(new ItemStack(ObjHandler.fuelBlock, 1, 1));
		addToMap(new ItemStack(ObjHandler.fuels, 1, 2));
		addToMap(new ItemStack(ObjHandler.fuelBlock, 1, 2));

		FUEL_MAP.sort(Comparators.SIMPLESTACK_ASCENDING);
	}

	private static void addToMap(ItemStack stack)
	{
		if (EMCHelper.doesItemHaveEmc(stack))
		{
			addToMap(new SimpleStack(stack));
		}
	}

	public static boolean isStackFuel(ItemStack stack)
	{
		return indexInMap(stack) != -1;
	}

	public static boolean isStackMaxFuel(ItemStack stack)
	{
		return indexInMap(stack) == FUEL_MAP.size() - 1;
	}

	public static ItemStack getFuelUpgrade(ItemStack stack)
	{
		int index = indexInMap(stack);

		if (index == -1)
		{
			PELogger.logFatal("Tried to upgrade invalid fuel: " + stack);
			return null;
		}

		int nextIndex = index == FUEL_MAP.size() - 1 ? 0 : index + 1;

		return FUEL_MAP.get(nextIndex).toItemStack();
	}

	private static void addToMap(SimpleStack stack)
	{
		if (stack.isValid())
		{
			SimpleStack copy = stack.copy();
			copy.qnty = 1;

			if (!FUEL_MAP.contains(copy))
			{
				FUEL_MAP.add(copy);
			}
		}
	}

	// 复用单次创建的 SimpleStack 对象
	private static int indexInMap(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null)
		{
			return -1;
		}
		SimpleStack ss = new SimpleStack(stack);
		ss.qnty = 1;
		return FUEL_MAP.indexOf(ss);
	}
}
