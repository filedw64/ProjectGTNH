package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashSet;
import java.util.Set;

public class OreDictionaryMapper implements IEMCMapper<NormalizedSimpleStack, Double> {
	private static final Set<String> BLACKLIST_EXCEPTIONS = new HashSet<>();

	public static void addBlacklistException(String str) {
		BLACKLIST_EXCEPTIONS.add(str);
	}

	static {
		addBlacklistException("crushedPineMaterial");
	}

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		if (!config.getBoolean("blacklistOres", "", true,
			"Set EMC=0 for those whose OD Name starts with `ore`, `rawOre`, `dustPure`, `dustImpure` or `crushed` besides `crushedPineMaterial` and `oreberry`"))
			return;
		//Black-list all ores
		for (String s : OreDictionary.getOreNames()) {
			if (s == null) continue;

			if (!s.startsWith("ore") && !s.startsWith("rawOre") && !s.startsWith("crushed") &&
				!s.startsWith("dustPure") && !s.startsWith("dustImpure")) continue;

			if (s.startsWith("oreberry")) continue;

			//Some exceptions in the black-listing
			if (BLACKLIST_EXCEPTIONS.contains(s)) continue;

			for (ItemStack stack : ItemHelper.getODItems(s)) {
				if (stack == null || stack.getItem() == null) continue;
				mapper.setValueBefore(NormalizedSimpleStack.forItem(stack), 0.0);
				mapper.setValueAfter(NormalizedSimpleStack.forItem(stack), 0.0);
			}
		}
	}

	@Override
	public String getName() {
		return "OreDictionaryMapper";
	}

	@Override
	public String getDescription() {
		return "Blacklist some OreDictionary names from getting an EMC value";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}