package moze_intel.projecte.integration;

import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class PHCMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        addMapping("harvestcraft:beeswaxItem", 256);
        addMapping("harvestcraft:honeyItem", 32);
        addMapping("harvestcraft:berrygarden", 384);
        addMapping("harvestcraft:herbgarden", 384);
        addMapping("harvestcraft:groundgarden", 384);
        addMapping("harvestcraft:desertgarden", 384);
        addMapping("harvestcraft:grassgarden", 384);
        addMapping("harvestcraft:gourdgarden", 384);
        addMapping("harvestcraft:stalkgarden", 384);
        addMapping("harvestcraft:watergarden", 384);
        addMapping("harvestcraft:textilegarden", 384);
        addMapping("harvestcraft:mushroomgarden", 384);
        addMapping("harvestcraft:tropicalgarden", 384);
        addMapping("harvestcraft:leafygarden", 384);

        for (ItemStack stack : ItemHelper.getODItems("listAllseed")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("seedItem")) {
                addMapping(stack, 16);
            }
        }

        for (ItemStack stack : ItemHelper.getODItems("listAllfishraw")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                addMapping(stack, 64);
            }
        }

        for (ItemStack stack : ItemHelper.getODItems("listAllmeatraw")) {
            if (stack == null || stack.getItem() == null) continue;
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                addMapping(stack, 64);
            }
        }

        for (String od : OreDictionary.getOreNames()) {
            if (od == null || !od.startsWith("crop")) continue;
            for (ItemStack stack : ItemHelper.getODItems(od)) {
                if (stack == null || stack.getItem() == null) continue;
                String id = Item.itemRegistry.getNameForObject(stack.getItem());
                if (id.startsWith("harvestcraft:") && id.endsWith("Item")) {
                    addMapping(stack, 64);
                }
            }
        }
    }
}