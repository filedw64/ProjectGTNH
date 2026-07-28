package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.common.config.Configuration;

public class SmeltingMapper implements IEMCMapper<NormalizedSimpleStack, Double> {
	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		FurnaceRecipes.smelting().getSmeltingList().forEach((input, output) -> {
			if (input == null || output == null) {
				return;
			}
			IngredientMap<NormalizedSimpleStack> map = new IngredientMap<>();
			NormalizedSimpleStack normInput = NormalizedSimpleStack.getFor(input),
				normOutput = NormalizedSimpleStack.getFor(output);
			map.addIngredient(normInput, input.stackSize);
			mapper.addConversion(output.stackSize, normOutput, map.getMap());
		});
	}

	@Override
	public String getName() {
		return "SmeltingMapper";
	}

	@Override
	public String getDescription() {
		return "Add Conversions for `FurnaceRecipes`";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}