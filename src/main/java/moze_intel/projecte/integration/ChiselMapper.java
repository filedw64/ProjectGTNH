package moze_intel.projecte.integration;

import com.cricketcraft.chisel.api.carving.CarvingUtils;
import com.cricketcraft.chisel.api.carving.ICarvingGroup;
import com.cricketcraft.chisel.api.carving.ICarvingRegistry;
import com.cricketcraft.chisel.api.carving.ICarvingVariation;
import cpw.mods.fml.common.Loader;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//Thanks to bdew for a first implementation of this: https://github.com/bdew/ProjectE/blob/f1b08624ff47c6cc716576701024cdb38ff3d297/src/main/java/moze_intel/projecte/emc/ChiselMapper.java
public class ChiselMapper extends AbstractIntegrationMapper {
	public final static String[] chiselBlockNames = new String[]{"marble", "limestone", "andesite", "granite", "diorite"};

	public static void init() {
		PELogger.logTrace("Succeed to get Chisel Registry: %s", CarvingUtils.getChiselRegistry());
	}

	@Override
	protected void doAddMappings() {
        if(!Loader.isModLoaded("chisel")) return;
		ICarvingRegistry carvingRegistry = CarvingUtils.getChiselRegistry();
		if (carvingRegistry == null) return;
		for (String name: chiselBlockNames) {
			Block block = Block.getBlockFromName("chisel:" + name);
			if (block != null) {
				mapper.setValueBefore(NormalizedSimpleStack.getFor(block), 1.0);
			}
		}

		for (String name : carvingRegistry.getSortedGroupNames()) {
			handleCarvingGroup(carvingRegistry.getGroup(name));
		}
	}

	private void handleCarvingGroup(ICarvingGroup group) {
		List<NormalizedSimpleStack> stacks = new ArrayList<>();
		for (ICarvingVariation v : group.getVariations()) {
			stacks.add(NormalizedSimpleStack.getFor(v.getBlock(), v.getBlockMeta()));
		}
		if (group.getOreName() != null) {
			for (ItemStack ore : OreDictionary.getOres(group.getOreName())) {
				stacks.add(NormalizedSimpleStack.getFor(ore));
			}
		}
		for (int i = 1; i < stacks.size(); i++) {
			mapper.addConversion(1, stacks.get(0), Collections.singletonList(stacks.get(i)));
			mapper.addConversion(1, stacks.get(i), Collections.singletonList(stacks.get(0)));
		}
	}
}