package moze_intel.projecte.integration.mappers;

public class PHNMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        addMapping("harvestthenether:netherLog", 32);
        addMapping("harvestthenether:quartzingotItem", 512);
        addMapping("harvestthenether:glowFlower", 256);
        addMapping("harvestthenether:netherGarden", 384);

        addMapping("harvestthenether:bloodleafseedItem", 16);
        addMapping("harvestthenether:fleshrootseedItem", 16);
        addMapping("harvestthenether:marrowberryseedItem", 16);
        addMapping("harvestthenether:glowflowerseedItem", 64);

        addMapping("harvestthenether:bloodleafItem", 64);
        addMapping("harvestthenether:fleshrootItem", 64);
        addMapping("harvestthenether:marrowberryItem", 64);
        addMapping("harvestthenether:ignisfruitItem", 64);
    }
}
