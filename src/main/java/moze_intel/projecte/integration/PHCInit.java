package moze_intel.projecte.integration;

import moze_intel.projecte.emc.mappers.IntegrationMapper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class PHCInit {
    public static void init() {

        IntegrationMapper.addMapping("harvestcraft:beeswaxItem", 0, 256);
        IntegrationMapper.addMapping("harvestcraft:honeyItem", 0, 32);
        IntegrationMapper.addMapping("harvestcraft:berrygarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:herbgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:groundgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:desertgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:grassgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:gourdgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:stalkgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:watergarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:textilegarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:mushroomgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:tropicalgarden", 0, 384);
        IntegrationMapper.addMapping("harvestcraft:leafygarden", 0, 384);

        for (ItemStack stack : ItemHelper.getODItems("listAllseed")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("seedItem")) {
                IntegrationMapper.addMapping(stack, 16);
            }
        }

        for (ItemStack stack : ItemHelper.getODItems("listAllfishraw")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                IntegrationMapper.addMapping(stack, 64);
            }
        }

        for (ItemStack stack : ItemHelper.getODItems("listAllmeatraw")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                IntegrationMapper.addMapping(stack, 64);
            }
        }

        for (String od : OreDictionary.getOreNames()) {
            if (od == null || !od.startsWith("crop")) continue;
            for (ItemStack stack : ItemHelper.getODItems(od)) {
                if (stack == null || stack.getItem() == null) continue;
                String id = Item.itemRegistry.getNameForObject(stack.getItem());
                if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                    IntegrationMapper.addMapping(stack, 64);
                }
            }
        }
    }
}
