package moze_intel.projecte.integration;

import moze_intel.projecte.emc.mappers.IntegrationMapper;
import moze_intel.projecte.emc.mappers.OreDictionaryMapper;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class PHCInit {
    public static void init() {
        OreDictionaryMapper.addBlacklistException("dustSalt");
        for (ItemStack stack : ItemHelper.getODItems("listAllseed")) {
            if (stack == null || stack.getItem() == null)
                continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("seedItem")) {
                IntegrationMapper.addMapping(stack, 16);
                IntegrationMapper.addMapping(id.replace("seed",""),0, 64);
            }
        }
    }
}
