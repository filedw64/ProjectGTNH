package moze_intel.projecte.integration.helpers;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.config.ProjectEConfig;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 把 ProjectE 恢复饱食度的物品接入 Nutrition 模组。
 * 所有 ca.wescook.nutrition.* 的引用都收在私有嵌套类 Internal 里，未安装 Nutrition 时本类被加载也不会解析那些类。
 */
public final class NutritionHelper {

	private static final String MODID = "nutrition";
	private static final int PEDESTAL_HUNGER_MULTIPLIER = 4;

	private NutritionHelper() {}

	public static boolean isEnabled() {
		return ProjectEConfig.enableNutritionIntegration && Loader.isModLoaded(MODID);
	}

	// 必须紧跟 player.getFoodStats().addStats(...) 之后调用：同一 tick 内 set/setAll/addAll 会抵消 Nutrition 的非食物饥饿惩罚
	public static void addNutrition(EntityPlayer player, int hungerRestored) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote || hungerRestored <= 0 || !isEnabled()) {
			return;
		}
		Internal.add(player, hungerRestored);
	}

	public static void addPedestalNutrition(EntityPlayer player, int hungerRestored) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote || hungerRestored <= 0 || !isEnabled()) {
			return;
		}
		Internal.add(player, hungerRestored * PEDESTAL_HUNGER_MULTIPLIER);
	}

	// 只在 isEnabled() 通过后才被触达，Nutrition 缺失时不会触发类解析
	private static final class Internal {

		private Internal() {}

		private static void add(EntityPlayer player, int hungerRestored) {
			float value = ca.wescook.nutrition.nutrients.NutrientUtils.getNutrientValue(hungerRestored, 1);
			ca.wescook.nutrition.api.NutritionManager.instance().addAll(player, value);
		}
	}
}
