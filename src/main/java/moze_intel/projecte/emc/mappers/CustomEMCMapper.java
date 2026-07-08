package moze_intel.projecte.emc.mappers;

import net.minecraftforge.common.config.Configuration;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.PELogger;

import java.util.Map;

public class CustomEMCMapper implements IEMCMapper<NormalizedSimpleStack, Double> {
	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		for (Map.Entry<NormalizedSimpleStack,Double> entry : CustomEMCParser.userValues.entrySet()) {
			PELogger.logInfo("Adding custom EMC value for " + entry.getKey() + ": " + entry.getValue());
			mapper.setValueBefore(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public String getName() {
		return "CustomEMCMapper";
	}

	@Override
	public String getDescription() {
		return "Uses the `custom_emc.cfg` File to add EMC values.";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}
