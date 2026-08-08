package moze_intel.projecte.integration.Forestry;

import com.google.common.collect.ImmutableMap;
import forestry.api.recipes.ICarpenterRecipe;
import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.IFermenterRecipe;
import forestry.api.recipes.RecipeManagers;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.integration.AbstractIntegrationMapper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;

public class ForestryMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        for (int i = 1; i <= 28; i++) {
            addMapping("Forestry:logs", i, 32); // 原木
            addMapping("Forestry:logsFireproof", i, 32); // 抗燃原木
        }

		for (int i = 0; i <= 6; i++)
			addMapping("Forestry:fruits", i, 24); // 樱桃, 核桃, 栗子, 柠檬, 李子, 枣椰, 木瓜

		addMapping("Forestry:pollen", 32); // 花粉
		addMapping("Forestry:pollen", 1, 512); // 水晶花粉
		addMapping("Forestry:ash", 32); // 灰烬
		addMapping("Forestry:propolis", 64); // 蜂胶
		addMapping("Forestry:propolis", 1, 64); // 粘性蜂胶
		addMapping("Forestry:propolis", 2, 48); // 脉动蜂胶
		addMapping("Forestry:propolis", 3, 64); // 丝滑蜂胶
		addMapping("Forestry:phosphor", 64); // 荧光粉
		addMapping("Forestry:beeswax", 64); // 蜂蜡
		addMapping("Forestry:craftingMaterial", 2, 16); // 丝缕
		addMapping("Forestry:craftingMaterial", 5, 8); // 碎冰
		addMapping("Forestry:honeyDrop", 128); // 蜂蜜滴
		addMapping("Forestry:apatite", 128); // 磷灰石
		addMapping("Forestry:peat", 128); // 泥炭
		addMapping("Forestry:royalJelly", 512); // 蜂王浆
		addMapping("Forestry:grafterProven", 16384); // 标定剪枝器

		carpenter:
		for (ICarpenterRecipe recipe : RecipeManagers.carpenterManager.recipes()) {
			IDescriptiveRecipe grid = recipe.getCraftingGridRecipe();
			ItemStack out = grid.getRecipeOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(out);
			if (nssOut == null) continue;

			Object[] ingredients = grid.getIngredients();
			IngredientMap<NormalizedSimpleStack> inMap = new IngredientMap<>();
			for (Object input : ingredients) {
				if (input == null) continue;
				if (input instanceof ItemStack is) {
					inMap.addIngredient(NormalizedSimpleStack.forItem(is), is.stackSize);
				}
				else if (input instanceof Iterable<?> list) {
					NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(list.toString());
					inMap.addIngredient(fake, 1);
					for (Object obj : list) {
						if (obj == null) continue;
						if (obj instanceof ItemStack stack) {
							mapper.addConversion(1, fake, ImmutableMap.of(NormalizedSimpleStack.forItem(stack), stack.stackSize));
						}
						else {
							PELogger.logWarn("Illegal Ingredient in Crafting Recipe: %s", obj);
						}
					}
				}
				else if (input instanceof ItemStack[] isArr) {
					NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(Arrays.toString(isArr));
					inMap.addIngredient(fake, 1);
					for (Object obj : isArr) {
						if (obj == null) continue;
						if (obj instanceof ItemStack stack) {
							mapper.addConversion(1, fake, ImmutableMap.of(NormalizedSimpleStack.forItem(stack), stack.stackSize));
						}
						else {
							PELogger.logWarn("Illegal Ingredient in Crafting Recipe: %s", obj);
						}
					}
				}
				else {
					PELogger.logWarn("Cannot process ingredient %s in %s", input, ingredients);
					continue carpenter;
				}
			}
			ItemStack box = recipe.getBox();
			if (box != null) inMap.addIngredient(NormalizedSimpleStack.forItem(box), box.stackSize);
			FluidStack fluid = recipe.getFluidResource();
			if (fluid != null) inMap.addIngredient(NormalizedSimpleStack.forFluid(fluid), fluid.amount);
			mapper.addConversion(out.stackSize, nssOut, inMap.getMap());
		}

		/* 离心机: 不做处理 (RecipeManagers.centrifugeManager) */

		/* 发酵机: 2+1 -> 1(l), 忽略肥料输入 */
		for (IFermenterRecipe recipe : RecipeManagers.fermenterManager.recipes()) {
			NormalizedSimpleStack itemIn = NormalizedSimpleStack.forItem(recipe.getResource()),
				fluidIn = NormalizedSimpleStack.forFluid(recipe.getFluidResource()),
				fluidOut = NormalizedSimpleStack.forFluid(recipe.getOutput());
			if (itemIn == null || fluidIn == null) continue;
			int fluidInAmount = recipe.getFermentationValue();
			int fluidOutAmount = (int) (recipe.getModifier() * fluidInAmount);
			mapper.addConversion(fluidOutAmount, fluidOut, ImmutableMap.of(itemIn, 1, fluidIn, fluidInAmount));
		}

		/* 加湿器 */
		NormalizedSimpleStack nssWater = NormalizedSimpleStack.forFluid(FluidRegistry.WATER),
			wheat = NormalizedSimpleStack.forItem(Items.wheat),
			mouldyWheat = NormalizedSimpleStack.forItem("Forestry:mouldyWheat", 0),
			decayingWheat = NormalizedSimpleStack.forItem("Forestry:decayingWheat", 0),
			mulch = NormalizedSimpleStack.forItem("Forestry:mulch", 0);
		mapper.addConversion(1, mouldyWheat, ImmutableMap.of(wheat, 1, nssWater, 75));
		if (mouldyWheat != null)
			mapper.addConversion(1, decayingWheat, ImmutableMap.of(mouldyWheat, 1, nssWater, 75));
		if (decayingWheat != null)
			mapper.addConversion(1, mulch, ImmutableMap.of(decayingWheat, 1, nssWater, 75));
    }
}
