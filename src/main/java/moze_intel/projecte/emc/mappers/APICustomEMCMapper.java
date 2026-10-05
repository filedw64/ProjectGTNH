package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.math.ExactEMC;

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

public class APICustomEMCMapper implements IEMCMapper<NormalizedSimpleStack, ExactEMC> {
	public static APICustomEMCMapper instance = new APICustomEMCMapper();
	public static final int PRIORITY_MIN_VALUE = 0;
	public static final int PRIORITY_MAX_VALUE = 512;
	public static final int PRIORITY_DEFAULT_VALUE = 1;
	private APICustomEMCMapper() {}

	//Need a special Map for Items and Blocks because the ItemID-mapping might change, so we need to store modid:unlocalizedName instead of the NormalizedSimpleStack which only holds itemid and metadata
	Map<String, Map<NormalizedSimpleStack, ExactEMC>> customEMCforMod = new HashMap<>();
	Map<String, Map<NormalizedSimpleStack, ExactEMC>> customNonItemEMCforMod = new HashMap<>();

	public void registerCustomEMC(ItemStack stack, double emcValue) {
		if (stack == null || stack.getItem() == null) return;
		if (emcValue < 0) emcValue = 0;
		ModContainer activeMod = Loader.instance().activeModContainer();
		String modId = activeMod == null ? null : activeMod.getModId();

		// 用 computeIfAbsent 简化初始化逻辑
		customEMCforMod.computeIfAbsent(modId, k -> new HashMap<>())
			.put(NormalizedSimpleStack.forItem(stack), ExactEMC.fromLegacyDouble(emcValue));
	}

	public void registerCustomEMC(Object o, double emcValue) {
		NormalizedSimpleStack stack = ConversionProxyImpl.instance.objectToNSS(o);
		if (stack == null) return;
		if (emcValue < 0) emcValue = 0;
		ModContainer activeMod = Loader.instance().activeModContainer();
		String modId = activeMod == null ? null : activeMod.getModId();

		customNonItemEMCforMod.computeIfAbsent(modId, k -> new HashMap<>()).put(stack, ExactEMC.fromLegacyDouble(emcValue));
	}

    public void registerCustomEMCExact(ItemStack stack, ExactEMC value) {
        if (stack == null || stack.getItem() == null) return;
        moze_intel.projecte.math.ExactEMCCodec.validateBalance(value);
        ModContainer mod = Loader.instance().activeModContainer();
        String id = mod == null ? null : mod.getModId();
        customEMCforMod.computeIfAbsent(id, k -> new HashMap<>()).put(NormalizedSimpleStack.forItem(stack), value);
    }
    public void registerCustomEMCExact(Object object, ExactEMC value) {
        NormalizedSimpleStack key = ConversionProxyImpl.instance.objectToNSS(object);
        if (key == null) return;
        moze_intel.projecte.math.ExactEMCCodec.validateBalance(value);
        ModContainer mod = Loader.instance().activeModContainer();
        String id = mod == null ? null : mod.getModId();
        customNonItemEMCforMod.computeIfAbsent(id, k -> new HashMap<>()).put(key, value);
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
	public void addMappings(IMappingCollector<NormalizedSimpleStack, ExactEMC> mapper, Configuration config) {
		final Map<String, Integer> priorityMap = new HashMap<>();
		Set<String> modIdSet = new HashSet<>();
		modIdSet.addAll(customEMCforMod.keySet());
		modIdSet.addAll(customNonItemEMCforMod.keySet());

		Map<?,?> tmp;
		for (String modId: modIdSet) {
			if (modId == null) continue;
			tmp = customEMCforMod.get(modId);
			int valueCount = tmp == null ? 0 : tmp.size();
			tmp = customNonItemEMCforMod.get(modId);
			valueCount += tmp == null ? 0 : tmp.size(); // 为了不用 contains 就 new 一个 map 这种操作还是太雷霆了点吧！

			priorityMap.put(modId, config.getInt(modId + "priority", "customEMCPriorities", PRIORITY_DEFAULT_VALUE,
				PRIORITY_MIN_VALUE, PRIORITY_MAX_VALUE, "Priority for Mod with ModId = " + modId + ". Values: " + valueCount));
		}

		if (modIdSet.contains(null)) {
			tmp = customEMCforMod.get(null);
			int valueCount = tmp == null ? 0 : tmp.size();
			tmp = customNonItemEMCforMod.get(null);
			valueCount += tmp == null ? 0 : tmp.size();

			priorityMap.put(null, config.getInt("modlessCustomEMCPriority", "", PRIORITY_DEFAULT_VALUE, PRIORITY_MIN_VALUE,
				PRIORITY_MAX_VALUE, "Priority for custom EMC values for which the ModId could not be determined. 0 to disable. Values: " + valueCount));
		}

		List<String> modIds = new ArrayList<>(modIdSet);
		modIds.sort((a, b) -> Integer.compare(priorityMap.get(b), priorityMap.get(a))); // Integer.compare 替代减法

		for (String modId : modIds) {
			String modIdOrUnknown = modId == null ? "unknown mod" : modId;

			// 两个 Map 的处理逻辑合并
			processMap(customEMCforMod.get(modId), modId, modIdOrUnknown, mapper, config);
			processMap(customNonItemEMCforMod.get(modId), modId, modIdOrUnknown, mapper, config);
		}
	}

	private void processMap(Map<NormalizedSimpleStack, ExactEMC> map, String modId, String modIdOrUnknown,
							IMappingCollector<NormalizedSimpleStack, ExactEMC> mapper, Configuration config)
	{
		if (map == null) return;
		for (Map.Entry<NormalizedSimpleStack, ExactEMC> entry : map.entrySet()) {
			NormalizedSimpleStack normStack = entry.getKey();
			ExactEMC value = entry.getValue();
			if (!isAllowedToSet(modId, normStack, value, config)) {
				PELogger.logInfo(String.format("Disallowed %s to set the value for %s to %s", modIdOrUnknown, normStack, value));
				continue;
			}
			mapper.setValueBefore(normStack, value);
			PELogger.logInfo(String.format("%s setting value for %s to %s", modIdOrUnknown, normStack, value));
		}
	}

	protected boolean isAllowedToSet(String modId, NormalizedSimpleStack stack, ExactEMC value, Configuration config) {
		String itemName;
		if (stack instanceof NormalizedSimpleStack.NSSItem item)
			itemName = item.itemName;
		else itemName = "IntermediateFakeItemsUsedInRecipes:";

		String modForItem;
		// 增加冒号检查
		int colonIndex = itemName.indexOf(':');
		if (colonIndex != -1)
			modForItem = itemName.substring(0, colonIndex);
		else modForItem = itemName; // 针对没有 ModID 前缀的虚拟物品或流体标签作为降级处理

		String permission = config.getString(modForItem, "permissions." + modId, "both",
			String.format("Allow '%s' to set and or remove values for '%s'. Options: [both, set, remove, none]", modId, modForItem),
			new String[]{"both", "set", "remove", "none"});

		if (permission.equals("both"))
			return true;

		if (value.isZero())
			return permission.equals("remove");
		return permission.equals("set");
	}
}
