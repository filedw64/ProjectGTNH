package moze_intel.projecte.integration.Forestry;

import moze_intel.projecte.integration.AbstractIntegrationMapper;

public class ForestryMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        for (int i = 1; i <= 28; i++) {
            addMapping("Forestry:logs", i, 32);
            addMapping("Forestry:logsFireproof", i, 32);
        }
    }
}