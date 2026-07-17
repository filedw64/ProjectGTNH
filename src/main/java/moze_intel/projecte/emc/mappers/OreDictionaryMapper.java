package moze_intel.projecte.emc.mappers;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.ItemHelper;

public class OreDictionaryMapper extends LazyMapper {

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		this.mapper = mapper;
		if (config.getBoolean("blacklistOres", "", true, "Set EMC=0 for everything that has an OD Name that starts with `ore`")) {
			//Black-list all ores
			for (String s : OreDictionary.getOreNames()) {
				if (s == null || !s.startsWith("ore")) continue;
                for (ItemStack stack : ItemHelper.getODItems(s)) {
                    if (stack == null) continue;
                    mapper.setValueBefore(NormalizedSimpleStack.getFor(stack), 0.0);
                    mapper.setValueAfter(NormalizedSimpleStack.getFor(stack), 0.0);
                }
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

}
