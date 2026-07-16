package moze_intel.projecte.emc.mappers;

import com.google.common.collect.ImmutableMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntegrationMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

    public static IntegrationMapper instance = new IntegrationMapper();
    Map<NormalizedSimpleStack, Double> values = new HashMap<>();
    List<IntegrationConversion> conversions = new ArrayList<>();

    @Override
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
        if (values == null) return;
        for (Map.Entry<NormalizedSimpleStack, Double> entry : values.entrySet()) {
            mapper.setValueBefore(entry.getKey(), entry.getValue());
        }
        for (IntegrationConversion intConv : conversions) {
            mapper.addConversion(intConv.amount, intConv.output, intConv.ingredients);
        }
    }

    public static void addOreMapping(String oreName, double value) {
        for (ItemStack stack : ItemHelper.getODItems(oreName)) {
            if (stack == null) continue;
            addMapping(stack, value);
        }
    }

    public static void addMapping(NormalizedSimpleStack nss, double value) {
        if (nss == null) return;
        instance.values.put(nss, value);
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

    public static void addSingleConversion(String in, int inMeta, String out, int outMeta) {
        NormalizedSimpleStack output = NormalizedSimpleStack.getFor(out, outMeta),
            input = NormalizedSimpleStack.getFor(in, inMeta);
        if (output == null || input == null) return;
        instance.conversions.add(new IntegrationConversion(1, output, ImmutableMap.of(input, 1)));
    }

    @Override
    public String getName() {
        return "IntegrationMapper";
    }

    @Override
    public String getDescription() {
        return "Default values for Items in other Mods";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public static class IntegrationConversion
    {
        public final int amount;
        public final NormalizedSimpleStack output;
        public final ImmutableMap<NormalizedSimpleStack, Integer> ingredients;

        private IntegrationConversion(int amount, NormalizedSimpleStack output, ImmutableMap<NormalizedSimpleStack, Integer> ingredients)
        {
            this.amount = amount;
            this.output = output;
            this.ingredients = ingredients;
        }
    }
}
