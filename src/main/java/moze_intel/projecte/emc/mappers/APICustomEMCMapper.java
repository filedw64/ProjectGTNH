package moze_intel.projecte.emc.mappers;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.impl.ConversionProxyImpl;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class APICustomEMCMapper implements IEMCMapper<NormalizedSimpleStack, Double> {
	public static APICustomEMCMapper instance = new APICustomEMCMapper();
	public static final int PRIORITY_MIN_VALUE = 0;
	public static final int PRIORITY_MAX_VALUE = 512;
	public static final int PRIORITY_DEFAULT_VALUE = 1;
	private APICustomEMCMapper() {}

	//Need a special Map for Items and Blocks because the ItemID-mapping might change, so we need to store modid:unlocalizedName instead of the NormalizedSimpleStack which only holds itemid and metadata
	Map<String, Map<NormalizedSimpleStack, Double>> customEMCforMod = new HashMap<>();
	Map<String, Map<NormalizedSimpleStack, Double>> customNonItemEMCforMod = new HashMap<>();

	public void registerCustomEMC(ItemStack stack, double emcValue) {
		if (stack == null || stack.getItem() == null) return;
		if (emcValue < 0) emcValue = 0;
		ModContainer activeMod = Loader.instance().activeModContainer();
		String modId = activeMod == null ? null : activeMod.getModId();

		// 用 computeIfAbsent 简化初始化逻辑
		customEMCforMod.computeIfAbsent(modId, k -> new HashMap<>())
			.put(NormalizedSimpleStack.forItem(stack), emcValue);
	}

	public void registerCustomEMC(Object o, double emcValue) {
		NormalizedSimpleStack stack = ConversionProxyImpl.instance.objectToNSS(o);
		if (stack == null) return;
		if (emcValue < 0) emcValue = 0;
		ModContainer activeMod = Loader.instance().activeModContainer();
		String modId = activeMod == null ? null : activeMod.getModId();

		customNonItemEMCforMod.computeIfAbsent(modId, k -> new HashMap<>())
			.put(stack, emcValue);
	}

	@Override
	public String getName() {
		return "APICustomEMCMapper";
	}

	@Override
	public String getDescription() {
		return "Allows other mods to set EMC values using the ProjectEAPI";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		final Map<String, Integer> priorityMap = new HashMap<>();
		Set<String> modIdSet = new HashSet<>();
		modIdSet.addAll(customEMCforMod.keySet());
		modIdSet.addAll(customNonItemEMCforMod.keySet());

		for (String modId: modIdSet) {
			if (modId == null) continue;
			int valueCount = customEMCforMod.getOrDefault(modId, new HashMap<>()).size()
				+ customNonItemEMCforMod.getOrDefault(modId, new HashMap<>()).size();

			priorityMap.put(modId, config.getInt(modId + "priority", "customEMCPriorities", PRIORITY_DEFAULT_VALUE, PRIORITY_MIN_VALUE, PRIORITY_MAX_VALUE, "Priority for Mod with ModId = " + modId + ". Values: " + valueCount));
		}

		if (modIdSet.contains(null)) {
			int valueCount = customEMCforMod.getOrDefault(null, new HashMap<>()).size()
				+ customNonItemEMCforMod.getOrDefault(null, new HashMap<>()).size();
			priorityMap.put(null, config.getInt("modlessCustomEMCPriority", "", PRIORITY_DEFAULT_VALUE, PRIORITY_MIN_VALUE, PRIORITY_MAX_VALUE, "Priority for custom EMC values for which the ModId could not be determined. 0 to disable. Values: " + valueCount));
		}

		List<String> modIds = new ArrayList<>(modIdSet);
		// Integer.compare 替代减法
		modIds.sort((a, b) -> Integer.compare(priorityMap.get(b), priorityMap.get(a)));

		for(String modId : modIds) {
			String modIdOrUnknown = modId == null ? "unknown mod" : modId;

			// 两个 Map 的处理逻辑合并
			processMap(customEMCforMod.get(modId), modId, modIdOrUnknown, mapper, config);
			processMap(customNonItemEMCforMod.get(modId), modId, modIdOrUnknown, mapper, config);
		}
	}

	private void processMap(Map<NormalizedSimpleStack, Double> map, String modId, String modIdOrUnknown, IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		if (map == null) return;
		for (Map.Entry<NormalizedSimpleStack, Double> entry : map.entrySet()) {
			NormalizedSimpleStack normStack = entry.getKey();
			Double value = entry.getValue();
			if (isAllowedToSet(modId, normStack, value, config)) {
				mapper.setValueBefore(normStack, value);
				PELogger.logInfo(String.format("%s setting value for %s to %s", modIdOrUnknown, normStack, value));
			} else {
				PELogger.logInfo(String.format("Disallowed %s to set the value for %s to %s", modIdOrUnknown, normStack, value));
			}
		}
	}

	protected boolean isAllowedToSet(String modId, NormalizedSimpleStack stack, Double value, Configuration config) {
		String itemName;
		if (stack instanceof NormalizedSimpleStack.NSSItem item) {
			itemName = item.itemName;
		} else {
			itemName = "IntermediateFakeItemsUsedInRecipes:";
		}

		String modForItem;
		// 增加冒号检查
		int colonIndex = itemName.indexOf(':');
		if (colonIndex != -1) {
			modForItem = itemName.substring(0, colonIndex);
		} else {
			modForItem = itemName; // 针对没有 ModID 前缀的虚拟物品或流体标签作为降级处理
		}

		String permission = config.getString(modForItem, "permissions." + modId, "both",
			String.format("Allow '%s' to set and or remove values for '%s'. Options: [both, set, remove, none]", modId, modForItem),
			new String[]{"both", "set", "remove", "none"});

		if (permission.equals("both")) {
			return true;
		}
		if (value == 0) {
			return permission.equals("remove");
		} else {
			return permission.equals("set");
		}
	}
}
