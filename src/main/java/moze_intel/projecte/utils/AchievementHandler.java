package moze_intel.projecte.utils;

import com.google.common.collect.ImmutableList;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.AchievementPage;
import moze_intel.projecte.gameObjs.ObjHandler;

public final class AchievementHandler
{
	public static final Achievement PHIL_STONE = new Achievement("phil_stone", "phil_stone", 0, 2, ObjHandler.philosStone, null).initIndependentStat().registerStat();
	public static final Achievement ALCH_CHEST = new Achievement("alch_chest", "alch_chest", 0, -2, ObjHandler.alchChest, null).initIndependentStat().registerStat();
	public static final Achievement ALCH_BAG = new Achievement("alch_bag", "alch_bag", 0, -4, ObjHandler.alchBag, ALCH_CHEST).registerStat();
	public static final Achievement TRANSMUTATION = new Achievement("transmutation", "transmutation", 0, 0, ObjHandler.transmuteStone, PHIL_STONE).registerStat();
	public static final Achievement CONDENSER = new Achievement("condenser", "condenser", -2, -2, ObjHandler.condenser, ALCH_CHEST).setSpecial().registerStat();
	public static final Achievement COLLECTOR = new Achievement("collector", "collector", -2, -4, ObjHandler.energyCollector, CONDENSER).setSpecial().registerStat();
	public static final Achievement RELAY = new Achievement("relay", "relay", -4, -4, ObjHandler.relay, COLLECTOR).setSpecial().registerStat();
	public static final Achievement PORTABLE_TRANSMUTATION = new Achievement("portable_transmutation", "portable_transmutation", -2, 0, ObjHandler.transmutationTablet, TRANSMUTATION).setSpecial().registerStat();
	public static final Achievement DARK_MATTER = new Achievement("dark_matter", "dark_matter", 2, 0, new ItemStack(ObjHandler.matter, 1, 0), null).initIndependentStat().registerStat();
	public static final Achievement RED_MATTER = new Achievement("red_matter", "red_matter", 2, -2, new ItemStack(ObjHandler.matter, 1, 1), DARK_MATTER).setSpecial().registerStat();
	public static final Achievement DM_BLOCK = new Achievement("dm_block", "dm_block", 4, 0, new ItemStack(ObjHandler.matterBlock, 1, 0), DARK_MATTER).setSpecial().registerStat();
	public static final Achievement RM_BLOCK = new Achievement("rm_block", "rm_block", 4, -2, new ItemStack(ObjHandler.matterBlock, 1, 1), RED_MATTER).setSpecial().registerStat();
	public static final Achievement DM_FURNACE = new Achievement("dm_furnace", "dm_furnace", 6, 0, ObjHandler.dmFurnaceOff, DM_BLOCK).setSpecial().registerStat();
	public static final Achievement RM_FURNACE = new Achievement("rm_furnace", "rm_furnace", 6, -2, ObjHandler.rmFurnaceOff, RM_BLOCK).setSpecial().registerStat();
	public static final Achievement DM_PICK = new Achievement("dm_pick", "dm_pick", 2, 2, ObjHandler.dmPick, DARK_MATTER).registerStat();
	public static final Achievement RM_PICK = new Achievement("rm_pick", "rm_pick", 2, 4, ObjHandler.rmPick, DM_PICK).setSpecial().registerStat();
	public static final Achievement KLEIN_BASIC = new Achievement("klein", "klein", 0, 4, new ItemStack(ObjHandler.kleinStars, 1, 0), PHIL_STONE).registerStat();
	public static final Achievement KLEIN_MASTER = new Achievement("klein_big", "klein_big", -2, 4, new ItemStack(ObjHandler.kleinStars, 1, 5), KLEIN_BASIC).setSpecial().registerStat();

	public static final ImmutableList<Achievement> list = ImmutableList.of(
		PHIL_STONE, ALCH_CHEST, ALCH_BAG, TRANSMUTATION, CONDENSER,
		COLLECTOR, RELAY, PORTABLE_TRANSMUTATION, DARK_MATTER, RED_MATTER, DM_BLOCK,
		RM_BLOCK, DM_FURNACE, RM_FURNACE, DM_PICK, RM_PICK, KLEIN_BASIC, KLEIN_MASTER
	);

	public static void init() {
		AchievementPage.registerAchievementPage(new AchievementPage("ProjectE", list.toArray(new Achievement[0])));
	}

	public static Achievement getAchievementForItem(ItemStack stack)
	{
		// 补充 stack.getItem() == null 的校验，防止在获取伤害值或比较时抛出空指针异常
		if (stack == null || stack.getItem() == null)
			return null;

		for (Achievement ach : list) {
			ItemStack s = ach.theItemStack;
			if (ItemHelper.basicAreStacksEqual(s, stack))
				return ach;
		}

		return null;
	}
}
