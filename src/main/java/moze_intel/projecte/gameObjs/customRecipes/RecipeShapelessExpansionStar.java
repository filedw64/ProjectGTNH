package moze_intel.projecte.gameObjs.customRecipes;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.gameObjs.ObjHandler;

import java.util.ArrayList;
import java.util.List;

public class RecipeShapelessExpansionStar extends ShapelessRecipes
{
	public RecipeShapelessExpansionStar(ItemStack output, List inputs)
	{
		super(output, inputs);
	}

	/**
	 * 用于检查合成网格中的物品是否匹配当前配方
	 */
	@Override
	public boolean matches(InventoryCrafting inv, World world)
	{
		// 直接使用原版 ShapelessRecipes 提供的无序匹配逻辑，这样最稳定
		return super.matches(inv, world);
	}

	/**
	 * 返回合成的结果（在这里处理能量 EMC 的继承转移）
	 */
	@Override
	public ItemStack getCraftingResult(InventoryCrafting inv)
	{
		// 复制一份产物，避免污染原配方的 output
		ItemStack result = this.getRecipeOutput().copy();
		double storedEMC = 0;

		// 遍历合成网格，提取所有作为材料的星星内的电量
		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			ItemStack stack = inv.getStackInSlot(i);
			if (stack != null && stack.getItem() instanceof IItemEmc)
			{
				storedEMC += ((IItemEmc) stack.getItem()).getStoredEmc(stack);
			}
		}

		// 如果收集到了电量，将其充入新合成的产物中
		if (storedEMC > 0 && result.getItem() instanceof IItemEmc)
		{
			((IItemEmc) result.getItem()).addEmc(result, storedEMC);
		}

		return result;
	}

	// 接管所有的配方注册逻辑，保持外部代码整洁

	public static void registerRecipes()
	{
		// 1. 马格南系列 (1级 = 4 * 6级卡莱恩， n级 = 4 * n-1级)
		addRecipe(new ItemStack(ObjHandler.magnumStar, 1, 0), new ItemStack(ObjHandler.kleinStars, 1, 5), 4);
		for (int i = 0; i < 5; i++)
		{
			addRecipe(new ItemStack(ObjHandler.magnumStar, 1, i + 1), new ItemStack(ObjHandler.magnumStar, 1, i), 4);
		}

		// 2. 葛甘图系列 (1级 = 9 * 6级马格南， n级 = 9 * n-1级)
		addRecipe(new ItemStack(ObjHandler.gargantuanStar, 1, 0), new ItemStack(ObjHandler.magnumStar, 1, 5), 9);
		for (int i = 0; i < 5; i++)
		{
			addRecipe(new ItemStack(ObjHandler.gargantuanStar, 1, i + 1), new ItemStack(ObjHandler.gargantuanStar, 1, i), 9);
		}

		// 3. 终焉系列 (1级 = 9 * 6级葛甘图， n级 = 9 * n-1级)
		addRecipe(new ItemStack(ObjHandler.colossalStar, 1, 0), new ItemStack(ObjHandler.gargantuanStar, 1, 5), 9);
		for (int i = 0; i < 5; i++)
		{
			addRecipe(new ItemStack(ObjHandler.colossalStar, 1, i + 1), new ItemStack(ObjHandler.colossalStar, 1, i), 9);
		}
	}

	/**
	 * 辅助注册方法：快速生成同一物品多数量的无序配方
	 */
	private static void addRecipe(ItemStack output, ItemStack input, int amount)
	{
		List<ItemStack> inputs = new ArrayList<ItemStack>();
		for (int i = 0; i < amount; i++)
		{
			inputs.add(input.copy());
		}
		// 注册自定义的继承类
		GameRegistry.addRecipe(new RecipeShapelessExpansionStar(output, inputs));
	}
}
