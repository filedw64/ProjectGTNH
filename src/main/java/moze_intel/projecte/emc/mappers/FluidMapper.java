package moze_intel.projecte.emc.mappers;

import com.google.common.collect.ImmutableMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.integration.CCCInit;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class FluidMapper implements IEMCMapper<NormalizedSimpleStack, Double> {
	IMappingCollector<NormalizedSimpleStack, Double> mapper;

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		this.mapper = mapper;
		mapper.setValueBefore(NormalizedSimpleStack.forFluid(FluidRegistry.WATER), CCCInit.finiteWater ? 8e-3 : -Double.MAX_VALUE);

		// 1 Bucket of Lava = 1 Block of Obsidian
		// 替换 Arrays.asList 为 Collections.singletonList 减少内存分配
		mapper.addConversion(1000, NormalizedSimpleStack.forFluid(FluidRegistry.LAVA), Collections.singletonList(NormalizedSimpleStack.forItem(Blocks.obsidian)));

		// Add Conversion in case MFR is not present and milk is not an actual fluid
		NormalizedSimpleStack fakeMilkFluid = NormalizedSimpleStack.forFake("fakeMilkFluid");
		mapper.setValueBefore(fakeMilkFluid, 16.0);
		mapper.addConversion(1, NormalizedSimpleStack.forItem(Items.milk_bucket), Arrays.asList(NormalizedSimpleStack.forItem(Items.bucket), fakeMilkFluid));

		Fluid milkFluid = FluidRegistry.getFluid("milk");
		if (milkFluid != null) {
			mapper.addConversion(1000, NormalizedSimpleStack.forFluid(milkFluid), Collections.singletonList(fakeMilkFluid));
		}

		for (FluidContainerRegistry.FluidContainerData data : FluidContainerRegistry.getRegisteredFluidContainerData()) {
			Fluid fluid = data.fluid.getFluid();
			mapper.addConversion(1, NormalizedSimpleStack.forItem(data.filledContainer),
				ImmutableMap.of(NormalizedSimpleStack.forItem(data.emptyContainer), 1, NormalizedSimpleStack.forFluid(fluid), data.fluid.amount));
		}

		addMelting(Blocks.obsidian, "molten.obsidian", 288);
		addMelting(Blocks.obsidian, "obsidian.molten", 288);

		addMelting(Blocks.glass, "glass.molten", 1000);
		addMelting(Blocks.glass_pane, "glass.molten", 250);
		addMelting(Items.ender_pearl, "ender", 250);

		addMelting(Blocks.glass, "molten.glass", 144);

		for (String s : OreDictionary.getOreNames()) {
			if (s == null) continue;
			if (!s.startsWith("ingot")) continue;
			NormalizedSimpleStack nssOre = NormalizedSimpleStack.forOreDictionary(s);
			if (nssOre == null) continue;
			String ingotType = s.substring(5).toLowerCase(Locale.ROOT);

			NormalizedSimpleStack nssFluid = NormalizedSimpleStack.forFluid("molten.".concat(ingotType));
			if (nssFluid != null)
				mapper.addConversion(144, nssFluid, Collections.singletonList(nssOre));

			nssFluid = NormalizedSimpleStack.forFluid(ingotType.concat(".molten"));
			if (nssFluid != null)
				mapper.addConversion(144, nssFluid, Collections.singletonList(nssOre));
		}

		addMelting("gemEmerald", "emerald.liquid", 640);
		addMelting("dustRedstone", "redstone", 100);
		addMelting("dustGlowstone", "glowstone", 250);

		addMelting("dustCryotheum", "cryotheum", 100);
		addMelting("dustPryotheum", "pryotheum", 100);
	}

	public void addMelting(String odName, String fluidName, int amount) {
		NormalizedSimpleStack nss = NormalizedSimpleStack.forOreDictionary(odName);
		if (nss != null) addMelting(nss, fluidName, amount);
	}

	public void addMelting(Item item, String fluidName, int amount) {
		NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(item);
		if (nss != null) addMelting(nss, fluidName, amount);
	}

	public void addMelting(Block block, String fluidName, int amount) {
		NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(block);
		if (nss != null) addMelting(nss, fluidName, amount);
	}

	public void addMelting(NormalizedSimpleStack stack, String fluidName, int amount) {
		Fluid fluid = FluidRegistry.getFluid(fluidName);
		if (fluid != null) {
			mapper.addConversion(amount, NormalizedSimpleStack.forFluid(fluid), Collections.singletonList(stack));
		}
		else {
			// WARN 降级为 DEBUG
			PELogger.logDebug("Can not get Fluid '%s', skipping melting recipe.", fluidName);
		}
	}

	@Override
	public String getName() {
		return "FluidMapper";
	}

	@Override
	public String getDescription() {
		return "Adds Conversions for fluid container items and fluids.";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}
