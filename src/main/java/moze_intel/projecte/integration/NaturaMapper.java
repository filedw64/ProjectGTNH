package moze_intel.projecte.integration;

public class NaturaMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        addMapping("Natura:Thornvines", 0, 8);

        for (int i = 0; i <= 2; i++) {
            addMapping("Natura:Glowshroom", i, 32);
        }

        for (int i = 12; i <= 15; i++) {
            addMapping("Natura:NetherBerryBush", i, 64);
            addMapping("Natura:BerryBush", i, 64);
        }

        addMapping("Natura:Saguaro", 0, 32);

        for (int i = 0; i <= 3; i++) {
            addMapping("Natura:Cloud", i, 8);
            addMapping("Natura:berry", i, 64);
            addMapping("Natura:berry.nether", i, 64);
        }

        addMapping("Natura:barleyFood", 0, 24);
        addMapping("Natura:barleyFood", 3, 64);
        addMapping("Natura:barleyFood", 6, 64);
        addMapping("Natura:barley.seed", 0, 16);
        addMapping("Natura:barley.seed", 1, 16);
        addMapping("Natura:saguaro.fruit", 0, 64);
        addMapping("Natura:Bluebells", 0, 16);
        addMapping("Natura:impmeat", 0, 64);

        for (int i = 1; i <= 3; i++) {
            addMapping("Natura:tree", i, 32);
            addMapping("Natura:Rare Tree", i, 32);
            addMapping("Natura:floraleavesnocolor", i, 1);
            addMapping("Natura:Rare Leaves", i, 1);
            addMapping("Natura:Dark Leaves", i, 1);
        }
        addMapping("Natura:redwood", 1, 32);
        addMapping("Natura:redwood", 2, 32);
        addMapping("Natura:Dark Tree", 1, 32);
        addMapping("Natura:floraleaves", 1, 1);
        addMapping("Natura:floraleaves", 2, 1);
    }
}
