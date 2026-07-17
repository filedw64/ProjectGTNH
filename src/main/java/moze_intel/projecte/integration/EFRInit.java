package moze_intel.projecte.integration;

import moze_intel.projecte.emc.mappers.IntegrationMapper;

public class EFRInit {

    public static void init() {

        IntegrationMapper.addMapping("etfuturum:honeycomb", 0, 16);
        IntegrationMapper.addMapping("etfuturum:bamboo", 0, 32);
        IntegrationMapper.addMapping("etfuturum:netherite_scrap", 0, 12288);
        IntegrationMapper.addMapping("etfuturum:stone", 1, 16);
        IntegrationMapper.addMapping("etfuturum:stone", 3, 16);
        IntegrationMapper.addMapping("etfuturum:stone", 5, 16);
        IntegrationMapper.addMapping("etfuturum:sweet_berries", 0, 16);
        IntegrationMapper.addMapping("etfuturum:amethyst_shard", 0, 32);
        IntegrationMapper.addMapping("etfuturum:honey_bottle", 0, 48);
        IntegrationMapper.addMapping("etfuturum:rabbit_hide", 0, 16);
        IntegrationMapper.addMapping("etfuturum:pigstep_record", 0, 8192);
        IntegrationMapper.addMapping("etfuturum:otherside_record", 0, 6144);
        IntegrationMapper.addMapping("etfuturum:beetroot_seeds", 0, 16);
        IntegrationMapper.addMapping("etfuturum:beetroot", 0, 64);
        IntegrationMapper.addMapping("etfuturum:mutton_raw", 0, 64);
        IntegrationMapper.addMapping("etfuturum:rabbit_raw", 0, 64);
        IntegrationMapper.addMapping("etfuturum:rabbit_foot", 0, 128);
        IntegrationMapper.addMapping("etfuturum:chorus_fruit", 0, 192);
        IntegrationMapper.addMapping("etfuturum:prismarine_shard", 0, 256);
        IntegrationMapper.addMapping("etfuturum:prismarine_crystals", 0, 512);
        IntegrationMapper.addMapping("etfuturum:shulker_shell", 0, 2048);
        IntegrationMapper.addMapping("etfuturum:dragon_breath", 0, 2);
        IntegrationMapper.addMapping("etfuturum:glow_lichen", 0, 8);
        IntegrationMapper.addMapping("etfuturum:glow_berries_item", 0, 16);
        IntegrationMapper.addMapping("etfuturum:wither_rose", 0, 16);

        for (int i = 0; i < 16; i++) {
            IntegrationMapper.addMapping("etfuturum:concrete", i, 4);
        }

        IntegrationMapper.addMapping("etfuturum:magma", 0, 128);
        IntegrationMapper.addMapping("etfuturum:soul_torch", 0, 21);
        IntegrationMapper.addMapping("etfuturum:lily_of_the_valley", 0, 16);
        IntegrationMapper.addMapping("etfuturum:mud", 0, 1);
        IntegrationMapper.addMapping("etfuturum:basalt", 0, 4);
        IntegrationMapper.addMapping("etfuturum:tuff", 0, 4);
        IntegrationMapper.addMapping("etfuturum:pink_petals", 0, 4);
        IntegrationMapper.addMapping("etfuturum:crying_obsidian", 0, 768);
        IntegrationMapper.addMapping("etfuturum:chorus_plant", 0, 64);
        IntegrationMapper.addMapping("etfuturum:chorus_flower", 0, 96);
        IntegrationMapper.addMapping("etfuturum:calcite", 0, 32);
        IntegrationMapper.addMapping("etfuturum:grass_path", 0, 1);
        IntegrationMapper.addMapping("etfuturum:sponge", 1, 128);
        IntegrationMapper.addMapping("etfuturum:red_netherbrick", 1, 4);
        IntegrationMapper.addMapping("etfuturum:cornflower", 0, 16);

        String str = "etfuturum:copper_block";
        for (int i = 1; i <= 3; i++) {
            IntegrationMapper.addSingleConversion(str, 0, str, i);
        }

        IntegrationMapper.addSingleConversion("etfuturum:copper_door", 0, "etfuturum:exposed_copper_door", 0);
        IntegrationMapper.addSingleConversion("etfuturum:copper_door", 0, "etfuturum:weathered_copper_door", 0);
        IntegrationMapper.addSingleConversion("etfuturum:copper_door", 0, "etfuturum:oxidized_copper_door", 0);

        IntegrationMapper.addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:exposed_copper_trapdoor", 0);
        IntegrationMapper.addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:weathered_copper_trapdoor", 0);
        IntegrationMapper.addSingleConversion("etfuturum:copper_trapdoor", 0, "etfuturum:oxidized_copper_trapdoor", 0);
    }
}
