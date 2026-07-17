package moze_intel.projecte.integration;

import moze_intel.projecte.emc.mappers.IntegrationMapper;

public class NaturaInit {
    public static void init() {
        IntegrationMapper.addMapping("Natura:Thornvines", 0, 8);

        for (int i = 0; i <= 2; i++) {
            IntegrationMapper.addMapping("Natura:Glowshroom", i, 32);
        }

        for (int i = 12; i <= 15; i++) {
            IntegrationMapper.addMapping("Natura:NetherBerryBush", i, 64);
            IntegrationMapper.addMapping("Natura:BerryBush", i, 64);
        }

        IntegrationMapper.addMapping("Natura:Saguaro", 0, 32);

        for (int i = 0; i <= 3; i++) {
            IntegrationMapper.addMapping("Natura:Cloud", i, 8);
            IntegrationMapper.addMapping("Natura:berry", i, 64);
            IntegrationMapper.addMapping("Natura:berry.nether", i, 64);
        }

        IntegrationMapper.addMapping("Natura:barleyFood", 0, 24);
        IntegrationMapper.addMapping("Natura:barleyFood", 3, 64);
        IntegrationMapper.addMapping("Natura:barleyFood", 6, 64);
        IntegrationMapper.addMapping("Natura:barley.seed", 0, 16);
        IntegrationMapper.addMapping("Natura:barley.seed", 1, 16);
        IntegrationMapper.addMapping("Natura:saguaro.fruit", 0, 64);
        IntegrationMapper.addMapping("Natura:Bluebells", 0, 16);
        IntegrationMapper.addMapping("Natura:impmeat", 0, 64);

        for (int i = 1; i <= 3; i++) {
            IntegrationMapper.addMapping("Natura:tree", i, 32);
            IntegrationMapper.addMapping("Natura:Rare Tree", i, 32);
            IntegrationMapper.addMapping("Natura:floraleavesnocolor", i, 1);
            IntegrationMapper.addMapping("Natura:Rare Leaves", i, 1);
            IntegrationMapper.addMapping("Natura:Dark Leaves", i, 1);
        }
        IntegrationMapper.addMapping("Natura:redwood", 1, 32);
        IntegrationMapper.addMapping("Natura:redwood", 2, 32);
        IntegrationMapper.addMapping("Natura:Dark Tree", 1, 32);
        IntegrationMapper.addMapping("Natura:floraleaves", 1, 1);
        IntegrationMapper.addMapping("Natura:floraleaves", 2, 1);
    }
}
