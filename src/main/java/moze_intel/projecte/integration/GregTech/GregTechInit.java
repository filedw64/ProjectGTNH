package moze_intel.projecte.integration.GregTech;

import moze_intel.projecte.emc.mappers.IntegrationMapper;
import net.minecraftforge.oredict.OreDictionary;

public class GregTechInit {
    public static void init() {
        IntegrationMapper.addMapping("gregtech:gt.metatool.01", OreDictionary.WILDCARD_VALUE, -Double.MAX_VALUE);
    }
}
