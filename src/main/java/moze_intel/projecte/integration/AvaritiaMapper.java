package moze_intel.projecte.integration;

import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

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
		for (IRecipe recipe : manager.getRecipeList()) {
			if (recipe == null) continue;
			ItemStack out = recipe.getRecipeOutput();
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.forItem(out);
			if (nssOut == null) continue;
			IngredientMap<NormalizedSimpleStack> inputs = new IngredientMap<>();

		}
	}
}
