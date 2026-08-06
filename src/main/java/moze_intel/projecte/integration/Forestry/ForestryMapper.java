package moze_intel.projecte.integration.Forestry;

import com.google.common.collect.ImmutableMap;
import forestry.api.recipes.ICarpenterRecipe;
import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.RecipeManagers;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.integration.AbstractIntegrationMapper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;

public class ForestryMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        for (int i = 1; i <= 28; i++) {
            addMapping("Forestry:logs", i, 32);
            addMapping("Forestry:logsFireproof", i, 32);
        }

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
    }
}