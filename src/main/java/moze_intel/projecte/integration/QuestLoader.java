package moze_intel.projecte.integration;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.nbt.NBTTagCompound;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class QuestLoader {

	private static final Gson GSON = new Gson();

	@SubscribeEvent
	@SuppressWarnings("deprecation")
	public void onDatabaseLoad(DatabaseEvent.Load event) {
		if ("false".equalsIgnoreCase(ProjectEConfig.questMode)) {
			return;
		}

		IQuestDatabase questDB = QuestingAPI.getAPI(ApiReference.QUEST_DB);
		IQuestLineDatabase lineDB = QuestingAPI.getAPI(ApiReference.LINE_DB);

		if (questDB == null || lineDB == null) {
			PELogger.logWarn("BetterQuesting API not found. Skipping ProjectE quest injection.");
			return;
		}

		// 动态判断语言并选择数据文件夹
		String questDir = isChinese() ? "quest_zh" : "quest";
		PELogger.logInfo("Injecting ProjectE Quests natively using language folder: " + questDir);

		injectQuests(questDB, lineDB, questDir);
	}

	/**
	 * 判断当前环境是否为中文 (兼容单人客户端与独立服务端)
	 */
	private boolean isChinese() {
		try {
			// 如果是客户端，通过反射安全获取 Minecraft 的语言设置
			if (cpw.mods.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) {
				Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
				Object mcInstance = mcClass.getMethod("getMinecraft").invoke(null);
				Object gameSettings = mcClass.getField("gameSettings").get(mcInstance);
				String lang = (String) gameSettings.getClass().getField("language").get(gameSettings);
				return lang != null && lang.toLowerCase().startsWith("zh");
			}
		} catch (Throwable t) {
			// 反射失败或处于独立服务端环境，忽略错误并降级到系统语言
		}
		// 服务端降级判断：读取操作系统默认语言
		return java.util.Locale.getDefault().getLanguage().toLowerCase().startsWith("zh");
	}

	private void injectQuests(IQuestDatabase questDB, IQuestLineDatabase lineDB, String questDir) {
		// 1. 贤者之石阶段 (The Genesis of Alch)
		loadChapter(questDB, lineDB, questDir, "TheGenesisofAlch-COUnjTnGSfCQDummrRQHag==", new String[]{
			"AeternalisFuel-UX7OyYewTouSY16Y8alOnw==.json",
			"AlchemicalBag-jIZKgKxXTJSHz4CYTrD2Cg==.json",
			"AlchemicalChest-h7YQcliQRDGbL9sScH1jzA==.json",
			"AlchemicalCoal-oaMvto46TGmAWVIq7apDwg==.json",
			"AntiMatterRelayM-FaaSJr6mS6WD2uhjblMSdw==.json",
			"DestructionCatal-f7Tg_pv5S_6FtgBr-ouM6w==.json",
			"EnergyCollectorM-B9AhxCymRWSV86RgFLV0Yg==.json",
			"EnergyCondenser-erObzjE5Q2uGxSqJE24eoQ==.json",
			"HighCovalenceDus-OAZPCqLeSdaETEcqi_ZZdw==.json",
			"InterdictionTorc-pqcS4VjJQlqnK0RgOLvoUA==.json",
			"KleinStarEin-yfsVbZ5DTeWrK71keIrmhQ==.json",
			"KleinStarOmega-4JZk6zIdTeSW4pi7pod3XA==.json",
			"LowCovalenceDust-me8b_WtKQCuWFbYqFXu0aw==.json",
			"MediumCovalenceD-1WHfUDqISY6FLNeZHriyIw==.json",
			"MobiusFuel-rKQouW-xSiS72PG74gt7qg==.json",
			"NovaCataclysm-4NNlbQOOTPebkz-8NKKinQ==.json",
			"NovaCatalyst-nukJ2PdASNKivetbBF_uwg==.json",
			"PhilosophersSton-4_v8praNQxeWl9mxKF0lyQ==.json",
			"ProjectVoidReena-F42qGTYPTN2a_pixMR8Kmg==.json",
			"TransmutationTab-KU37erN0SVCWKtp9uvLj8Q==.json"
		});

		// 2. 暗物质阶段 (The Mysterious Dark Matter)
		loadChapter(questDB, lineDB, questDir, "TheMysteriousDar-0OWMJxgqQUGwjVILgtXx4Q==", new String[]{
			"AntiMatterRelayM-4_R6PhrhSAqpS3qTu3QOUA==.json",
			"ArchangelsSmite--3qD0EXDSfOe18QpM7MqcA==.json",
			"BlackHoleBand-06s2ZveVTk2FXX6yJ3GHoA==.json",
			"CatalyticLens-JKLLDdGTTbOlnmbk6JbQVg==.json",
			"DarkMatterArmor-MNCdpTs-TYGSlvp5Imo--g==.json",
			"DarkMatterBlock-lwQCWYTXS1GM1-bQKHTd5Q==.json",
			"DarkMatterFurnac-XqZX4OJqQDK8CV9jTJgeIQ==.json",
			"DarkMatterSword-7TTW55SgRLuVAAgZAdD54g==.json",
			"DarkMatterTools-ee93-OHfTr6GFXufUG1E5Q==.json",
			"EnergyCollectorM-Wu7mS9mcT8aryHDykYZIQA==.json",
			"EvertideAmulet-EUgOtdGKSXS-_LXsPV8h8w==.json",
			"HarvestGoddessBa-TtWTHWZvRWWC83sH2oMRJA==.json",
			"HyperkineticLens-tpIydatfSdu-BovmWuLj4A==.json",
			"IgnitionRing-MUA5asUWShCLjwTSwiyy2Q==.json",
			"IronBand-6VCitIo7QmCdwYkM61WjfQ==.json",
			"MysteriousDarkMa-Wd3LDewKSfyImU0ngJwhGA==.json",
			"RepairTalisman-3pGCPIKXRLOoX4VBl-cf_A==.json",
			"SwiftwolfsRendin-16XRxkzUTj69Pl-wQ7Hl4w==.json",
			"TransmutationTab-diGNJtYKRG2hSczCsKY7ZA==.json",
			"VolcaniteAmulet-JOg9Na-wTdW0n170mLhDaA==.json",
			"ZeroRing-u1lOoZsPTaeQTyC6IhqY8g==.json"
		});

		// 3. 红物质阶段 (The Searing Red Matter)
		loadChapter(questDB, lineDB, questDir, "TheSearingRedMat-a6A8yGIISqG5LRCyMtTV3g==", new String[]{
			"AnInfinityGemNoM-Yc1DdvXiS_61kyer4oS3Gg==.json",
			"AntiMatterRelayM-wId9hLb-SxK7WUckZGTVYg==.json",
			"ArcaneRing-NByfLWXPS3ixHoZxK-qJbw==.json",
			"DarkMatterPedest-9v29WgeHSU-jlHXGn3seKA==.json",
			"EnergyCollectorM-5v4H7FACSz26tTEcNS_2IQ==.json",
			"EnergyCondenserM-R4n-Xk3bRry7GroJnqTjqA==.json",
			"GodStoneImeanBod-bWtYzmirSCWiMN3ePKoRJQ==.json",
			"LifeStone-sc5RWcO8R8efT87TgRCnEA==.json",
			"MercurialEye-2p0qeaD7RKW7Zq5alh99-w==.json",
			"Preparationsfort-L-LeYVZDTAmSqEnSbL6TIg==.json",
			"Preparationsfort-ZBEHMqA0S4qJxAIqSMieSw==.json",
			"Preparationsfort-spMfbdiSQv60nR9iw4qHfg==.json",
			"RedMatterBlock-zAWO9t_jSlKdg52DNaXN3Q==.json",
			"RedMatterFurnace-j3H0atTNRGGHb8u0yXjcKA==.json",
			"RedMatterKatar-sKRAzuPoTiytxOVdyInhJQ==.json",
			"RedMatterMorning-ybHkWlsZSK6qC-IyTrm8zA==.json",
			"SearingRedMatter-GreDgxUAT4aoGImoq91Trg==.json",
			"SoleSurvivorImea-bUty5kzYSk-OZb0uP0aVgQ==.json",
			"TheGemArmorSet-nkN0VRniT8yIv-lSGG519g==.json",
			"TomeofKnowledge-wHkQv8ORQGK4qw7G-oFu8A==.json",
			"VoidRing-l8haQpVLS4-pjvN0TpBJMw==.json"
		});
	}

	private void loadChapter(IQuestDatabase questDB, IQuestLineDatabase lineDB, String questDir, String folderName, String[] files) {
		int fSplitIdx = folderName.indexOf('-');
		if (fSplitIdx == -1) return;

		String chapterName = folderName.substring(0, fSplitIdx);
		UUID chapterUuid = UuidConverter.decodeUuid(folderName.substring(fSplitIdx + 1));

		IQuestLine chapter = lineDB.get(chapterUuid);
		if (chapter == null) {
			chapter = lineDB.createNew(chapterUuid);
		}

		try {
			String linePropPath = "/assets/projecte/questline/" + folderName + "/QuestLine.json";
			InputStream linePropIs = getClass().getResourceAsStream(linePropPath);
			if (linePropIs != null) {
				JsonObject json = GSON.fromJson(new InputStreamReader(linePropIs, StandardCharsets.UTF_8), JsonObject.class);
				NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
				chapter.readFromNBT(nbt);
			} else {
				chapter.setProperty(NativeProps.NAME, chapterName);
			}
		} catch (Exception e) {
			PELogger.logWarn("Failed to load QuestLine properties for: " + folderName);
		}

		for (String fileName : files) {
			try {
				int splitIdx = fileName.indexOf('-');
				if (splitIdx == -1) continue;
				String uuidStr = fileName.substring(splitIdx + 1, fileName.length() - 5);
				UUID questUuid = UuidConverter.decodeUuid(uuidStr);

				String questPath = "/assets/projecte/" + questDir + "/" + folderName + "/" + fileName;
				InputStream questIs = getClass().getResourceAsStream(questPath);
				if (questIs != null) {
					JsonObject json = GSON.fromJson(new InputStreamReader(questIs, StandardCharsets.UTF_8), JsonObject.class);
					NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);

					IQuest quest = questDB.get(questUuid);
					if (quest == null) {
						quest = questDB.createNew(questUuid);
					}
					quest.readFromNBT(nbt);
				} else {
					PELogger.logWarn("Missing quest data JSON: " + questPath);
				}

				String layoutPath = "/assets/projecte/questline/" + folderName + "/" + fileName;
				InputStream layoutIs = getClass().getResourceAsStream(layoutPath);
				if (layoutIs != null) {
					JsonObject json = GSON.fromJson(new InputStreamReader(layoutIs, StandardCharsets.UTF_8), JsonObject.class);
					NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);

					IQuestLineEntry entry = chapter.get(questUuid);
					if (entry == null) {
						entry = chapter.createNew(questUuid);
					}
					entry.readFromNBT(nbt);
				} else {
					PELogger.logWarn("Missing quest layout JSON: " + layoutPath);
				}
			} catch (Exception e) {
				PELogger.logWarn("Failed to load ProjectE quest file: " + fileName);
				e.printStackTrace();
			}
		}
	}
}
