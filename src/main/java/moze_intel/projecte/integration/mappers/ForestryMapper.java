package moze_intel.projecte.integration.mappers;

import com.google.common.collect.ImmutableMap;
import forestry.api.recipes.ICarpenterRecipe;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.IFabricatorRecipe;
import forestry.api.recipes.IFabricatorSmeltingRecipe;
import forestry.api.recipes.IFermenterRecipe;
import forestry.api.recipes.ISqueezerRecipe;
import forestry.api.recipes.IStillRecipe;
import forestry.api.recipes.RecipeManagers;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;

/**
 * Add emc value for Forestry Items, and process Forestry recipes.
 *
 * @author filedw64
 * @author WindyBye
 */
public class ForestryMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        for (int i = 1; i <= 28; i++) {
            addMapping("Forestry:logs", i, 32); // 原木
            addMapping("Forestry:logsFireproof", i, 32); // 抗燃原木
        }
        for (int i = 0; i <= 6; i++) {
            addMapping("Forestry:fruits", i, 24); // 樱桃, 核桃, 栗子, 柠檬, 李子, 枣椰, 木瓜
        }

        addMapping("Forestry:pollen", 32); // 花粉
        addMapping("Forestry:pollen", 1, 512); // 水晶花粉
        addMapping("Forestry:ash", 32); // 灰烬
        addMapping("Forestry:propolis", 64); // 蜂胶
        addMapping("Forestry:propolis", 1, 64); // 粘性蜂胶
        addMapping("Forestry:propolis", 2, 48); // 脉动蜂胶
        addMapping("Forestry:propolis", 3, 64); // 丝滑蜂胶
        addMapping("Forestry:phosphor", 64); // 荧光粉
		addMapping("Forestry:beeswax", 64); // 蜂蜡
		addMapping("Forestry:refractoryWax", 96); // 抗燃蜂蜡
        addMapping("Forestry:craftingMaterial", 2, 16); // 丝缕
        addMapping("Forestry:craftingMaterial", 5, 8); // 碎冰
		addMapping("Forestry:honeyDrop", 128); // 蜂蜜滴
		addMapping("Forestry:honeydew", 128); // 蜂蜜汁
        addMapping("Forestry:apatite", 128); // 磷灰石
        addMapping("Forestry:peat", 128); // 泥炭
        addMapping("Forestry:royalJelly", 512); // 蜂王浆
        addMapping("Forestry:grafterProven", 16384); // 标定剪枝器

        // 加湿器
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

        // 木工机
		carpenter:
		for (ICarpenterRecipe recipe : RecipeManagers.carpenterManager.recipes()) {
			IDescriptiveRecipe grid = recipe.getCraftingGridRecipe();
			ItemStack out = grid.getRecipeOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(out);
			if (nssOut == null) continue;

			Object[] ingredients = grid.getIngredients();
			IngredientMap<NormalizedSimpleStack> inMap = new IngredientMap<>();

			for (Object input : ingredients) {
				if (!addIngredientToMap(inMap, input)) {
					PELogger.logWarn("Cannot process ingredient %s in %s", input, ingredients);
					continue carpenter;
				}
			}

			ItemStack box = recipe.getBox();
			if (box != null) {
				NormalizedSimpleStack nssBox = NormalizedSimpleStack.forItem(box);
				if (nssBox != null)
					inMap.addIngredient(nssBox, box.stackSize);
			}

			FluidStack fluid = recipe.getFluidResource();
			if (fluid != null) {
				NormalizedSimpleStack nssFluid = NormalizedSimpleStack.forFluid(fluid);
				if (nssFluid != null)
					inMap.addIngredient(nssFluid, fluid.amount);
			}

			mapper.addConversion(out.stackSize, nssOut, inMap.getMap());
		}

		// 加工台熔融玻璃
		for (IFabricatorSmeltingRecipe recipe : RecipeManagers.fabricatorSmeltingManager.recipes()) {
			ItemStack in = recipe.getResource();
			FluidStack out = recipe.getProduct();
			NormalizedSimpleStack inItem = NormalizedSimpleStack.forItem(in),
				outFluid = NormalizedSimpleStack.forFluid(out);
			if (inItem == null || outFluid == null) continue;
			mapper.addConversion(out.amount, outFluid, ImmutableMap.of(inItem, in.stackSize));
		}

		// 热电子加工台
		fabricator:
		for (IFabricatorRecipe recipe : RecipeManagers.fabricatorManager.recipes()) {
			ItemStack output = recipe.getRecipeOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(output);
			if (nssOut == null) continue;

			IngredientMap<NormalizedSimpleStack> inMap = new IngredientMap<>();

			FluidStack fluid = recipe.getLiquid();
			NormalizedSimpleStack nssFluid = NormalizedSimpleStack.forFluid(fluid);
			if (nssFluid != null)
				inMap.addIngredient(nssFluid, fluid.amount);
			else {
				PELogger.logWarn("Cannot process ingredient %s", fluid);
				continue;
			}

			if (recipe.getPlan() != null)
				continue;

			Object[] ingredients = recipe.getIngredients();
			for (Object input : ingredients) {
				if (!addIngredientToMap(inMap, input))
					continue fabricator;
			}

			mapper.addConversion(output.stackSize, nssOut, inMap.getMap());
		}

		// 榨汁机
		squeezer:
		for (ISqueezerRecipe recipe : RecipeManagers.squeezerManager.recipes()) {
			FluidStack outFluid = recipe.getFluidOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forFluid(outFluid);
			if (nssOut == null) continue;

			IngredientMap<NormalizedSimpleStack> inMap = new IngredientMap<>();
			for (ItemStack is : recipe.getResources()) {
				if (is == null) continue;
				NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(is);
				if (nss == null) {
					PELogger.logWarn("Cannot process ingredient %s", is);
					continue squeezer;
				}
				inMap.addIngredient(nss, is.stackSize);
			}

			mapper.addConversion(outFluid.amount, nssOut, inMap.getMap());
		}

		// 蒸馏器
		for (IStillRecipe recipe : RecipeManagers.stillManager.recipes()) {
			FluidStack inFluid = recipe.getInput();
			FluidStack outFluid = recipe.getOutput();
			NormalizedSimpleStack nssIn = NormalizedSimpleStack.forFluid(inFluid);
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forFluid(outFluid);
			if (nssIn != null && nssOut != null)
				mapper.addConversion(outFluid.amount, nssOut, ImmutableMap.of(nssIn, inFluid.amount));
		}

		// 发酵机: 忽略肥料输入
		for (IFermenterRecipe recipe : RecipeManagers.fermenterManager.recipes()) {
			NormalizedSimpleStack itemIn = NormalizedSimpleStack.forItem(recipe.getResource()),
				fluidIn = NormalizedSimpleStack.forFluid(recipe.getFluidResource()),
				fluidOut = NormalizedSimpleStack.forFluid(recipe.getOutput());
			if (itemIn == null || fluidIn == null || fluidOut == null) continue;

			int fluidInAmount = recipe.getFermentationValue();
			int fluidOutAmount = (int) (recipe.getModifier() * fluidInAmount);
			mapper.addConversion(fluidOutAmount, fluidOut, ImmutableMap.of(itemIn, 1, fluidIn, fluidInAmount));
		}

		// 离心机: 反向处理蜂巢 (RecipeManagers.centrifugeManager)
		for (ICentrifugeRecipe recipe : RecipeManagers.centrifugeManager.recipes()) {
			ItemStack input = recipe.getInput();
			NormalizedSimpleStack nssIn = NormalizedSimpleStack.forItem(input);
			if (nssIn == null) continue;
			IngredientMap<NormalizedSimpleStack> inMap = new IngredientMap<>();
			recipe.getAllProducts().forEach((stack, chance) -> {
				if (stack == null) return;
				NormalizedSimpleStack out = NormalizedSimpleStack.forItem(stack);
				inMap.addIngredient(out, (int) (stack.stackSize * chance * 10));
			});
			mapper.addConversion(input.stackSize * 10, nssIn, inMap.getMap());
		}
	}


    private boolean addIngredientToMap(IngredientMap<NormalizedSimpleStack> inMap, Object input) {
        if (input == null) return true;

        if (input instanceof ItemStack is) {
			NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(is);
            if (nss == null)
				return false;
			inMap.addIngredient(nss, is.stackSize);
            return true;
        }
        else if (input instanceof Iterable<?> list) {
			NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(list.toString());
            inMap.addIngredient(fake, 1);
            for (Object obj : list) {
                if (obj instanceof ItemStack stack) {
					NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(stack);
                    if (nss != null)
						mapper.addConversion(1, fake, ImmutableMap.of(nss, stack.stackSize));
                }
				else {
                    PELogger.logWarn("Illegal Ingredient in Crafting Recipe Iterable: %s", obj);
                }
            }
            return true;
        }
        else if (input instanceof ItemStack[] isArr) {
			NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(Arrays.toString(isArr));
            inMap.addIngredient(fake, 1);
            for (ItemStack stack : isArr) {
				NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(stack);
				if (nss != null)
					mapper.addConversion(1, fake, ImmutableMap.of(nss, stack.stackSize));
				else
					PELogger.logWarn("Illegal Ingredient in Crafting Recipe Array: %s", stack);
            }
            return true;
        }
        return false;
    }
}
