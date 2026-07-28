package moze_intel.projecte.integration.GregTech;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.integration.AbstractIntegrationMapper;
import moze_intel.projecte.utils.PELogger;

public class GTMapper extends AbstractIntegrationMapper {
    public static void init() {
        PELogger.logTrace("RecipeMap compressorRecipes has %s recipes", RecipeMaps.compressorRecipes.getAllRecipes().size());
    }

    @Override
    protected void doAddMappings() {
        // 压缩机: 1+1 -> 1
        for (GTRecipe gtre : RecipeMaps.compressorRecipes.getAllRecipes()) {
            IngredientMap<NormalizedSimpleStack> ingredientMap = new IngredientMap<>();
            ingredientMap.addIngredient(NormalizedSimpleStack.getFor(gtre.mInputs[0]), gtre.mInputs[0].stackSize);
            if (gtre.mFluidInputs.length > 0) {
                ingredientMap.addIngredient(NormalizedSimpleStack.getFor(gtre.mFluidInputs[0].getFluid()), gtre.mFluidInputs[0].amount);
            }
            mapper.addConversion(gtre.mOutputs[0].stackSize, NormalizedSimpleStack.getFor(gtre.mOutputs[0]), ingredientMap.getMap());
        }
    }
}