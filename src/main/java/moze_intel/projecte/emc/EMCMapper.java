package moze_intel.projecte.emc;

import moze_intel.projecte.PECore;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.collector.DoubleCollector;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.emc.generators.DoubleGenerator;
import moze_intel.projecte.emc.generators.IValueGenerator;
import moze_intel.projecte.emc.mappers.APICustomConversionMapper;
import moze_intel.projecte.emc.mappers.APICustomEMCMapper;
import moze_intel.projecte.emc.mappers.CraftingMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.emc.mappers.FluidMapper;
import moze_intel.projecte.emc.mappers.IEMCMapper;
import moze_intel.projecte.emc.mappers.IntegrationMapper;
import moze_intel.projecte.emc.mappers.LazyMapper;
import moze_intel.projecte.emc.mappers.OreDictionaryMapper;
import moze_intel.projecte.emc.mappers.SmeltingMapper;
import moze_intel.projecte.emc.mappers.customConversions.CustomConversionMapper;
import moze_intel.projecte.integration.GregTech.GTNSSItem;
import moze_intel.projecte.integration.GregTech.GTSimpleStack;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.PrefixConfiguration;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EMCMapper
{
    public static boolean enableNBTprocess = true;
	public static Map<SimpleStack, Double> emc = new HashMap<>();
	public static Map<NormalizedSimpleStack, Double> graphMapperValues;

	public static void map()
	{
		List<IEMCMapper<NormalizedSimpleStack, Double>> emcMappers = Arrays.asList(
			new OreDictionaryMapper(),
            new LazyMapper(),
            APICustomEMCMapper.instance,
            new CustomConversionMapper(),
            new CustomEMCMapper(),
            new CraftingMapper(),
            new FluidMapper(),
            new SmeltingMapper(),
            new APICustomConversionMapper(),
            new IntegrationMapper()
		);
        SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = new SimpleGraphMapper<>(new DoubleArithmetic());
		IValueGenerator<NormalizedSimpleStack, Double> valueGenerator = new DoubleGenerator<>(mapper);
		IMappingCollector<NormalizedSimpleStack, Double> mappingCollector = new DoubleCollector<>(mapper);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

        enableNBTprocess = config.getBoolean("enableNBTprocess", "general", true, "Process items that have different NBT tags as different items.");

		PELogger.logInfo("Start to collect Mappings");
		for (IEMCMapper<NormalizedSimpleStack, Double> emcMapper : emcMappers) {
			try {
				if (!config.getBoolean(emcMapper.getName(), "enabledMappers", emcMapper.isAvailable(), emcMapper.getDescription()) || !emcMapper.isAvailable()) {
					continue;
				}
				long start = System.currentTimeMillis();
				emcMapper.addMappings(mappingCollector, new PrefixConfiguration(config, "mapperConfigurations." + emcMapper.getName()));
				PELogger.logInfo("Collected Mappings from %s. (took %.3fs)", emcMapper.getClass().getName(), (System.currentTimeMillis() - start) / 1e3);
			}
			catch (Exception e) {
				PELogger.logFatal("Exception during Mapping Collection from Mapper %s. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!", emcMapper.getClass().getName());
				e.printStackTrace();
			}
		}
		long start = System.currentTimeMillis();
		NormalizedSimpleStack.addMappings(mappingCollector);
		PELogger.logInfo("Collected Mappings from NormalizedSimpleStack. (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		PELogger.logInfo("Mapping Collection finished");
		mappingCollector.finishCollection();

		config.save();
		PELogger.logInfo("Start to generate Values");

		start = System.currentTimeMillis();
		graphMapperValues = valueGenerator.generateValues();
		PELogger.logInfo("EMC Values Generated! (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		filterEMCMap(graphMapperValues);
		NormalizedSimpleStack.clearMap();

		graphMapperValues.forEach((nss, val) -> {
            if (nss instanceof NormalizedSimpleStack.NSSItem nssItem) {
                Object obj = Item.itemRegistry.getObject(nssItem.itemName);
				int id = Item.itemRegistry.getIDForObject(obj);
				if (nss instanceof GTNSSItem gtnssItem) {
					emc.put(new GTSimpleStack(id, 1, gtnssItem.damage, gtnssItem.primary, gtnssItem.secondary), val);
				}
				else emc.put(new SimpleStack(id, 1, nssItem.damage), val);
			}
			else if (nss instanceof NormalizedSimpleStack.NSSFluid nssFluid) {
				Fluid fluid = FluidRegistry.getFluid(nssFluid.name);
				emc.put(new FluidSimpleStack(fluid.getID(), 1), val);
			}
		});

		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	/**
	 * Remove all entrys from the map, that are not {@link NormalizedSimpleStack.NSSItem}s, have a value <= 0 or WILDCARD_VALUE as metadata.
	 */
	static void filterEMCMap(Map<NormalizedSimpleStack, Double> map) {
		map.keySet().removeIf(nss -> {
			if (map.get(nss) <= 0) return true;
			if (nss instanceof NormalizedSimpleStack.NSSItem nssItem)
				return nssItem.damage == OreDictionary.WILDCARD_VALUE;
			return !(nss instanceof NormalizedSimpleStack.NSSFluid);
		});
	}

	public static boolean mapContains(SimpleStack key)
	{
		SimpleStack copy = key.copy();
		copy.qnty = 1;
		return emc.containsKey(copy);
	}

	public static Double getEmcValue(SimpleStack stack)
	{
		SimpleStack copy = stack.copy();
		copy.qnty = 1;
		return emc.get(copy);
	}

	public static void clearMaps() {
		emc.clear();
	}
}
