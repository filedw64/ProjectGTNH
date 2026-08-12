package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.integration.mappers.AvaritiaMapper;
import moze_intel.projecte.integration.mappers.ChiselMapper;
import moze_intel.projecte.integration.mappers.EFRMapper;
import moze_intel.projecte.integration.mappers.ForestryMapper;
import moze_intel.projecte.integration.mappers.GTMapper;
import moze_intel.projecte.integration.Integration;
import moze_intel.projecte.integration.mappers.NaturaMapper;
import moze_intel.projecte.integration.mappers.PHCMapper;
import moze_intel.projecte.integration.mappers.PHNMapper;
import net.minecraftforge.common.config.Configuration;

public class IntegrationMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		if (Integration.PHC)
			new PHCMapper().addMappings(mapper);
		if (Integration.PHN)
			new PHNMapper().addMappings(mapper);
		if (Integration.EFR)
			new EFRMapper().addMappings(mapper);
		if (Integration.natura)
			new NaturaMapper().addMappings(mapper);
		if (Integration.forestry)
			new ForestryMapper().addMappings(mapper);
		if (Integration.chisel)
			new ChiselMapper().addMappings(mapper);
		if (Integration.avaritia)
			new AvaritiaMapper().addMappings(mapper);
		if (Integration.gregtech)
			new GTMapper().addMappings(mapper);
	}

	@Override
	public String getName() {
		return "IntegrationMapper";
	}

	@Override
	public String getDescription() {
		return "Add default values and conversions for Items by integration classes";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}
