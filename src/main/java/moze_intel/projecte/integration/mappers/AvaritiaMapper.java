package moze_intel.projecte.integration.mappers;

import com.google.common.collect.ImmutableMap;
import fox.spiteful.avaritia.crafting.CompressorManager;
import fox.spiteful.avaritia.crafting.CompressorRecipe;
import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import fox.spiteful.avaritia.crafting.ExtremeShapedOreRecipe;
import fox.spiteful.avaritia.crafting.ExtremeShapedRecipe;
import fox.spiteful.avaritia.crafting.ExtremeShapelessRecipe;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.ArrayList;

/**
 * Add emc value for Avaritia items, and process Avaritia recipes.
 *
 * @author WindyBye
 * @author filedw64
 */
public class AvaritiaMapper extends AbstractIntegrationMapper {
	@Override
	protected void doAddMappings() {
		addMapping("Avaritia:Resource", 2, 7111); // 1 emc/tick = 20 emc/s
		ExtremeCraftingManager manager = ExtremeCraftingManager.getInstance();
		craftRecipe:
		for (IRecipe recipe : manager.getRecipeList()) {
			if (recipe == null) continue;
			ItemStack out = recipe.getRecipeOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(out);
			if (nssOut == null) continue;
			IngredientMap<NormalizedSimpleStack> inputs = new IngredientMap<>();

			if (recipe instanceof ExtremeShapedRecipe shaped) {
				// 有序配方
				if (shaped.recipeItems == null) continue;
				for (ItemStack stack : shaped.recipeItems) {
					if (stack == null) continue;
					inputs.addIngredient(NormalizedSimpleStack.forItem(stack), stack.stackSize);
				}
			}
			else if (recipe instanceof ExtremeShapelessRecipe shapeless) {
				// 无序配方
				if (shapeless.recipeItems == null) continue;
				for (ItemStack stack : shapeless.recipeItems) {
					if (stack == null) continue;
					inputs.addIngredient(NormalizedSimpleStack.forItem(stack), stack.stackSize);
				}
			}
			else if (recipe instanceof ShapelessOreRecipe shapelessOre) {
				// 无序矿辞配方
				ArrayList<Object> arr;
				if ((arr = shapelessOre.getInput()) == null) continue;
				for (Object obj : arr) {
					if (!addIngredientToMap(inputs, obj)) {
						PELogger.logWarn("Cannot process ingredient %s in %s", obj, arr);
						continue craftRecipe;
					}
				}
			}
			else if (recipe instanceof ExtremeShapedOreRecipe shapedOre) {
				// 有序矿辞配方
				Object[] arr;
				if ((arr = shapedOre.getInput()) == null) continue;
				for (Object obj : arr) {
					if (!addIngredientToMap(inputs, obj)) {
						PELogger.logWarn("Cannot process ingredient %s in %s", obj, arr);
						continue craftRecipe;
					}
				}
			}
			else {
				PELogger.logWarn("Cannot process Avaritia recipe: %s", recipe);
				continue;
			}
			mapper.addConversion(out.stackSize, nssOut, inputs.getMap());
		}

		for (CompressorRecipe recipe : CompressorManager.getRecipes()) {
			if (recipe == null) continue;
			ItemStack out = recipe.getOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(out);
			if (nssOut == null) continue;
			Object in = recipe.getIngredient();
			if (in == null) continue;
			if (in instanceof ItemStack is) {
				NormalizedSimpleStack nssIn = NormalizedSimpleStack.forItem(is);
				if (nssIn == null) continue;
				mapper.addConversion(out.stackSize, nssOut, ImmutableMap.of(nssIn, recipe.getCost()));
			}
			else if (in instanceof ArrayList<?> list) {
				NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(list.toString());
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
				mapper.addConversion(out.stackSize, nssOut, ImmutableMap.of(fake, recipe.getCost()));
			}
			else {
				PELogger.logWarn("Cannot process Avaritia recipe: %s", recipe);
			}
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

		return false;
	}
}
