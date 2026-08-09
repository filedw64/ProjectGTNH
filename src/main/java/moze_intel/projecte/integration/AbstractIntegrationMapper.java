package moze_intel.projecte.integration;

import com.google.common.collect.ImmutableMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;

public abstract class AbstractIntegrationMapper {

    protected IMappingCollector<NormalizedSimpleStack, Double> mapper;
    public final void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper) {
        this.mapper = mapper;
		try {
			doAddMappings(); // 避免一个 IntegrationMapper 出错，后续的联动都丢失
		}
		catch (Exception e) {
			PELogger.logError("Exception when processing %s: %s", this.getClass().getName(), e);
		}
    }

    protected abstract void doAddMappings();

    protected void addMapping(NormalizedSimpleStack nss, double value) {
        if (nss == null) return;
        mapper.setValueBefore(nss, value);
    }

    protected void addMapping(String id, int meta, double value) {
        addMapping(NormalizedSimpleStack.forItem(id, meta), value);
    }

    protected void addMapping(String id, double value) {
        addMapping(id, 0, value);
    }

    protected void addMapping(ItemStack istack, double value) {
        addMapping(NormalizedSimpleStack.forItem(istack), value);
    }

    protected void addSingleConversion(String in, int inMeta, int inNum, String out, int outMeta, int outNum) {
        NormalizedSimpleStack output = NormalizedSimpleStack.forItem(out, outMeta),
            input = NormalizedSimpleStack.forItem(in, inMeta);
        if (output == null || input == null) return;
        mapper.addConversion(outNum, output, ImmutableMap.of(input, inNum));
    }

    protected void addSingleConversion(String in, int inMeta, String out, int outMeta) {
        addSingleConversion(in, inMeta, 1, out, outMeta, 1);
    }
}
