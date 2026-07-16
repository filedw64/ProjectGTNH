package moze_intel.projecte.integration;

import moze_intel.projecte.emc.mappers.IntegrationMapper;

public class PHNInit {
    public static void init() {
        IntegrationMapper.addMapping("harvestthenether:netherLog", 0, 32);
        IntegrationMapper.addMapping("harvestthenether:quartzingotItem", 0, 1024);
        IntegrationMapper.addMapping("harvestthenether:glowFlower", 0, 256);
        IntegrationMapper.addMapping("harvestthenether:netherGarden", 0, 384);

        IntegrationMapper.addMapping("harvestthenether:bloodleafseedItem", 0, 16);
        IntegrationMapper.addMapping("harvestthenether:fleshrootseedItem", 0, 16);
        IntegrationMapper.addMapping("harvestthenether:marrowberryseedItem", 0, 16);
        IntegrationMapper.addMapping("harvestthenether:glowflowerseedItem", 0, 64);

        IntegrationMapper.addMapping("harvestthenether:bloodleafItem", 0, 64);
        IntegrationMapper.addMapping("harvestthenether:fleshrootItem", 0, 64);
        IntegrationMapper.addMapping("harvestthenether:marrowberryItem", 0, 64);
        IntegrationMapper.addMapping("harvestthenether:ignisfruitItem", 0, 64);
    }
}
