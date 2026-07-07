package projecte.emc;

import com.google.common.collect.Maps;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.math.Fraction;
import projecte.PECore;
import projecte.api.event.EMCRemapEvent;
import projecte.emc.arithmetics.HiddenFractionArithmetic;
import projecte.emc.arithmetics.IValueArithmetic;
import projecte.emc.collector.DumpToFileCollector;
import projecte.emc.collector.IExtendedMappingCollector;
import projecte.emc.collector.IntToFractionCollector;
import projecte.emc.generators.FractionToIntGenerator;
import projecte.emc.generators.IValueGenerator;
import projecte.emc.mappers.APICustomConversionMapper;
import projecte.emc.mappers.APICustomEMCMapper;
import projecte.emc.mappers.Chisel2Mapper;
import projecte.emc.mappers.CraftingMapper;
import projecte.emc.mappers.CustomEMCMapper;
import projecte.emc.mappers.IEMCMapper;
import projecte.emc.mappers.LazyMapper;
import projecte.emc.mappers.OreDictionaryMapper;
import projecte.emc.mappers.SmeltingMapper;
import projecte.emc.mappers.customConversions.CustomConversionMapper;
import projecte.emc.pregenerated.PregeneratedEMC;
import projecte.playerData.Transmutation;
import projecte.utils.PELogger;
import projecte.utils.PrefixConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EMCMapper
{
	public static Map<SimpleStack, Integer> emc = new LinkedHashMap<>();
	public static Map<NormalizedSimpleStack, Integer> graphMapperValues;

	public static void map()
	{
		List<IEMCMapper<NormalizedSimpleStack, Integer>> emcMappers = Arrays.asList(
				new OreDictionaryMapper(),
				new LazyMapper(),
				new Chisel2Mapper(),
				APICustomEMCMapper.instance,
				new CustomConversionMapper(),
				new CustomEMCMapper(),
				new CraftingMapper(),
				new projecte.emc.mappers.FluidMapper(),
				new SmeltingMapper(),
				new APICustomConversionMapper()
		);
		SimpleGraphMapper<NormalizedSimpleStack, Fraction, IValueArithmetic<Fraction>> mapper = new SimpleGraphMapper(new HiddenFractionArithmetic());
		IValueGenerator<NormalizedSimpleStack, Integer> valueGenerator = new FractionToIntGenerator(mapper);
		IExtendedMappingCollector<NormalizedSimpleStack, Integer, IValueArithmetic<Fraction>> mappingCollector = new IntToFractionCollector(mapper);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

		if (config.getBoolean("dumpEverythingToFile", "general", false,"Want to take a look at the internals of EMC Calculation? Enable this to write all the conversions and setValue-Commands to config/ProjectGTNH/mappingdump.json")) {
			mappingCollector = new DumpToFileCollector(new File(PECore.CONFIG_DIR, "mappingdump.json"), mappingCollector);
		}

		boolean shouldUsePregenerated = config.getBoolean("pregenerate", "general", false, "When the next EMC mapping occurs write the results to config/ProjectGTNH/pregenerated_emc.json and only ever run the mapping again" +
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
							"Exploits that derive from conversions that are unknown to ProjectGTNH will not be found."
			));

			PELogger.logInfo("Starting to collect Mappings...");
			for (IEMCMapper<NormalizedSimpleStack, Integer> emcMapper : emcMappers)
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
			PELogger.logInfo("Generated Values...");

			filterEMCMap(graphMapperValues);

			if (shouldUsePregenerated) {
				//Should have used pregenerated, but the file was not read => regenerate.
				try
				{
					PregeneratedEMC.write(PECore.PREGENERATED_EMC_FILE, graphMapperValues);
					PELogger.logInfo("Wrote Pregen-file!");
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}


		for (Map.Entry<NormalizedSimpleStack, Integer> entry: graphMapperValues.entrySet()) {
			if (entry.getKey() instanceof NormalizedSimpleStack.NSSItem)
			{
				NormalizedSimpleStack.NSSItem normStackItem = (NormalizedSimpleStack.NSSItem)entry.getKey();
				Object obj = Item.itemRegistry.getObject(normStackItem.itemName);
				if (obj != null)
				{
					int id = Item.itemRegistry.getIDForObject(obj);
					emc.put(new SimpleStack(id, 1, normStackItem.damage), entry.getValue());
				} else {
					PELogger.logWarn("Could not add EMC value for %s|%s. Can not get ItemID!", normStackItem.itemName, normStackItem.damage);
				}
			}
		}

		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	/**
	 * Remove all entrys from the map, that are not {@link projecte.emc.NormalizedSimpleStack.NSSItem}s, have a value < 0 or WILDCARD_VALUE as metadata.
	 * @param map
	 */
	static void filterEMCMap(Map<NormalizedSimpleStack, Integer> map) {
		for(Iterator<Map.Entry<NormalizedSimpleStack, Integer>> iter = graphMapperValues.entrySet().iterator(); iter.hasNext();) {
			Map.Entry<NormalizedSimpleStack, Integer> entry = iter.next();
			NormalizedSimpleStack normStack = entry.getKey();
			if (normStack instanceof NormalizedSimpleStack.NSSItem && entry.getValue() > 0) {
				NormalizedSimpleStack.NSSItem normStackItem = (NormalizedSimpleStack.NSSItem)normStack;
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

	public static int getEmcValue(SimpleStack stack)
	{
		SimpleStack copy = stack.copy();
		copy.qnty = 1;

		return emc.get(copy);
	}

	public static void clearMaps() {
		emc.clear();
	}
}
