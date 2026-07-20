package moze_intel.projecte.emc;

import com.google.common.collect.Maps;
import moze_intel.projecte.emc.mappers.APICustomConversionMapper;
import moze_intel.projecte.emc.mappers.APICustomEMCMapper;
import moze_intel.projecte.emc.mappers.Chisel2Mapper;
import moze_intel.projecte.emc.mappers.CraftingMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.emc.mappers.FluidMapper;
import moze_intel.projecte.emc.mappers.IEMCMapper;
import moze_intel.projecte.emc.mappers.IntegrationMapper;
import moze_intel.projecte.emc.mappers.LazyMapper;
import moze_intel.projecte.emc.mappers.OreDictionaryMapper;
import moze_intel.projecte.emc.mappers.SmeltingMapper;
import moze_intel.projecte.integration.GregTech.GTNSSItem;
import moze_intel.projecte.integration.GregTech.GTSimpleStack;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.emc.collector.DoubleCollector;
import moze_intel.projecte.emc.collector.DumpToFileCollector;
import moze_intel.projecte.emc.collector.IExtendedMappingCollector;
import moze_intel.projecte.emc.generators.DoubleGenerator;
import moze_intel.projecte.emc.generators.IValueGenerator;
import moze_intel.projecte.emc.mappers.customConversions.CustomConversionMapper;
import moze_intel.projecte.emc.pregenerated.PregeneratedEMC;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.PrefixConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EMCMapper
{
    public static boolean enableNBTprocess = true;
	public static Map<SimpleStack, Double> emc = new LinkedHashMap<>();
	public static Map<NormalizedSimpleStack, Double> graphMapperValues;

	public static void map()
	{
		List<IEMCMapper<NormalizedSimpleStack, Double>> emcMappers = Arrays.asList(
            new OreDictionaryMapper(),
            new LazyMapper(),
            new Chisel2Mapper(),
            APICustomEMCMapper.instance,
            new CustomConversionMapper(),
            new CustomEMCMapper(),
            new CraftingMapper(),
            new FluidMapper(),
            new SmeltingMapper(),
            new APICustomConversionMapper(),
            IntegrationMapper.instance
		);
        SimpleGraphMapper<NormalizedSimpleStack, Double, IValueArithmetic<Double>> mapper = new SimpleGraphMapper<>(new DoubleArithmetic());
		IValueGenerator<NormalizedSimpleStack, Double> valueGenerator = new DoubleGenerator<>(mapper);
		IExtendedMappingCollector<NormalizedSimpleStack, Double, IValueArithmetic<Double>> mappingCollector = new DoubleCollector<>(mapper);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

        enableNBTprocess = config.getBoolean("enableNBTprocess", "general", true, "Process items that have different NBT tags as different items.");

		if (config.getBoolean("dumpEverythingToFile", "general", false,"Want to take a look at the internals of EMC Calculation? Enable this to write all the conversions and setValue-Commands to config/ProjectE/mappingdump.json")) {
			mappingCollector = new DumpToFileCollector<>(new File(PECore.CONFIG_DIR, "mappingdump.json"), mappingCollector);
		}

		boolean shouldUsePregenerated = config.getBoolean("pregenerate", "general", false, "When the next EMC mapping occurs write the results to config/ProjectE/pregenerated_emc.json and only ever run the mapping again" +
						" when that file does not exist, this setting is set to false, or an error occurred parsing that file.");

		if (shouldUsePregenerated && PECore.PREGENERATED_EMC_FILE.canRead() && PregeneratedEMC.tryRead(PECore.PREGENERATED_EMC_FILE, graphMapperValues = Maps.newHashMap()))
		{
			PELogger.logInfo(String.format("Loaded %d values from pregenerated EMC File", graphMapperValues.size()));
		}
		else
		{
			SimpleGraphMapper.setLogFoundExploits(config.getBoolean("logEMCExploits", "general", true,
					"Log known EMC Exploits. This can not and will not find all possible exploits. " +
							"This will only find exploits that result in fixed/custom emc values that the algorithm did not overwrite. " +
							"Exploits that derive from conversions that are unknown to ProjectE will not be found."
			));
			PELogger.logInfo("Starting to collect Mappings...");
			for (IEMCMapper<NormalizedSimpleStack, Double> emcMapper : emcMappers)
			{
				try
				{
					if (config.getBoolean(emcMapper.getName(), "enabledMappers", emcMapper.isAvailable(), emcMapper.getDescription()) && emcMapper.isAvailable())
					{
						DumpToFileCollector.currentGroupName = emcMapper.getName();
						emcMapper.addMappings(mappingCollector, new PrefixConfiguration(config, "mapperConfigurations." + emcMapper.getName()));
						PELogger.logInfo("Collected Mappings from " + emcMapper.getClass().getName());
					}
				} catch (Exception e)
				{
					PELogger.logFatal(String.format("Exception during Mapping Collection from Mapper %s. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!", emcMapper.getClass().getName()));
					e.printStackTrace();
				}
			}
			DumpToFileCollector.currentGroupName = "NSSHelper";
			NormalizedSimpleStack.addMappings(mappingCollector);

			PELogger.logInfo("Mapping Collection finished");
			mappingCollector.finishCollection();

			PELogger.logInfo("Starting to generate Values:");

			config.save();

			graphMapperValues = valueGenerator.generateValues();
			PELogger.logInfo("EMC Values Generated!");

			filterEMCMap(graphMapperValues);
            NormalizedSimpleStack.NSSFake.clearMap();

			if (shouldUsePregenerated) {
				//Should have used pregenerated, but the file was not read => regenerate.
				try {
					PregeneratedEMC.write(PECore.PREGENERATED_EMC_FILE, graphMapperValues);
					PELogger.logInfo("Wrote Pregen-file!");
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		for (Map.Entry<NormalizedSimpleStack, Double> entry: graphMapperValues.entrySet()) {
            if (entry.getKey() instanceof GTNSSItem gtnssItem) {
                Object obj = Item.itemRegistry.getObject(gtnssItem.itemName);
                if (obj != null) {
                    int id = Item.itemRegistry.getIDForObject(obj);
                    emc.put(new GTSimpleStack(id, 1, gtnssItem.damage, gtnssItem.primary, gtnssItem.secondary), entry.getValue());
                }
                else {
                    PELogger.logWarn("Could not add EMC value for %s|%s. Can not get ItemID!", gtnssItem.itemName, gtnssItem.damage);
                }
            }
			else if (entry.getKey() instanceof NormalizedSimpleStack.NSSItem normStackItem)
			{
                Object obj = Item.itemRegistry.getObject(normStackItem.itemName);
				if (obj != null) {
					int id = Item.itemRegistry.getIDForObject(obj);
					emc.put(new SimpleStack(id, 1, normStackItem.damage), entry.getValue());
				}
                else {
					PELogger.logWarn("Could not add EMC value for %s|%s. Can not get ItemID!", normStackItem.itemName, normStackItem.damage);
				}
			}
		}

		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	/**
	 * Remove all entrys from the map, that are not {@link NormalizedSimpleStack.NSSItem}s, have a value <= 0 or WILDCARD_VALUE as metadata.
	 * @param map
	 */
	static void filterEMCMap(Map<NormalizedSimpleStack, Double> map) {
		for(Iterator<Map.Entry<NormalizedSimpleStack, Double>> iter = map.entrySet().iterator(); iter.hasNext();) {
			Map.Entry<NormalizedSimpleStack, Double> entry = iter.next();
			NormalizedSimpleStack normStack = entry.getKey();
			if (normStack instanceof NormalizedSimpleStack.NSSItem normStackItem && entry.getValue() > 0) {
                if (normStackItem.damage != OreDictionary.WILDCARD_VALUE) {
					continue;
				}
			}
			iter.remove();
		}
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
