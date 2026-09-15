package moze_intel.projecte.emc.mappers;

import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Configuration;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;

import java.util.Collections;

public class LazyMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

	IMappingCollector<NormalizedSimpleStack, Double> mapper;

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config) {
		this.mapper = mapper;

		// 直接传入 Block/Item
		addMapping(Blocks.cobblestone, 1);
		addMapping(Blocks.stone, 1);
		addMapping(Blocks.end_stone, 1);
		addMapping(Blocks.netherrack, 1);
		addMapping(Blocks.dirt, 1);
		addMapping(Blocks.dirt, 2, 2);
		addMapping(Blocks.grass, 2);
		addMapping(Blocks.mycelium, 2);
		addMapping(Blocks.leaves, 1);
		addMapping(Blocks.leaves2, 1);
		addMapping(Blocks.sand, 0, 1);
		addMapping(Blocks.sand, 1, 1);
		addMapping(Blocks.snow, 1);
		addMapping(Blocks.ice, 1);
		addMapping(Blocks.deadbush, 1);
		addMapping(Blocks.gravel, 4);
		addMapping(Blocks.cactus, 8);
		addMapping(Blocks.vine, 8);
		addMapping(Blocks.torch, 9);
		addMapping(Blocks.web, 12);
		addMapping(Items.wheat_seeds, 16);
		addMapping(Items.melon, 16);
		addMapping(Items.clay_ball, 16);
		addMapping(Blocks.waterlily, 16);

		for (int i = 0; i <= 8; i++)
			addMapping(Blocks.red_flower, i, 16);

		for (int i = 0; i <= 5; i++)
			addMapping(Blocks.double_plant, i, (i == 2 || i == 3) ? 1 : 32);

		addMapping(Blocks.yellow_flower, 16);
		addMapping(Items.wheat, 24);
		addMapping(Items.nether_wart, 24);
		addMapping(Items.stick, 4);
		addMapping(Blocks.red_mushroom, 32);
		addMapping(Blocks.brown_mushroom, 32);
		addMapping(Items.reeds, 32);
		addMapping(Blocks.soul_sand, 49);
		addMapping(Blocks.obsidian, 64);

		for (int i = 0; i < 16; i++)
			addMapping(Blocks.stained_hardened_clay, i, 64);

		addMapping(Blocks.sponge, 128);
		addMapping(Items.apple, 128);

		addMapping(Items.dye, 3, 128); // Cocoa beans
		addMapping(Blocks.pumpkin, 144);
		addMapping(Items.bone, 144);

		addMapping(Blocks.mossy_cobblestone, 2);

		// Mossy Stone Bricks
		mapper.addConversion(1, NormalizedSimpleStack.forItem(Blocks.stonebrick, 1),
			ImmutableMap.of(NormalizedSimpleStack.forItem(Blocks.stonebrick), 2));
		addMapping(Blocks.stonebrick, 2, 1);
		addMapping(Blocks.stonebrick, 3, 1);

		addMapping(Items.saddle, 192);
		addMapping(Items.record_11, 2048);
		addMapping(Items.record_13, 2048);
		addMapping(Items.record_blocks, 2048);
		addMapping(Items.record_cat, 2048);
		addMapping(Items.record_chirp, 2048);
		addMapping(Items.record_far, 2048);
		addMapping(Items.record_mall, 2048);
		addMapping(Items.record_mellohi, 2048);
		addMapping(Items.record_stal, 2048);
		addMapping(Items.record_strad, 2048);
		addMapping(Items.record_wait, 2048);
		addMapping(Items.record_ward, 2048);
		addMapping(Items.string, 12);

		for (int i = 1; i < 16; i++) {
			mapper.setValueFromConversion(1, NormalizedSimpleStack.forItem(Blocks.wool, i),
				Collections.singletonList(NormalizedSimpleStack.forItem(Blocks.wool)));
		}

		addMapping(Items.rotten_flesh, 32);
		addMapping(Items.slime_ball, 32);
		addMapping(Items.egg, 32);
		addMapping(Items.feather, 48);
		addMapping(Items.leather, 64);
		addMapping(Items.spider_eye, 128);
		addMapping(Items.gunpowder, 192);
		addMapping(Items.ender_pearl, 1024);
		addMapping(Items.blaze_rod, 1536);
		addMapping(Items.ghast_tear, 4096);
		addMapping(Blocks.dragon_egg, 262144);
		addMapping(Items.porkchop, 64);
		addMapping(Items.beef, 64);
		addMapping(Items.chicken, 64);

		for (int i = 0; i < 4; i++)
			addMapping(Items.fish, i, 64);

		addMapping(Items.carrot, 64);
		addMapping(Items.potato, 64);
		addMapping(Items.poisonous_potato, 64);
		addMapping(Items.iron_ingot, 256);
		addMapping(Items.gold_ingot, 2048);
		addMapping(Items.diamond, 8192);
		addMapping(Items.flint, 4);
		addMapping(Items.coal, 128);
		addMapping(Items.redstone, 64);
		addMapping(Items.glowstone_dust, 384);
		addMapping(Items.quartz, 256);

		// Lapis Lazuli
		addMapping(Items.dye, 4, 864);

		// ink sac
		addMapping(Items.dye, 0, 16);

		addMapping(Items.enchanted_book, 2048);
		addMapping(Items.emerald, 16384);

		addMapping(Items.nether_star, 139264);
		addMapping(Items.iron_horse_armor, 2048);
		addMapping(Items.golden_horse_armor, 16384);
		addMapping(Items.diamond_horse_armor, 40960);
		addMapping(Blocks.tallgrass, 1, 1);
		addMapping(Blocks.tallgrass, 2, 1);
		addMapping(Blocks.packed_ice, 4);
		addMapping(Items.snowball, 1);
		addMapping(Items.filled_map, 1472);

		addMapping(Items.skull, 0, 256);
		addMapping(Items.skull, 2, 256);
		addMapping(Items.skull, 4, 256);

		addMapping("appliedenergistics2:item.ItemMultiMaterial", 1, 256);
	}

	protected void addMapping(Block block, double value) {
		this.mapper.setValueBefore(NormalizedSimpleStack.forItem(block), value);
	}

	protected void addMapping(Block block, int meta, double value) {
		this.mapper.setValueBefore(NormalizedSimpleStack.forItem(block, meta), value);
	}

	protected void addMapping(Item item, double value) {
		this.mapper.setValueBefore(NormalizedSimpleStack.forItem(item), value);
	}

	protected void addMapping(Item item, int meta, double value) {
		this.mapper.setValueBefore(NormalizedSimpleStack.forItem(item, meta), value);
	}

	protected void addMapping(String unlocalName, int meta, double value) {
		Object obj = Item.itemRegistry.getObject(unlocalName);
		if (obj instanceof Item item)
			addMapping(item, meta, value);
		else if (obj instanceof Block block)
			addMapping(block, meta, value);
	}

	@Override
	public String getName() {
		return "LazyMapper";
	}

	@Override
	public String getDescription() {
		return "Default values for Items";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}
}
