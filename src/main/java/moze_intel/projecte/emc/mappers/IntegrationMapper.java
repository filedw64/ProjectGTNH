package moze_intel.projecte.emc.mappers;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import java.util.HashMap;
import java.util.Map;

public class IntegrationMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

    public static IntegrationMapper instance = new IntegrationMapper();
    Map<NormalizedSimpleStack, Double> map = new HashMap<>();

    private IntegrationMapper() {}

    @Override
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
        if (map == null) return;
        for (Map.Entry<NormalizedSimpleStack, Double> entry : map.entrySet()) {
            mapper.setValueBefore(entry.getKey(), entry.getValue());
        }
    }

    public static void addOreMapping(String oreName, double value) {
        for (ItemStack stack : ItemHelper.getODItems(oreName)) {
            if (stack != null)
                addMapping(stack, value);
        }
    }

    public static void addMapping(NormalizedSimpleStack nss, double value) {
        if(nss != null)
            instance.map.put(nss, value);
    }

    public static void addMapping(ItemStack istack, double value) {
        addMapping(NormalizedSimpleStack.getFor(istack), value);
    }

    public static void addMapping(Block block, int meta, double value) {
        addMapping(NormalizedSimpleStack.getFor(block, meta), value);
    }

    public static void addMapping(Item item, int meta, double value) {
        addMapping(NormalizedSimpleStack.getFor(item, meta), value);
    }

    public static void addMapping(String id, int meta, double value) {
        addMapping(NormalizedSimpleStack.getFor(id, meta), value);
    }

    @Override
    public String getName() {
        return "IntegrationMapper";
    }

    @Override
    public String getDescription() {
        return "Default values for Items from other Mods by Integration Classes";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
