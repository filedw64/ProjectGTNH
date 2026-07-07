package projectgtnh.emc.mappers;

import projectgtnh.config.CustomEMCParser;
import projectgtnh.emc.collector.IMappingCollector;
import projectgtnh.emc.NormalizedSimpleStack;
import projectgtnh.utils.PELogger;
import net.minecraftforge.common.config.Configuration;

import java.util.Map;

public class CustomEMCMapper implements IEMCMapper<NormalizedSimpleStack, Integer> {
	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Integer> mapper, Configuration config) {
		for (Map.Entry<NormalizedSimpleStack,Integer> entry : CustomEMCParser.userValues.entrySet()) {
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
