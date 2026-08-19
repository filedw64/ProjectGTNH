package moze_intel.projecte.emc;

import moze_intel.projecte.PECore;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
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
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.PrefixConfiguration;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
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

	public static void map()
	{
		// 在开始映射前清空之前的记录
		clearMaps();

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

		// 废弃了 DoubleCollector 和 DoubleGenerator，直接使用单一实例！
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = new SimpleGraphMapper<>(DoubleArithmetic.INSTANCE);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

		enableNBTprocess = config.getBoolean("enableNBTprocess", "general", true, "Process items that have different NBT tags as different items.");

		PELogger.logInfo("Start to collect Mappings");
		for (IEMCMapper<NormalizedSimpleStack, Double> emcMapper : emcMappers) {
			if (!config.getBoolean(emcMapper.getName(), "enabledMappers", emcMapper.isAvailable(), emcMapper.getDescription()) || !emcMapper.isAvailable())
				continue;
			long start = System.currentTimeMillis();
			try {
				emcMapper.addMappings(mapper, new PrefixConfiguration(config, "mapperConfigurations." + emcMapper.getName()));
			}
			catch (Exception e) {
				PELogger.logFatal("Exception during Mapping Collection from Mapper %s. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!", emcMapper.getClass().getName());
				e.printStackTrace();
				continue;
			}
			PELogger.logInfo("Collected Mappings from %s. (took %.3fs)", emcMapper.getClass().getName(), (System.currentTimeMillis() - start) / 1e3);
		}

		long start = System.currentTimeMillis();
		NormalizedSimpleStack.addMappings(mapper);
		PELogger.logInfo("Collected Mappings from NormalizedSimpleStack. (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		PELogger.logInfo("Mapping Collection finished");
		mapper.finishCollection();

		config.save();
		PELogger.logInfo("Start to generate Values");

		start = System.currentTimeMillis();
		// 将 graphMapperValues 写入 map() 内，生成结束后释放内存
		Map<NormalizedSimpleStack, Double> graphMapperValues = mapper.generateValues();
		filterEMCMap(graphMapperValues);
		PELogger.logInfo("EMC Values Generated! (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		graphMapperValues.forEach((nss, val) -> {
			if (nss instanceof NormalizedSimpleStack.NSSItem nssItem) {
				Object obj = Item.itemRegistry.getObject(nssItem.itemName);
				int id = Item.itemRegistry.getIDForObject(obj);
				if (id == -1) {
					// 还是做一下判断，万一呢
					PELogger.logWarn("Item not found in registry for NSSItem: %s. Skipping...", nssItem.itemName);
					return;
				}
				if (nss instanceof NormalizedSimpleStack.NBTNSSItem nbtnssItem)
					emc.put(new NBTSimpleStack(id, nbtnssItem.damage, nbtnssItem.nbt), val);
				else emc.put(new SimpleStack(id, nssItem.damage), val);
			}
			else if (nss instanceof NormalizedSimpleStack.NSSFluid nssFluid) {
				emc.put(new FluidSimpleStack(nssFluid.fluid.getID()), val);
			}
		});

		NormalizedSimpleStack.clearMap();
		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	/**
	 * Remove all entrys from the map, that are not {@link NormalizedSimpleStack.NSSItem} or {@link NormalizedSimpleStack.NSSFluid},
	 * have a value <= 0 or WILDCARD_VALUE as metadata.
	 */
	static void filterEMCMap(Map<NormalizedSimpleStack, Double> map) {
		// 使用 entrySet() 代替 keySet()，避免重复寻址查询
		// 接管之前 DoubleGenerator 负责的 <= 0 过滤，原地剔除，不产生新的 HashMap
		map.entrySet().removeIf(entry -> {
			if (entry.getValue() <= 0) return true;
			NormalizedSimpleStack nss = entry.getKey();
			if (nss instanceof NormalizedSimpleStack.NSSItem nssItem)
				return nssItem.damage == OreDictionary.WILDCARD_VALUE;
			return !(nss instanceof NormalizedSimpleStack.NSSFluid);
		});
	}

	public static boolean mapContains(SimpleStack key) {
		return emc.containsKey(key);
	}

	public static Double getEmcValue(SimpleStack stack) {
		return emc.get(stack);
	}

	public static void clearMaps() {
		emc.clear();
	}
}
