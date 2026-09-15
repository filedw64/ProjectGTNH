package moze_intel.projecte.integration;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import moze_intel.projecte.gameObjs.ObjHandler;
import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import net.minecraft.item.Item;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;

public class HardcoreRecipeLoader {

	public static void loadHardcoreRecipes() {
		registerThaumcraftRecipes();
		registerCraftingRecipes();
		registerGregTechRecipes();
		registerAvaritiaRecipes();
	}

	private static void registerThaumcraftRecipes() {
		// 水晶矩阵块
		ItemStack crystalMatrix = GameRegistry.findItemStack("Avaritia", "Crystal_Matrix", 1);
		if (crystalMatrix == null) {
			System.err.println("[ProjectE-GTNH] 无法找到 Avaritia:Crystal_Matrix，贤者之石注魔配方加载可能失败！");
		}

		// 贤者之石
		ItemStack philosStone = new ItemStack(ObjHandler.philosStone);

		// 贡品
		ItemStack[] types = new ItemStack[] {
			new ItemStack(Blocks.glowstone),        // 萤石块
			new ItemStack(Blocks.redstone_block),   // 红石块
			new ItemStack(Blocks.gold_block),       // 金块
			new ItemStack(Blocks.iron_block),       // 铁块
			new ItemStack(Blocks.lapis_block),      // 青金石块
			new ItemStack(Blocks.emerald_block)     // 绿宝石块
		};

		ItemStack[] tributes = new ItemStack[24];

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 6; j++) {
				tributes[i * 6 + j] = types[j].copy();
			}
		}

		// 定义注魔所需的源质
		AspectList aspects = new AspectList()
			.add(Aspect.EXCHANGE, 128)   // 交换 (PERMUTATIO)
			.add(Aspect.MAGIC, 64)       // 魔法 (PRAECANTATIO)
			.add(Aspect.CRAFT, 64)       // 制作 (FABRICO)
			.add(Aspect.AURA, 32)        // 光环/灵气 (AURAM)
			.add(Aspect.ENERGY, 32);     // 能量 (POTENTIA)

		// 注册注魔配方
		ThaumcraftApi.addInfusionCraftingRecipe(
			"PHILOSOPHER_STONE",
			philosStone,
			8,
			aspects,
			crystalMatrix,
			tributes
		);

		// ==========================================
		// 炼金术箱子 (Alchemical Chest) 注魔配方
		// ==========================================

		ItemStack draconiumChest = GameRegistry.findItemStack("DraconicEvolution", "draconiumChest", 1);
		ItemStack compressedChest = GameRegistry.findItemStack("avaritiaddons", "CompressedChest", 1);

		ItemStack dirtChest = GameRegistry.findItemStack("IronChest", "BlockIronChest", 1);
		if (dirtChest != null) {
			dirtChest.setItemDamage(7);
		}

		ItemStack covLow = new ItemStack(ObjHandler.covalence, 1, 0);
		ItemStack covMed = new ItemStack(ObjHandler.covalence, 1, 1);
		ItemStack covHigh = new ItemStack(ObjHandler.covalence, 1, 2);

		ItemStack[] alchChestTributes = new ItemStack[] {
			covLow, covMed, covHigh, compressedChest,
			covLow, covMed, covHigh, dirtChest
		};

		// 源质
		AspectList alchChestAspects = new AspectList()
			.add(Aspect.getAspect("primordium"), 64) // 原初
			.add(Aspect.MAGIC, 128)                  // 魔法 (Praecantatio)
			.add(Aspect.ENERGY, 128)                 // 能量 (Potentia)
			.add(Aspect.getAspect("aequalitas"), 256)// 等价
			.add(Aspect.VOID, 256);                  // 虚空 (Vacuos)

		ThaumcraftApi.addInfusionCraftingRecipe(
			"ALCHEMICAL_CHEST",
			new ItemStack(ObjHandler.alchChest),
			10,
			alchChestAspects,
			draconiumChest,
			alchChestTributes
		);

		// ==========================================
		// 能量凝聚器 (Energy Condenser) 注魔配方
		// ==========================================

		// 贡品：16个物品（8个钻石块，8个黑曜石）
		ItemStack[] condenserTributes = new ItemStack[16];
		ItemStack diamondBlock = new ItemStack(Blocks.diamond_block);
		ItemStack obsidian = new ItemStack(Blocks.obsidian);

		// 交替填充，确保完美的对称性
		for (int i = 0; i < 8; i++) {
			condenserTributes[i * 2] = diamondBlock.copy();
			condenserTributes[i * 2 + 1] = obsidian.copy();
		}

		AspectList condenserAspects = new AspectList()
			.add(Aspect.EXCHANGE, 1024); // 交换 (Permutatio)

		ThaumcraftApi.addInfusionCraftingRecipe(
			"CONDENSER_MK1",
			new ItemStack(ObjHandler.condenser),
			12, // 极高的不稳定性，因为消耗 1024 源质，注魔时间长
			condenserAspects,
			new ItemStack(ObjHandler.alchChest),
			condenserTributes
		);
		// ==========================================
		// 潮汐护符 (Evertide Amulet)
		// ==========================================
		ItemStack dmBlock = new ItemStack(ObjHandler.matterBlock, 1, 0); // 暗物质块

		ItemStack[] evertideTributes = new ItemStack[18];
		for (int i = 0; i < 18; i++) {
			evertideTributes[i] = new ItemStack(Items.water_bucket);
		}

		AspectList evertideAspects = new AspectList()
			.add(Aspect.WATER, 1024); // Aqua

		ThaumcraftApi.addInfusionCraftingRecipe(
			"EVERTIDE_AMULET",
			new ItemStack(ObjHandler.everTide),
			8, // 不稳定性较高
			evertideAspects,
			dmBlock,
			evertideTributes
		);

		// ==========================================
		// 熔焰护符 (Volcanite Amulet)
		// ==========================================
		ItemStack[] volcaniteTributes = new ItemStack[18];
		for (int i = 0; i < 18; i++) {
			volcaniteTributes[i] = new ItemStack(Items.lava_bucket);
		}

		AspectList volcaniteAspects = new AspectList()
			.add(Aspect.FIRE, 1024); // Ignis

		ThaumcraftApi.addInfusionCraftingRecipe(
			"VOLCANITE_AMULET",
			new ItemStack(ObjHandler.volcanite),
			8, // 不稳定性较高
			volcaniteAspects,
			dmBlock,
			volcaniteTributes
		);

		// ==========================================
		// 修复护符 (Repair Talisman)
		// ==========================================
		ItemStack string = new ItemStack(Items.string);

		ItemStack[] repairTributes = new ItemStack[24];

		ItemStack[] repairPattern = new ItemStack[] { string, covLow, string, covMed, string, covHigh };
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 6; j++) {
				repairTributes[i * 6 + j] = repairPattern[j].copy();
			}
		}

		AspectList repairAspects = new AspectList()
			.add(Aspect.TOOL, 256)      // 工具 (Instrumentum)
			.add(Aspect.CRAFT, 256)     // 制作 (Fabrico)
			.add(Aspect.SENSES, 256)    // 感觉 (Sensus)
			.add(Aspect.ORDER, 256);    // 秩序 (Ordo)

		ThaumcraftApi.addInfusionCraftingRecipe(
			"REPAIR_TALISMAN",
			new ItemStack(ObjHandler.repairTalisman),
			10, // 24个物品，不稳定性调高到 10
			repairAspects,
			new ItemStack(Items.paper), // 中心：纸
			repairTributes
		);

		ItemStack aeternalisBlock = new ItemStack(ObjHandler.fuelBlock, 1, 2); // 永恒燃料块
		ItemStack novaCatalyst = new ItemStack(ObjHandler.novaCatalyst); // 爆破新星
		ItemStack novaCataclysm = new ItemStack(ObjHandler.novaCataclysm); // 灾难新星

		// ==========================================
		// 暗物质熔炉 (Dark Matter Furnace) - 16贡品
		// ==========================================
		ItemStack[] dmFurnaceTributes = new ItemStack[16];
		// 8暗物质块，4爆破新星，4灾难新星
		// 完美对称排布：暗物质块 - 爆破 - 暗物质块 - 灾难 (循环 4 次)
		ItemStack[] dmFurnacePattern = new ItemStack[] { dmBlock, novaCatalyst, dmBlock, novaCataclysm };
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				dmFurnaceTributes[i * 4 + j] = dmFurnacePattern[j].copy();
			}
		}

		AspectList dmFurnaceAspects = new AspectList()
			.add(Aspect.FIRE, 2048)                    // Ignis
			.add(Aspect.getAspect("infernus"), 512)    // 炼狱
			.add(Aspect.getAspect("terminus"), 128);   // 终焉

		ThaumcraftApi.addInfusionCraftingRecipe(
			"DM_FURNACE",
			new ItemStack(ObjHandler.dmFurnaceOff),
			12, // 较高不稳定性
			dmFurnaceAspects,
			new ItemStack(Blocks.furnace),
			dmFurnaceTributes
		);

		// ==========================================
		// 毁灭燧石 (Destruction Catalyst) - 32贡品
		// ==========================================
		ItemStack[] destCatalystTributes = new ItemStack[32];
		ItemStack flintAndSteel = new ItemStack(Items.flint_and_steel, 1, 32767); // 忽略耐久的打火石

		// 8打火石，8灾难新星，16永恒燃料块
		// 完美对称排布：打火石 - 永恒块 - 灾难 - 永恒块 (循环 8 次)
		ItemStack[] destPattern = new ItemStack[] { flintAndSteel, aeternalisBlock, novaCataclysm, aeternalisBlock };
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 4; j++) {
				destCatalystTributes[i * 4 + j] = destPattern[j].copy();
			}
		}

		AspectList destCatalystAspects = new AspectList()
			.add(Aspect.getAspect("ira"), 64)          // 愤怒
			.add(Aspect.getAspect("infernus"), 64)     // 炼狱
			.add(Aspect.FIRE, 64);                     // Ignis

		ThaumcraftApi.addInfusionCraftingRecipe(
			"DESTRUCTION_CATALYST",
			new ItemStack(ObjHandler.dCatalyst),
			15, // 极高不稳定性 (32物品)
			destCatalystAspects,
			new ItemStack(Items.flint),
			destCatalystTributes
		);

		// ==========================================
		// 不稳定水晶 (Hyperkinetic Lens) - 32贡品
		// ==========================================
		ItemStack[] hyperTributes = new ItemStack[32];
		// 14灾难新星，14钻石块，4暗物质块
		hyperTributes[0] = dmBlock.copy();
		hyperTributes[8] = dmBlock.copy();
		hyperTributes[16] = dmBlock.copy();
		hyperTributes[24] = dmBlock.copy();

		// 剩下的 28 个空位，交替填入灾难新星和钻石块
		boolean toggle = true;
		for (int i = 0; i < 32; i++) {
			if (hyperTributes[i] == null) {
				hyperTributes[i] = toggle ? novaCataclysm.copy() : diamondBlock.copy();
				toggle = !toggle;
			}
		}

		AspectList hyperAspects = new AspectList()
			.add(Aspect.ENERGY, 512)     // Potentia
			.add(Aspect.ENTROPY, 512);   // Perditio

		ThaumcraftApi.addInfusionCraftingRecipe(
			"HYPERKINETIC_LENS",
			new ItemStack(ObjHandler.hyperLens),
			15,
			hyperAspects,
			new ItemStack(Blocks.redstone_block),
			hyperTributes
		);

		// ==========================================
		// 暗物质工具系列 (Dark Matter Tools)
		// ==========================================
		ItemStack dm = new ItemStack(ObjHandler.matter, 1, 0); // 暗物质
		ItemStack dmBlockTool = new ItemStack(ObjHandler.matterBlock, 1, 0);
		ItemStack diaBlockTool = new ItemStack(Blocks.diamond_block);

		// 8 贡品：4暗物质 + 2暗物质块 + 2钻石块
		ItemStack[] dmToolTributes = new ItemStack[] {
			dm.copy(), dmBlockTool.copy(), dm.copy(), diaBlockTool.copy(),
			dm.copy(), dmBlockTool.copy(), dm.copy(), diaBlockTool.copy()
		};

		// 源质定义
		AspectList weaponAspects = new AspectList().add(Aspect.WEAPON, 1024); // Telum (武器)
		AspectList toolAspects = new AspectList().add(Aspect.TOOL, 1024);     // Instrumentum (工具)

		int toolInstability = 6; // 中等偏上的不稳定性

		// 1. 暗物质剑 (DM Sword)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_SWORD",
			new ItemStack(ObjHandler.dmSword), toolInstability, weaponAspects,
			new ItemStack(Items.diamond_sword), dmToolTributes);

		// 2. 暗物质镐 (DM Pickaxe)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_PICK",
			new ItemStack(ObjHandler.dmPick), toolInstability, toolAspects,
			new ItemStack(Items.diamond_pickaxe), dmToolTributes);

		// 3. 暗物质斧 (DM Axe)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_AXE",
			new ItemStack(ObjHandler.dmAxe), toolInstability, toolAspects,
			new ItemStack(Items.diamond_axe), dmToolTributes);

		// 4. 暗物质锹 (DM Shovel)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_SHOVEL",
			new ItemStack(ObjHandler.dmShovel), toolInstability, toolAspects,
			new ItemStack(Items.diamond_shovel), dmToolTributes);

		// 5. 暗物质锄 (DM Hoe)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_HOE",
			new ItemStack(ObjHandler.dmHoe), toolInstability, toolAspects,
			new ItemStack(Items.diamond_hoe), dmToolTributes);

		// 6. 暗物质锤 (DM Hammer)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_HAMMER",
			new ItemStack(ObjHandler.dmHammer), toolInstability, toolAspects,
			dmBlockTool.copy(), dmToolTributes);

		// 7. 暗物质剪刀 (DM Shears)
		ThaumcraftApi.addInfusionCraftingRecipe("DM_SHEARS",
			new ItemStack(ObjHandler.dmShears), toolInstability, toolAspects,
			new ItemStack(Items.shears), dmToolTributes);

		// ==========================================
		// 暗物质护甲系列 (Dark Matter Armor)
		// ==========================================
		AspectList armorAspects = new AspectList().add(Aspect.ARMOR, 1024); // Tutamen (护甲)
		int armorInstability = 7;

		// 1. 暗物质头盔 (DM Helmet - pe_dm_armor_0)
		ItemStack[] dmHelmetTributes = new ItemStack[] {
			new ItemStack(Items.leather_helmet), new ItemStack(Items.iron_helmet),
			new ItemStack(Items.golden_helmet), dmBlockTool.copy(),
			new ItemStack(Items.leather_helmet), new ItemStack(Items.iron_helmet),
			new ItemStack(Items.golden_helmet), dmBlockTool.copy()
		};
		ThaumcraftApi.addInfusionCraftingRecipe("DM_HELMET",
			new ItemStack(ObjHandler.dmHelmet), armorInstability, armorAspects,
			new ItemStack(Items.diamond_helmet), dmHelmetTributes);

		// 2. 暗物质胸甲 (DM Chestplate - pe_dm_armor_1)
		ItemStack[] dmChestTributes = new ItemStack[] {
			new ItemStack(Items.leather_chestplate), new ItemStack(Items.iron_chestplate),
			new ItemStack(Items.golden_chestplate), dmBlockTool.copy(),
			new ItemStack(Items.leather_chestplate), new ItemStack(Items.iron_chestplate),
			new ItemStack(Items.golden_chestplate), dmBlockTool.copy()
		};
		ThaumcraftApi.addInfusionCraftingRecipe("DM_CHEST",
			new ItemStack(ObjHandler.dmChest), armorInstability, armorAspects,
			new ItemStack(Items.diamond_chestplate), dmChestTributes);

		// 3. 暗物质护腿 (DM Leggings - pe_dm_armor_2)
		ItemStack[] dmLegsTributes = new ItemStack[] {
			new ItemStack(Items.leather_leggings), new ItemStack(Items.iron_leggings),
			new ItemStack(Items.golden_leggings), dmBlockTool.copy(),
			new ItemStack(Items.leather_leggings), new ItemStack(Items.iron_leggings),
			new ItemStack(Items.golden_leggings), dmBlockTool.copy()
		};
		ThaumcraftApi.addInfusionCraftingRecipe("DM_LEGS",
			new ItemStack(ObjHandler.dmLegs), armorInstability, armorAspects,
			new ItemStack(Items.diamond_leggings), dmLegsTributes);

		// 4. 暗物质靴子 (DM Boots - pe_dm_armor_3)
		ItemStack[] dmBootsTributes = new ItemStack[] {
			new ItemStack(Items.leather_boots), new ItemStack(Items.iron_boots),
			new ItemStack(Items.golden_boots), dmBlockTool.copy(),
			new ItemStack(Items.leather_boots), new ItemStack(Items.iron_boots),
			new ItemStack(Items.golden_boots), dmBlockTool.copy()
		};
		ThaumcraftApi.addInfusionCraftingRecipe("DM_BOOTS",
			new ItemStack(ObjHandler.dmFeet), armorInstability, armorAspects,
			new ItemStack(Items.diamond_boots), dmBootsTributes);

		// ==========================================
		// 戒指/指环系列 (ProjectE Rings)
		// ==========================================
		ItemStack ironBand = new ItemStack(ObjHandler.ironBand); // 中心物品：铁指环
		ItemStack dmBlockRing = new ItemStack(ObjHandler.matterBlock, 1, 0); // 暗物质方块
		int ringInstability = 10; // 24个物品的高不稳定性

		// ------------------------------------------
		// 1. 丰收女神戒指 (Harvest Goddess Band)
		// 4树苗, 4花, 4原木, 4小麦, 8暗物质方块
		// ------------------------------------------
		ItemStack sapling = new ItemStack(Blocks.sapling, 1, 32767);
		ItemStack flower = new ItemStack(Blocks.red_flower, 1, 32767);
		ItemStack log = new ItemStack(Blocks.log, 1, 32767);
		ItemStack wheat = new ItemStack(Items.wheat);

		ItemStack[] harvestTributes = new ItemStack[24];
		// 模式(6个): 树苗 - 暗物质块 - 花 - 原木 - 暗物质块 - 小麦 (循环 4 次)
		ItemStack[] harvestPattern = new ItemStack[] { sapling, dmBlockRing, flower, log, dmBlockRing, wheat };
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 6; j++) {
				harvestTributes[i * 6 + j] = harvestPattern[j].copy();
			}
		}
		AspectList harvestAspects = new AspectList()
			.add(Aspect.TREE, 1024)   // Arbor
			.add(Aspect.PLANT, 1024)  // Herba
			.add(Aspect.EARTH, 1024)  // Terra
			.add(Aspect.LIFE, 1024);  // Victus

		ThaumcraftApi.addInfusionCraftingRecipe("HARVEST_GODDESS",
			new ItemStack(ObjHandler.harvestGod), ringInstability, harvestAspects, ironBand, harvestTributes);

		// ------------------------------------------
		// 2. 黑洞指环 (Black Hole Band)
		// 16羊毛, 8暗物质方块
		// ------------------------------------------
		ItemStack wool = new ItemStack(Blocks.wool, 1, 32767);
		ItemStack[] blackHoleTributes = new ItemStack[24];
		// 模式(3个): 羊毛 - 羊毛 - 暗物质块 (循环 8 次)
		ItemStack[] blackHolePattern = new ItemStack[] { wool, wool, dmBlockRing };
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 3; j++) {
				blackHoleTributes[i * 3 + j] = blackHolePattern[j].copy();
			}
		}
		AspectList blackHoleAspects = new AspectList()
			.add(Aspect.GREED, 2048)  // Lucrum
			.add(Aspect.VOID, 2048);  // Vacuos

		ThaumcraftApi.addInfusionCraftingRecipe("BLACK_HOLE_BAND",
			new ItemStack(ObjHandler.blackHole), ringInstability, blackHoleAspects, ironBand, blackHoleTributes);

		// ------------------------------------------
		// 3. 大天使的惩戒 (Archangel's Smite)
		// 8羽毛, 8弓, 8暗物质方块
		// ------------------------------------------
		ItemStack feather = new ItemStack(Items.feather);
		ItemStack bow = new ItemStack(Items.bow, 1, 32767);
		ItemStack[] archangelTributes = new ItemStack[24];
		// 模式(3个): 羽毛 - 弓 - 暗物质块 (循环 8 次)
		ItemStack[] archangelPattern = new ItemStack[] { feather, bow, dmBlockRing };
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 3; j++) {
				archangelTributes[i * 3 + j] = archangelPattern[j].copy();
			}
		}
		AspectList archangelAspects = new AspectList().add(Aspect.WEAPON, 4096); // Telum

		ThaumcraftApi.addInfusionCraftingRecipe("ARCHANGEL_SMITE",
			new ItemStack(ObjHandler.angelSmite), ringInstability, archangelAspects, ironBand, archangelTributes);

		// ------------------------------------------
		// 4. 烈焰指环 (Ignition Ring)
		// 8打火石, 8莫比乌斯燃料块, 8暗物质方块
		// ------------------------------------------
		ItemStack flintSteel = new ItemStack(Items.flint_and_steel, 1, 32767);
		ItemStack mobiusBlock = new ItemStack(ObjHandler.fuelBlock, 1, 1);
		ItemStack[] ignitionTributes = new ItemStack[24];
		// 模式(3个): 打火石 - 莫比乌斯块 - 暗物质块 (循环 8 次)
		ItemStack[] ignitionPattern = new ItemStack[] { flintSteel, mobiusBlock, dmBlockRing };
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 3; j++) {
				ignitionTributes[i * 3 + j] = ignitionPattern[j].copy();
			}
		}
		AspectList ignitionAspects = new AspectList()
			.add(Aspect.WEATHER, 2048)              // Tempestas
			.add(Aspect.getAspect("infernus"), 2048); // Infernus

		ThaumcraftApi.addInfusionCraftingRecipe("IGNITION_RING",
			new ItemStack(ObjHandler.ignition), ringInstability, ignitionAspects, ironBand, ignitionTributes);

		// ------------------------------------------
		// 5. 零度指环 (Zero Ring)
		// 8雪块, 8雪球, 8暗物质方块
		// ------------------------------------------
		ItemStack snowBlock = new ItemStack(Blocks.snow);
		ItemStack snowball = new ItemStack(Items.snowball);
		ItemStack[] zeroTributes = new ItemStack[24];
		// 模式(3个): 雪块 - 雪球 - 暗物质块 (循环 8 次)
		ItemStack[] zeroPattern = new ItemStack[] { snowBlock, snowball, dmBlockRing };
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 3; j++) {
				zeroTributes[i * 3 + j] = zeroPattern[j].copy();
			}
		}
		AspectList zeroAspects = new AspectList()
			.add(Aspect.WEATHER, 2048)  // Tempestas
			.add(Aspect.COLD, 2048);    // Gelum

		ThaumcraftApi.addInfusionCraftingRecipe("ZERO_RING",
			new ItemStack(ObjHandler.zero), ringInstability, zeroAspects, ironBand, zeroTributes);

		// ------------------------------------------
		// 6. 疾风戒指 (Swiftwolf's Rending Gale)
		// 12羽毛, 12暗物质方块
		// ------------------------------------------
		ItemStack[] swrgTributes = new ItemStack[24];
		// 模式(2个): 羽毛 - 暗物质块 (循环 12 次)
		ItemStack[] swrgPattern = new ItemStack[] { feather, dmBlockRing };
		for (int i = 0; i < 12; i++) {
			for (int j = 0; j < 2; j++) {
				swrgTributes[i * 2 + j] = swrgPattern[j].copy();
			}
		}
		AspectList swrgAspects = new AspectList()
			.add(Aspect.WEATHER, 1024)                 // Tempestas
			.add(Aspect.FLIGHT, 1024)                  // Volatus
			.add(Aspect.getAspect("tabernus"), 1024)   // Tabernus (如果有些附属没加这个源质会返回null，建议检查)
			.add(Aspect.TRAVEL, 1024);                 // Iter

		ThaumcraftApi.addInfusionCraftingRecipe("SWRG_RING",
			new ItemStack(ObjHandler.swrg), ringInstability, swrgAspects, ironBand, swrgTributes);

	}

	private static void registerCraftingRecipes() {
		ItemStack philosStone = new ItemStack(ObjHandler.philosStone);
		ItemStack coal = new ItemStack(Items.coal);
		ItemStack alchCoal = new ItemStack(ObjHandler.fuels, 1, 0);
		ItemStack mobius = new ItemStack(ObjHandler.fuels, 1, 1);
		ItemStack aeternalis = new ItemStack(ObjHandler.fuels, 1, 2);

		ItemStack mobiusBlock = new ItemStack(ObjHandler.fuelBlock, 1, 1);
		ItemStack aeternalisBlock = new ItemStack(ObjHandler.fuelBlock, 1, 2);
		ItemStack gunpowder = new ItemStack(Items.gunpowder);
		ItemStack catalyst = new ItemStack(ObjHandler.novaCatalyst);

		// 1. 炼金煤炭 (上下左右各放一个煤炭，中间贤者之石)
		GameRegistry.addRecipe(alchCoal,
			" F ",
			"FPF",
			" F ",
			'P', philosStone, 'F', coal
		);

		// 2. 莫比乌斯燃料 (上下左右各放一个炼金煤炭，中间贤者之石)
		GameRegistry.addRecipe(mobius,
			" F ",
			"FPF",
			" F ",
			'P', philosStone, 'F', alchCoal
		);

		// 3. 永恒燃料 (上下左右各放一个莫比乌斯燃料，中间贤者之石)
		GameRegistry.addRecipe(aeternalis,
			" F ",
			"FPF",
			" F ",
			'P', philosStone, 'F', mobius
		);

		// 4. 爆破新星 (TNT经典配方结构，一次做2个。火药保持不变，沙子位置换成莫比乌斯燃料块)
		GameRegistry.addRecipe(new ItemStack(ObjHandler.novaCatalyst, 2),
			"GSG",
			"SGS",
			"GSG",
			'G', gunpowder, 'S', mobiusBlock
		);

		// 5. 灾难新星 (TNT经典配方结构，一次做2个。火药位置换成永恒燃料块，沙子位置换成爆破新星)
		GameRegistry.addRecipe(new ItemStack(ObjHandler.novaCataclysm, 2),
			"GSG",
			"SGS",
			"GSG",
			'G', aeternalisBlock, 'S', catalyst
		);

		ItemStack charcoal = new ItemStack(Items.coal, 1, 1); // 木炭
		ItemStack cobble = new ItemStack(Blocks.cobblestone); // 原石
		ItemStack iron = new ItemStack(Items.iron_ingot);     // 铁锭
		ItemStack redstone = new ItemStack(Items.redstone);   // 红石
		ItemStack diamond = new ItemStack(Items.diamond);     // 钻石

		// 6. 低等共价粉 (4木炭 + 4原石 + 贤者之石)
		GameRegistry.addShapelessRecipe(new ItemStack(ObjHandler.covalence, 40, 0),
			philosStone, charcoal, charcoal, charcoal, charcoal, cobble, cobble, cobble, cobble
		);

		// 7. 中等共价粉 (4铁锭 + 4红石 + 贤者之石)
		GameRegistry.addShapelessRecipe(new ItemStack(ObjHandler.covalence, 40, 1),
			philosStone, iron, iron, iron, iron, redstone, redstone, redstone, redstone
		);

		// 8. 高等共价粉 (4煤炭 + 4钻石 + 贤者之石)
		GameRegistry.addShapelessRecipe(new ItemStack(ObjHandler.covalence, 40, 2),
			philosStone, coal, coal, coal, coal, diamond, diamond, diamond, diamond
		);

		// ==========================================
		// 炼金术之袋 (Alchemical Bags) 16种颜色
		// ==========================================
		ItemStack covLow = new ItemStack(ObjHandler.covalence, 1, 0);
		ItemStack covMed = new ItemStack(ObjHandler.covalence, 1, 1);
		ItemStack covHigh = new ItemStack(ObjHandler.covalence, 1, 2);
		ItemStack enderChest = new ItemStack(Blocks.ender_chest);
		ItemStack alchChest = new ItemStack(ObjHandler.alchChest);

		// 使用循环一次性注册 16 种颜色的炼金袋
		for (int i = 0; i < 16; i++) {
			GameRegistry.addRecipe(new ItemStack(ObjHandler.alchBag, 1, i),
				"ABC",
				"DED",
				"FFF",
				'A', covLow,
				'B', covMed,
				'C', covHigh,
				'D', enderChest,
				'E', alchChest,
				'F', new ItemStack(Blocks.wool, 1, i) // 羊毛的 meta 对应袋子的颜色
			);
		}

		ItemStack crystalMatrix = GameRegistry.findItemStack("Avaritia", "Crystal_Matrix", 1);
		ItemStack solarSplitter = GameRegistry.findItemStack("supersolarpanel", "solarsplitter", 1);
		ItemStack blastFurnace = GameRegistry.findItemStack("etfuturum", "blast_furnace", 1);
		ItemStack alloyGlass = GameRegistry.findItemStack("IC2", "blockAlloyGlass", 1);

		// ==========================================
		// 能量收集器 MK1 (Energy Collector MK1)
		// ==========================================
		GameRegistry.addRecipe(new ItemStack(ObjHandler.energyCollector),
			"ABA",
			"CDC",
			"EFE",
			'A', new ItemStack(Blocks.glowstone),
			'B', solarSplitter,
			'C', new ItemStack(Blocks.quartz_block),
			'D', crystalMatrix,
			'E', new ItemStack(Blocks.daylight_detector),
			'F', blastFurnace
		);

		// ==========================================
		// 反物质继电器 MK1 (Anti-Matter Relay MK1)
		// ==========================================
		GameRegistry.addRecipe(new ItemStack(ObjHandler.relay),
			"OGO",
			"GCG",
			"OGO",
			'O', new ItemStack(Blocks.obsidian),
			'G', alloyGlass,
			'C', crystalMatrix
		);

		// ==========================================
		// 一级卡莱恩能量之星 (Klein Star Ein)
		// ==========================================
		ItemStack diamondBlock = new ItemStack(Blocks.diamond_block);

		GameRegistry.addRecipe(new ItemStack(ObjHandler.kleinStars, 1, 0),
			"MMM",
			"MDM",
			"MMM",
			'M', mobiusBlock,
			'D', diamondBlock
		);

		// ==========================================
		// 禁止火把 (Interdiction Torch)
		// ==========================================
		ItemStack torch = new ItemStack(Blocks.torch);
		ItemStack redstoneTorch = new ItemStack(Blocks.redstone_torch);

		ItemStack soulTorch = GameRegistry.findItemStack("etfuturum", "soul_torch", 1);
		ItemStack glowstoneTorch = GameRegistry.findItemStack("GalacticraftCore", "tile.glowstoneTorch", 1);

		// 容错处理
		if (glowstoneTorch == null) {
			glowstoneTorch = GameRegistry.findItemStack("GalacticraftCore", "glowstoneTorch", 1);
		}


		// 输出数量我这里默认设为 1，如果你想一次做多个，可以在 confuseTorch 后面加上数量，比如 new ItemStack(ObjHandler.confuseTorch, 2)
		GameRegistry.addRecipe(new ItemStack(ObjHandler.confuseTorch),
			"ABC",
			"DED",
			"FFF",
			'A', torch,
			'B', redstoneTorch,
			'C', soulTorch,
			'D', glowstoneTorch,
			'E', philosStone,
			'F', aeternalisBlock
		);

		// ==========================================
		// 暗物质 (Dark Matter) - 第二阶段核心
		// ==========================================

		// 提取 GregTech 的基础物品实例
		Item gtMetaItem = GameRegistry.findItem("gregtech", "gt.metaitem.01");

		// A: IV 力场发生器
		ItemStack ivFieldGen = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 32674) : null;
		// B: IV 发射器
		ItemStack ivEmitter = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 32684) : null;
		// C: IV 传感器
		ItemStack ivSensor = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 32694) : null;
		// D: 硅岩锭 (Naquadah Ingot)
		ItemStack naquadahIngot = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 11324) : null;

		// E: 永恒燃料块
		ItemStack aeternalisBlock2 = new ItemStack(ObjHandler.fuelBlock, 1, 2);

		if (ivFieldGen != null && ivEmitter != null && ivSensor != null && naquadahIngot != null) {
			GameRegistry.addRecipe(new ItemStack(ObjHandler.matter, 1, 0), // matter meta 0 是暗物质
				"BAC",
				"DED",
				"CAB",
				'A', ivFieldGen,
				'B', ivEmitter,
				'C', ivSensor,
				'D', naquadahIngot,
				'E', aeternalisBlock2
			);
		} else {
			System.err.println("[ProjectE-GTNH] 暗物质(Dark Matter) 3x3 配方注册失败！请检查 GregTech 是否正确加载。");
		}

		// ==========================================
		// 催化水晶 (Catalytic Lens)
		// ==========================================
		ItemStack dmBlockCatalyst = new ItemStack(ObjHandler.matterBlock, 1, 0);
		ItemStack destructionCatalyst = new ItemStack(ObjHandler.dCatalyst);
		ItemStack hyperLens = new ItemStack(ObjHandler.hyperLens);

		// 配方 1: BAC
		GameRegistry.addRecipe(new ItemStack(ObjHandler.cataliticLens),
			"AAA",
			"BAC",
			"AAA",
			'A', dmBlockCatalyst,
			'B', destructionCatalyst,
			'C', hyperLens
		);

		// 配方 2: CAB (左右对换)
		GameRegistry.addRecipe(new ItemStack(ObjHandler.cataliticLens),
			"AAA",
			"CAB",
			"AAA",
			'A', dmBlockCatalyst,
			'C', destructionCatalyst,
			'B', hyperLens
		);


		// ==========================================
		// 铁指环 (Iron Band) - 基础合成材料
		// ==========================================
		ItemStack ironBlock = new ItemStack(Blocks.iron_block);
		ItemStack darkMatter = new ItemStack(ObjHandler.matter, 1, 0); // 暗物质

		GameRegistry.addRecipe(new ItemStack(ObjHandler.ironBand),
			"III",
			"IDI",
			"III",
			'I', ironBlock,
			'D', darkMatter
		);

		// ==========================================
		// 红物质 (Red Matter) - 无序合成
		// ==========================================
		// 1. 极元始珍珠 (Avaritia:big_pearl)
		Item itemBigPearl = GameRegistry.findItem("Avaritia", "big_pearl");
		ItemStack bigPearl = itemBigPearl != null ? new ItemStack(itemBigPearl) : null;

		// 2. 无尽块 (Avaritia:Resource_Block meta 1)
		net.minecraft.block.Block blockInfinity = GameRegistry.findBlock("Avaritia", "Resource_Block");
		ItemStack infinityBlock = blockInfinity != null ? new ItemStack(blockInfinity, 1, 1) : null;

		// 3. 龙之能量核心 (DraconicEvolution:draconiumEnergyCore)
		Item itemDraconicCore = GameRegistry.findItem("DraconicEvolution", "draconiumEnergyCore");
		ItemStack draconicCore = itemDraconicCore != null ? new ItemStack(itemDraconicCore) : null;

		// 4. 创世碎片 (TaintedMagic:ItemMaterial meta 5)
		Item itemTaintedMat = GameRegistry.findItem("TaintedMagic", "ItemMaterial");
		ItemStack genesisShard = itemTaintedMat != null ? new ItemStack(itemTaintedMat, 1, 5) : null;

		// 5. 纯净泪水 (WarpTheory:item.warptheory.cleanser)
		Item itemCleanser = GameRegistry.findItem("WarpTheory", "item.warptheory.cleanser");
		if (itemCleanser == null) itemCleanser = GameRegistry.findItem("WarpTheory", "cleanser");
		ItemStack pureTear = itemCleanser != null ? new ItemStack(itemCleanser) : null;

		// 6. 永恒魔力池 (Botania:pool meta 1)
		net.minecraft.block.Block blockPool = GameRegistry.findBlock("Botania", "pool");
		ItemStack everPool = blockPool != null ? new ItemStack(blockPool, 1, 1) : null;

		// 7, 8, 9. 暗物质方块 x3
		ItemStack dmBlockRM = new ItemStack(ObjHandler.matterBlock, 1, 0);

		// 目标输出：红物质
		ItemStack redMatter = new ItemStack(ObjHandler.matter, 1, 1);

		// 确保所有跨模组材料都成功获取，防止启动崩溃
		if (bigPearl != null && infinityBlock != null && draconicCore != null &&
			genesisShard != null && pureTear != null && everPool != null) {

			GameRegistry.addShapelessRecipe(redMatter,
				bigPearl, infinityBlock, draconicCore, genesisShard, pureTear, everPool,
				dmBlockRM, dmBlockRM, dmBlockRM
			);
		} else {
			System.err.println("[ProjectE-GTNH] 红物质(Red Matter) 无序配方注册失败！请检查所依赖的魔法/科技模组是否齐全。");
		}

		// 无尽催化剂 (Avaritia:Resource meta 5)
		Item avaritiaRes = GameRegistry.findItem("Avaritia", "Resource");
		ItemStack infCatalyst = avaritiaRes != null ? new ItemStack(avaritiaRes, 1, 5) : null;

		if (infCatalyst != null) {
			// 中心红物质，四周 8 个无尽催化剂，产出 2 个红物质
			GameRegistry.addRecipe(new ItemStack(ObjHandler.matter, 2, 1),
				"CCC",
				"CMC",
				"CCC",
				'C', infCatalyst,
				'M', redMatter.copy()
			);
		}

	}
	private static void registerAvaritiaRecipes() {

		// ==========================================
		// 转化桌 (Transmutation Table) - 9x9 终极合成
		// ==========================================

		// A: Avaritia 中子素态锭 (Avaritia:Resource meta 4)
		Item avaritiaResource = GameRegistry.findItem("Avaritia", "Resource");
		ItemStack avaritiaNeutronium = avaritiaResource != null ? new ItemStack(avaritiaResource, 1, 4) : null;

		// B: GregTech 中子锭 (gregtech:gt.metaitem.01 meta 11129)
		Item gtMetaItem = GameRegistry.findItem("gregtech", "gt.metaitem.01");
		ItemStack gtNeutronium = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 11129) : null;

		// C: 神秘时代 平衡碎片 (Thaumcraft:ItemShard meta 6)
		Item tcShard = GameRegistry.findItem("Thaumcraft", "ItemShard");
		ItemStack balancedShard = tcShard != null ? new ItemStack(tcShard, 1, 6) : null;

		// E: 神秘时代 原始魔力 (Thaumcraft:ItemResource meta 15)
		Item tcResource = GameRegistry.findItem("Thaumcraft", "ItemResource");
		ItemStack primalCharm = tcResource != null ? new ItemStack(tcResource, 1, 15) : null;

		// D: 贤者之石
		ItemStack philosStone = new ItemStack(ObjHandler.philosStone);

		// 确保所有外部物品都成功获取到了，防止配方注册崩溃
		if (avaritiaNeutronium != null && gtNeutronium != null && balancedShard != null && primalCharm != null) {
			ExtremeCraftingManager.getInstance().addRecipe(
				new ItemStack(ObjHandler.transmuteStone),
				"AABBBBBAA",
				"ABBBCBBBA",
				"BBBBBCBBB",
				"BBEBCBBBB",
				"BEBEDEBEB",
				"BBBBCBEBB",
				"BBBCBBBBB",
				"ABBBCBBBA",
				"AABBBBBAA",
				'A', avaritiaNeutronium,
				'B', gtNeutronium,
				'C', balancedShard,
				'E', primalCharm,
				'D', philosStone
			);
		} else {
			System.err.println("[ProjectE-GTNH] 转化桌(Transmutation Table) 9x9 配方注册失败！请检查 Avaritia, GregTech 或 Thaumcraft 是否正确加载。");
		}

		// ==========================================
		// 能量收集器 MK2 (Energy Collector MK2)
		// ==========================================
		ItemStack glowstone = new ItemStack(Blocks.glowstone);
		ItemStack dmBlock = new ItemStack(ObjHandler.matterBlock, 1, 0); // 暗物质块
		ItemStack collectorMK1 = new ItemStack(ObjHandler.energyCollector);

		ExtremeCraftingManager.getInstance().addRecipe(
			new ItemStack(ObjHandler.collectorMK2),
			"AAAABAAAA",
			"AAABBBAAA",
			"AAAABAAAA",
			"AAAAAAAAA",
			"AAACACAAA",
			"AAAAAAAAA",
			"AAAAAAAAA",
			"AAAAAAAAA",
			"AAAAAAAAA", // 补齐第9行
			'A', glowstone,
			'B', dmBlock,
			'C', collectorMK1
		);

		// ==========================================
		// 反物质继电器 MK2 (Anti-Matter Relay MK2)
		// ==========================================
		ItemStack obsidian = new ItemStack(Blocks.obsidian);
		ItemStack relayMK1 = new ItemStack(ObjHandler.relay);

		ExtremeCraftingManager.getInstance().addRecipe(
			new ItemStack(ObjHandler.relayMK2),
			"AAAABAAAA",
			"AAABBBAAA",
			"AAAABAAAA",
			"AAAAAAAAA",
			"AAACACAAA",
			"AAAAAAAAA",
			"AAAAAAAAA",
			"AAAAAAAAA",
			"AAAAAAAAA", // 补齐第9行
			'A', obsidian,
			'B', dmBlock,
			'C', relayMK1
		);

		// ==========================================
		// 便携式转化桌 (Transmutation Tablet) - 9x9 终极合成
		// ==========================================

		// A: 超时空金属块 (gregtech:gt.blockmetal9 meta 4)
		net.minecraft.block.Block gtBlockMetal9 = GameRegistry.findBlock("gregtech", "gt.blockmetal9");
		ItemStack spacetimeBlock = gtBlockMetal9 != null ? new ItemStack(gtBlockMetal9, 1, 4) : null;

		// B: 无尽块 (Avaritia:Resource_Block meta 1)
		net.minecraft.block.Block avaritiaResourceBlock = GameRegistry.findBlock("Avaritia", "Resource_Block");
		ItemStack infinityBlock = avaritiaResourceBlock != null ? new ItemStack(avaritiaResourceBlock, 1, 1) : null;

		// C & E: 时空锭 (gregtech:gt.metaitem.01 meta 11588)
		ItemStack spacetimeIngot = gtMetaItem != null ? new ItemStack(gtMetaItem, 1, 11588) : null;

		// D: 转化桌 (Transmutation Table)
		ItemStack transmuteTable = new ItemStack(ObjHandler.transmuteStone);

		// 确保所有神仙材料都存在
		if (spacetimeBlock != null && infinityBlock != null && spacetimeIngot != null) {
			ExtremeCraftingManager.getInstance().addRecipe(
				new ItemStack(ObjHandler.transmutationTablet),
				"AABBBBBAA",
				"ABBBCBBBA",
				"BBBBBCBBB",
				"BBEBCBBBB",
				"BEBEDEBEB",
				"BBBBCBEBB",
				"BBBCBBBBB",
				"ABBBCBBBA",
				"AABBBBBAA",
				'A', spacetimeBlock,
				'B', infinityBlock,
				'C', spacetimeIngot,
				'E', spacetimeIngot, // C 和 E 都是时空锭
				'D', transmuteTable
			);
		} else {
			System.err.println("[ProjectE-GTNH] 便携式转化桌(Transmutation Tablet) 9x9 配方注册失败！请检查 GT 或 Avaritia 是否正确加载。");
		}
	}

	private static void registerGregTechRecipes() {
		RecipeMap<?> cR = RecipeMaps.compressorRecipes;

		// 1. 炼金煤炭块
		GTValues.RA.stdBuilder()
			.itemInputs(new ItemStack(ObjHandler.fuels, 9, 0))
			.itemOutputs(new ItemStack(ObjHandler.fuelBlock, 1, 0))
			.duration(100)
			.eut(2048)
			.addTo(cR);

		// 2. 莫比乌斯燃料块
		GTValues.RA.stdBuilder()
			.itemInputs(new ItemStack(ObjHandler.fuels, 9, 1))
			.itemOutputs(new ItemStack(ObjHandler.fuelBlock, 1, 1))
			.duration(100)
			.eut(2048)
			.addTo(cR);

		// 3. 永恒燃料块
		GTValues.RA.stdBuilder()
			.itemInputs(new ItemStack(ObjHandler.fuels, 9, 2))
			.itemOutputs(new ItemStack(ObjHandler.fuelBlock, 1, 2))
			.duration(100)
			.eut(2048)
			.addTo(cR);

		// 4. 暗物质块
		GTValues.RA.stdBuilder()
			.itemInputs(new ItemStack(ObjHandler.matter, 9, 0))
			.itemOutputs(new ItemStack(ObjHandler.matterBlock, 1, 0))
			.duration(400)
			.eut(131072)
			.addTo(cR);

		// 5. 红物质块
		GTValues.RA.stdBuilder()
			.itemInputs(new ItemStack(ObjHandler.matter, 9, 1))
			.itemOutputs(new ItemStack(ObjHandler.matterBlock, 1, 1))
			.duration(1200)
			.eut(2097152)
			.addTo(cR);
	}
}
