package moze_intel.projecte.config;

import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.Item;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProjectEConfig {
	public static boolean showUnlocalizedNames;
	public static boolean showODNames;
	public static boolean showEMCTooltip;
	public static boolean showStatTooltip;
	public static boolean showPedestalTooltip;
	public static boolean showPedestalTooltipInGUI;

	public static boolean enableTimeWatch;
	public static boolean enableArcaneTablet;
	public static boolean enableExpansionStar;
	public static boolean enableInfiniteFuel;
	public static boolean enableInfiniteSteak;
	public static boolean enableBuildersWand;
	public static boolean enableRodOfHarmony;
	public static int buildersWandMaxBlocks;
	public static int buildersWandBlocksPerTick;
	public static int rodOfHarmonyCostPerBlock;
	public static int rodOfHarmonyCostPerWallBlock;
	public static int rodOfHarmonyMaxDistance;
	public static int rodOfHarmonySafeRadius;
	public static boolean enableAEGU;
	public static boolean enableStoneAlwaysEdible;
	public static boolean bowLightningEnabled;

	public static boolean craftableTome;
	public static boolean altCraftingMat;
	public static boolean useOldDamage;
	public static int archangelPedCooldown;
	public static int bodyPedCooldown;
	public static int evertidePedCooldown;
	public static int harvestPedCooldown;
	public static int ignitePedCooldown;
	public static int lifePedCooldown;
	public static int repairPedCooldown;
	public static int swrgPedCooldown;
	public static int soulPedCooldown;
	public static int volcanitePedCooldown;
	public static int zeroPedCooldown;

	public static int timePedBonus;
	public static float timePedMobSlowness;
	public static boolean interdictionMode;
	public static boolean pickaxeAoeVeinMining;
	public static boolean harvBandGrass;
	public static boolean useLootBalls;
	public static boolean pulsatingOverlay;
	public static boolean unsafeKeyBinds;
	public static boolean offensiveAbilities;
	public static float katarDeathAura;
	public static int projectileCooldown;
	public static boolean disableAllRadiusMining;
	public static int gemChestCooldown;

	public static boolean enablePickBlockTransmute;
	public static int pickBlockMaxStacks;
	public static boolean pickBlockCreativeFree;

	public static String sciFormat;

	public static boolean ignitionRingIgniteBlocks;
	public static boolean zeroRingPlaceSnow;

	// GTNH Integration Config
	public static boolean questMode;
	public static boolean enableNutritionIntegration;

	// emc 热重载
	public static boolean emcAutoReload;
	public static boolean verifyIncrementalEMC;

	// nbt 白名单 与 动态 nbt emc 计算
	public static String[] nbtWhitelistConfig;
	public static String[] dynamicEmcNbtConfig;

	// 消除 Item.itemRegistry.getNameForObject() 开销
	public static Map<Item, List<String>> nbtDistinctlist = new HashMap<>();
	public static Map<String, Double> dynamicEmcNbt = new HashMap<>();

	public static void init(File configFile) {

		Configuration config = new Configuration(configFile);
		try {
			config.load();

			// Load GTNH Integration config
			questMode = config.getBoolean("questMode", "integration", true, "Whether to add quest when BetterQuesting is loaded");
			enableNutritionIntegration = config.getBoolean("enableNutritionIntegration", "nutrition", true, "If true and the Nutrition mod is loaded, the Body and Life Stones will also restore Nutrition nutrient values when they restore hunger.");

			emcAutoReload = config.getBoolean("autoReloadEMC", "emc", true, "Watch config/ProjectE/custom_emc.cfg for external changes and hot reload the EMC values incrementally.");
			verifyIncrementalEMC = config.getBoolean("verifyIncrementalEMC", "emc", false, "Verify incremental EMC with a full remap at most once per 60 seconds. Debug only: runs synchronously on the server thread and may stall ticks. Does not save mapping.cfg. Verification failure rolls back the update.");

			showUnlocalizedNames = config.getBoolean("unToolTips", "misc", false, "Show item unlocalized names in tooltips (useful for custom EMC registration)");
			showODNames = config.getBoolean("odToolTips", "misc", false, "Show item Ore Dictionary names in tooltips (useful for custom EMC registration)");
			showEMCTooltip = config.getBoolean("emcToolTips", "misc", true, "Show the EMC value as a tooltip on items and blocks");
			showStatTooltip = config.getBoolean("statToolTips", "misc", true, "Show stats as tooltips for various ProjectE blocks");
			showPedestalTooltip = config.getBoolean("pedestalToolTips", "misc", true, "Show DM pedestal functions in item tooltips");
			showPedestalTooltipInGUI = config.getBoolean("pedestalToolTipsInGUI", "misc", false, "Show pedestal function tooltips only in pedestal GUI");
			useLootBalls = config.getBoolean("useLootBalls", "misc", false, "Make loot balls for drops. Disabling this may potentially cause bad performance when large amounts of loot are spawned!");
			pulsatingOverlay = config.getBoolean("pulsatingOverlay", "misc", false, "The Philosopher's Stone overlay softly pulsates");
			unsafeKeyBinds = config.getBoolean("unsafeKeyBinds", "misc", false, "False requires your hand be empty for Gem Armor Offensive Abilities to be readied or triggered");
			projectileCooldown = config.getInt("projectileCooldown", "misc", 0, 0, Integer.MAX_VALUE, "A cooldown (in ticks) for firing projectiles");
			gemChestCooldown = config.getInt("gemChestCooldown", "misc", 0, 0, Integer.MAX_VALUE, "A cooldown (in ticks) for Gem Chestplate explosion");
			enablePickBlockTransmute = config.getBoolean("enablePickBlockTransmute", "misc", true, "Consume EMC to get the targeted block directly when pick blocking it (middle click)");
			pickBlockMaxStacks = config.getInt("pickBlockMaxStacks", "misc", 1, 1, 64, "Maximum number of stacks given by one pick block transmutation");
			pickBlockCreativeFree = config.getBoolean("pickBlockCreativeFree", "misc", true, "If true pick block transmutation costs no EMC in creative mode");
			sciFormat = config.getString("sciFormat", "misc", "e", "The string used as a separator for scientific notation in tooltips");

			enableTimeWatch = config.getBoolean("enableTimeWatch", "items", true, "Enable Watch of Flowing Time");
			enableArcaneTablet = config.getBoolean("enableArcaneTablet", "items", true, "Enable Arcane Transmutation Tablet");
			enableExpansionStar = config.getBoolean("enableExpansionStar", "items", true, "Enable Stars backported from Project Expansion");
			enableInfiniteFuel = config.getBoolean("enableInfiniteFuel", "items", true, "Enable Infinite Fuel backported from Project Expansion");
			enableInfiniteSteak = config.getBoolean("enableInfiniteSteak", "items", true, "Enable Infinite Steak backported from Project Expansion");
			enableBuildersWand = config.getBoolean("enableBuildersWand", "items", true, "Enable EMC Builders Wand");
			enableRodOfHarmony = config.getBoolean("enableRodOfHarmony", "items", true, "Enable Rod of Harmony");
			buildersWandMaxBlocks = config.getInt("buildersWandMaxBlocks", "items", 1024, 1, 4096, "Maximum number of blocks a single Builders Wand operation may place");
			buildersWandBlocksPerTick = config.getInt("buildersWandBlocksPerTick", "items", 128, 1, 4096, "Maximum number of blocks the Builders Wand may place per server tick");
			rodOfHarmonyCostPerBlock = config.getInt("rodOfHarmonyCostPerBlock", "items", 8, 0, Integer.MAX_VALUE, "EMC cost per block flown with the Rod of Harmony");
			rodOfHarmonyCostPerWallBlock = config.getInt("rodOfHarmonyCostPerWallBlock", "items", 576, 0, Integer.MAX_VALUE, "EMC cost per solid block tunnelled through with the Rod of Harmony");
			rodOfHarmonyMaxDistance = config.getInt("rodOfHarmonyMaxDistance", "items", 0, 0, 512, "Maximum Rod of Harmony travel distance in blocks; 0 means server view distance * 16");
			rodOfHarmonySafeRadius = config.getInt("rodOfHarmonySafeRadius", "items", 2, 0, 8, "Horizontal radius searched for a safe landing spot by the Rod of Harmony");
			enableAEGU = config.getBoolean("enableAEGU", "items", true, "Enable Alchemical Energy Generating Unit");
			enableStoneAlwaysEdible = config.getBoolean("enableStoneAlwaysEdible", "items", true, "If true, carrying a Body Stone or Life Stone in the main inventory or a Baubles slot lets you keep eating while your hunger bar is full. Does not consume EMC.");
			bowLightningEnabled = config.getBoolean("bowLightningEnabled", "items", true, "Whether the Storm Bow's arrows call down lightning on impact");

			ignitionRingIgniteBlocks = config.getBoolean("ignitionRingIgniteBlocks", "items", false, "If true, the Ignition Ring will passively ignite surrounding blocks.");
			zeroRingPlaceSnow = config.getBoolean("zeroRingPlaceSnow", "items", false, "If true, the Zero Ring will passively freeze water and place snow around the player.");

			craftableTome = config.getBoolean("craftableTome", "difficulty", false, "The Tome of Knowledge can be crafted.");
			altCraftingMat = config.getBoolean("altCraftingMat", "difficulty", false, "If true some ProjectE items require a nether star instead of a diamond.");
			useOldDamage = config.getBoolean("useOldDamage", "difficulty", false, "If true the old damage amounts from ProjectE 1.4.7 and before will be used for weapons.");
			offensiveAbilities = config.getBoolean("offensiveAbilities", "difficulty", true, "Set to false to disable Gem Armor offensive abilities (helmet zap and chestplate explosion)");
			katarDeathAura = config.getFloat("katarDeathAura", "difficulty", 1000F, 0, Integer.MAX_VALUE, "Amount of damage Katar AOE deals");

			config.getCategory("pedestalcooldown").setComment("Cooldown for various items within the pedestal. A cooldown of -1 will disable the functionality.\n" +
				"A cooldown of 0 will cause the actions to happen every tick. Use caution as a very low value could cause TPS issues.");

			archangelPedCooldown = config.getInt("archangelPedCooldown", "pedestalcooldown", 40, -1, Integer.MAX_VALUE, "Delay between Archangel Smite shooting arrows while in the pedestal.");
			bodyPedCooldown = config.getInt("bodyPedCooldown", "pedestalcooldown", 10, -1, Integer.MAX_VALUE, "Delay between Body Stone healing 0.5 shanks while in the pedestal.");
			evertidePedCooldown = config.getInt("evertidePedCooldown", "pedestalcooldown", 20, -1, Integer.MAX_VALUE, "Delay between Evertide Amulet trying to start rain while in the pedestal.");
			harvestPedCooldown = config.getInt("harvestPedCooldown", "pedestalcooldown", 10, -1, Integer.MAX_VALUE, "Delay between Harvest Goddess trying to grow and harvest while in the pedestal.");
			ignitePedCooldown = config.getInt("ignitePedCooldown", "pedestalcooldown", 40, -1, Integer.MAX_VALUE, "Delay between Ignition Ring trying to light entities on fire while in the pedestal.");
			lifePedCooldown = config.getInt("lifePedCooldown", "pedestalcooldown", 5, -1, Integer.MAX_VALUE, "Delay between Life Stone healing both food and hunger by 0.5 shank/heart while in the pedestal.");
			repairPedCooldown = config.getInt("repairPedCooldown", "pedestalcooldown", 20, -1, Integer.MAX_VALUE, "Delay between Talisman of Repair trying to repair player items while in the pedestal.");
			swrgPedCooldown = config.getInt("swrgPedCooldown", "pedestalcooldown", 70, -1, Integer.MAX_VALUE, "Delay between SWRG trying to smite mobs while in the pedestal.");
			soulPedCooldown = config.getInt("soulPedCooldown", "pedestalcooldown", 10, -1, Integer.MAX_VALUE, "Delay between Soul Stone healing 0.5 hearts while in the pedestal.");
			volcanitePedCooldown = config.getInt("volcanitePedCooldown", "pedestalcooldown", 20, -1, Integer.MAX_VALUE, "Delay between Volcanite Amulet trying to stop rain while in the pedestal.");
			zeroPedCooldown = config.getInt("zeroPedCooldown", "pedestalcooldown", 40, -1, Integer.MAX_VALUE, "Delay between Zero Ring trying to extinguish entities and freezing ground while in the pedestal.");

			timePedBonus = config.getInt("timePedBonus", "effects", 18, 0, 256, "Bonus ticks given by the Watch of Flowing Time while in the pedestal. 0 = effectively no bonus.");
			timePedMobSlowness = config.getFloat("timePedMobSlowness", "effects", 0.10F, 0.0F, 1.0F, "Factor the Watch of Flowing Time slows down mobs by while in the pedestal. Set to 1.0 for no slowdown.");
			interdictionMode = config.getBoolean("interdictionMode", "effects", true, "If true the Interdiction Torch only affects hostile mobs. If false it affects all non blacklisted living entities.");

			pickaxeAoeVeinMining = config.getBoolean("pickaxeAoeVeinMining", "items", false, "Instead of vein mining the ore you right click with your Dark/Red Matter Pick/Star it vein mines all ores in an AOE around you like it did in ProjectE before version 1.4.4.");
			harvBandGrass = config.getBoolean("harvBandGrass", "items", false, "Allows the Harvest Goddess Band to passively grow tall grass, flowers, etc, on top of grass blocks.");
			disableAllRadiusMining = config.getBoolean("disableAllRadiusMining", "items", false, "If set to true, disables all radius-based mining functionaliy (right click of tools)");

			nbtWhitelistConfig = config.getStringList("nbtWhitelist", "nbt", new String[]{
				"Botania:specialFlower|type",
				"Botania:specialFlower|color",
				"etfuturum:shulker_box|color"
			}, "Format: modid:itemname|nbt_key. These NBT tags will be preserved and act as distinct items in the transmutation table.");

			dynamicEmcNbtConfig = config.getStringList("dynamicEmcNbt", "nbt", new String[]{
				"mana|0.001"
			}, "Format: nbt_key|emc_multiplier. When an item with this NBT is consumed, the value will be multiplied by the multiplier and added to EMC, then the tag is stripped.");

			parseNBTConfigs();

			PELogger.logInfo("Configuration file loaded.");
		}
		catch (Exception e) {
			PELogger.logFatal("Caught exception while loading config file!");
			e.printStackTrace();
		}
		finally {
			if (config.hasChanged())
				config.save();
		}
	}

	private static void parseNBTConfigs() {
		nbtDistinctlist.clear();
		for (String entry : nbtWhitelistConfig) {
			String[] split = entry.split("\\|");
			if (split.length != 2) continue;
			// 启动时直接解析出 Item
			Object obj = Item.itemRegistry.getObject(split[0]);
			if (obj instanceof Item item)
				nbtDistinctlist.computeIfAbsent(item, k -> new ArrayList<>()).add(split[1]);
		}

		dynamicEmcNbt.clear();
		for (String entry : dynamicEmcNbtConfig) {
			String[] split = entry.split("\\|");
			if (split.length == 2) {
				try {
					dynamicEmcNbt.put(split[0], Double.parseDouble(split[1]));
				} catch (NumberFormatException e) {
					PELogger.logError("Invalid multiplier for dynamic EMC NBT: " + entry);
				}
			}
		}
	}
}
