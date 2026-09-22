package moze_intel.projecte.integration.mappers;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.recipe.RecipeBrew;
import vazkii.botania.api.recipe.RecipeElvenTrade;
import vazkii.botania.api.recipe.RecipeManaInfusion;
import vazkii.botania.api.recipe.RecipePetals;
import vazkii.botania.api.recipe.RecipePureDaisy;
import vazkii.botania.api.recipe.RecipeRuneAltar;

import java.util.HashMap;
import java.util.Map;

/**
 * Add emc value for Botania items, and process Botania recipes.
 *
 * @author Windy-Bye
 */
public class BotaniaMapper extends AbstractIntegrationMapper {

	@Override
	protected void doAddMappings() {
		// 定义 1 Mana = 1 EMC
		NormalizedSimpleStack manaFake = NormalizedSimpleStack.forFake("Botania_Mana");
		addMapping(manaFake, 1.0);

		// Petal Apothecary
		for (RecipePetals recipe : BotaniaAPI.petalRecipes) {
			if (recipe.getOutput() == null) continue;
			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipe.getOutput());
			if (outNSS == null) continue;

			Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
			for (Object in : recipe.getInputs())
				addInputToMap(in, inputs, 1);
			mapper.addConversion(recipe.getOutput().stackSize, outNSS, inputs);
		}

		// Runic Altar
		for (RecipeRuneAltar recipe : BotaniaAPI.runeAltarRecipes) {
			if (recipe.getOutput() == null) continue;
			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipe.getOutput());
			if (outNSS == null) continue;

			Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
			for (Object in : recipe.getInputs()) {
				addInputToMap(in, inputs, 1);
			}
			// 加上魔力消耗
			if (recipe.getManaUsage() > 0) {
				inputs.put(manaFake, inputs.getOrDefault(manaFake, 0) + recipe.getManaUsage());
			}
			mapper.addConversion(recipe.getOutput().stackSize, outNSS, inputs);
		}

		// Mana Infusion / Alchemy
		for (RecipeManaInfusion recipe : BotaniaAPI.manaInfusionRecipes) {
			// 跳过带有复制性质的配方
			if (recipe.getOutput() == null || recipe.isConjuration()) continue;

			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipe.getOutput());
			if (outNSS == null) continue;

			Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
			addInputToMap(recipe.getInput(), inputs, 1);

			if (recipe.getManaToConsume() > 0) {
				inputs.put(manaFake, inputs.getOrDefault(manaFake, 0) + recipe.getManaToConsume());
			}
			mapper.addConversion(recipe.getOutput().stackSize, outNSS, inputs);
		}

		// Pure Daisy
		for (RecipePureDaisy recipe : BotaniaAPI.pureDaisyRecipes) {
			if (recipe.getOutput() == null) continue;
			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipe.getOutput(), recipe.getOutputMeta());
			if (outNSS == null) continue;

			Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
			addInputToMap(recipe.getInput(), inputs, 1);
			mapper.addConversion(1, outNSS, inputs);
		}

		// Elven Trade
		for (RecipeElvenTrade recipe : BotaniaAPI.elvenTradeRecipes) {
			if (recipe.getOutput() == null) continue;
			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipe.getOutput());
			if (outNSS == null) continue;

			Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
			for (Object in : recipe.getInputs()) {
				addInputToMap(in, inputs, 1);
			}
			mapper.addConversion(recipe.getOutput().stackSize, outNSS, inputs);
		}

		// Botanical Brewery
		Item vialItem = (Item) Item.itemRegistry.getObject("Botania:vial");
		Item bloodPendantItem = (Item) Item.itemRegistry.getObject("Botania:bloodPendant");

		if (vialItem != null) {
			// meta 0 是药瓶, meta 1 是细口瓶
			ItemStack[] containers = new ItemStack[]{
				new ItemStack(vialItem, 1, 0),
				new ItemStack(vialItem, 1, 1),
				(bloodPendantItem != null ? new ItemStack(bloodPendantItem, 1, 0) : null)
			};

			for (RecipeBrew recipe : BotaniaAPI.brewRecipes) {
				for (ItemStack container : containers) {
					if (container == null) continue;

					ItemStack outStack = recipe.getOutput(container);
					// 如果输出是原版的玻璃瓶，说明该配方不支持此容器
					if (outStack == null || outStack.getItem() == Items.glass_bottle) continue;

					NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(outStack);
					if (outNSS == null) continue;

					Map<NormalizedSimpleStack, Integer> inputs = new HashMap<>();
					addInputToMap(container, inputs, 1); // 计入容器的成本

					for (Object in : recipe.getInputs()) {
						addInputToMap(in, inputs, 1);
					}
					if (recipe.getManaUsage() > 0) {
						inputs.put(manaFake, inputs.getOrDefault(manaFake, 0) + recipe.getManaUsage());
					}
					mapper.addConversion(outStack.stackSize, outNSS, inputs);
				}
			}
		}
	}

	/**
	 * 辅助方法：将不同类型的输入安全地转化为 NormalizedSimpleStack 并加入 Map 中
	 */
	private void addInputToMap(Object input, Map<NormalizedSimpleStack, Integer> map, int amount) {
		if (input == null) return;
		NormalizedSimpleStack nss = null;

		if (input instanceof String od)
			nss = NormalizedSimpleStack.forOreDictionary(od); // 直接交由 ProjectE 的 OreDict 节点来接管矿物词典
		else if (input instanceof ItemStack stack)
			nss = NormalizedSimpleStack.forItem(stack);
		else if (input instanceof Block block)
			nss = NormalizedSimpleStack.forItem(block); // 对于单纯的 Block 对象，默认 meta 为 0

		if (nss != null)
			map.put(nss, map.getOrDefault(nss, 0) + amount);
	}
}
