package moze_intel.projecte.integration.mappers;

public class EFRMapper extends AbstractIntegrationMapper {
    @Override
    protected void doAddMappings() {
        addMapping("etfuturum:honeycomb", 0, 16);
        addMapping("etfuturum:bamboo", 0, 32);
        addMapping("etfuturum:netherite_scrap", 0, 12288);
        addMapping("etfuturum:stone", 1, 16);
        addMapping("etfuturum:stone", 3, 16);
        addMapping("etfuturum:stone", 5, 16);
        addMapping("etfuturum:sweet_berries", 0, 16);
        addMapping("etfuturum:amethyst_shard", 0, 32);
        addMapping("etfuturum:honey_bottle", 0, 48);
        addMapping("etfuturum:rabbit_hide", 0, 16);
        addMapping("etfuturum:pigstep_record", 0, 8192);
        addMapping("etfuturum:otherside_record", 0, 6144);
        addMapping("etfuturum:beetroot_seeds", 0, 16);
        addMapping("etfuturum:beetroot", 0, 64);
        addMapping("etfuturum:mutton_raw", 0, 64);
        addMapping("etfuturum:rabbit_raw", 0, 64);
        addMapping("etfuturum:rabbit_foot", 0, 128);
        addMapping("etfuturum:chorus_fruit", 0, 192);
        addMapping("etfuturum:prismarine_shard", 0, 256);
        addMapping("etfuturum:prismarine_crystals", 0, 512);
        addMapping("etfuturum:shulker_shell", 0, 2048);
        addMapping("etfuturum:dragon_breath", 0, 2);
        addMapping("etfuturum:glow_lichen", 0, 8);
        addMapping("etfuturum:glow_berries_item", 0, 16);
        addMapping("etfuturum:wither_rose", 0, 16);

        for (int i = 0; i < 16; i++) {
            addMapping("etfuturum:concrete", i, 4);
        }

        addMapping("etfuturum:magma", 0, 128);
        addMapping("etfuturum:soul_torch", 0, 21);
        addMapping("etfuturum:lily_of_the_valley", 0, 16);
        addMapping("etfuturum:mud", 0, 1);
        addMapping("etfuturum:basalt", 0, 4);
        addMapping("etfuturum:tuff", 0, 4);
        addMapping("etfuturum:pink_petals", 0, 4);
        addMapping("etfuturum:crying_obsidian", 0, 768);
        addMapping("etfuturum:chorus_plant", 0, 64);
        addMapping("etfuturum:chorus_flower", 0, 96);
        addMapping("etfuturum:calcite", 0, 32);
        addMapping("etfuturum:grass_path", 0, 1);
        addMapping("etfuturum:sponge", 1, 128);
        addMapping("etfuturum:red_netherbrick", 1, 4);
        addMapping("etfuturum:cornflower", 0, 16);

        String str = "etfuturum:copper_block";
        for (int i = 1; i <= 3; i++) {
            addSingleConversion(str, 0, str, i);
        }

        addSingleConversion("etfuturum:copper_door", 0, "etfuturum:exposed_copper_door", 0);
        addSingleConversion("etfuturum:copper_door", 0, "etfuturum:weathered_copper_door", 0);
        addSingleConversion("etfuturum:copper_door", 0, "etfuturum:oxidized_copper_door", 0);

        addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:exposed_copper_trapdoor", 0);
        addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:weathered_copper_trapdoor", 0);
        addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:oxidized_copper_trapdoor", 0);
    }
}
