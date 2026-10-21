package moze_intel.projecte.utils;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 物质管道 / 物质接口的颜色（频率信道）工具。
 * 全项目统一使用"羊毛色序"：0 白、1 橙、2 品红、3 浅蓝、4 黄、5 黄绿、6 粉、7 灰、
 * 8 浅灰、9 青、10 紫、11 蓝、12 棕、13 绿、14 红、15 黑。
 * 方块 metadata、贴图调色板（tools/gen_pipe_textures.mjs）与语言键 pe.matter.color.* 都按这套顺序。
 */
public final class MatterColorHelper
{
	/** 颜色总数（metadata 0..15） */
	public static final int COLOR_COUNT = 16;

	/** 羊毛色序 → 矿石字典染料名（dyeWhite = 0 … dyeBlack = 15） */
	private static final String[] DYE_ORE_NAMES = new String[] {"dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue", "dyeYellow", "dyeLime", "dyePink", "dyeGray", "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"};

	/**
	 * 羊毛色序 → Items.dye 的 damage。两套编号语义相反，千万别直接混用：
	 * Items.dye 的 damage 0 是墨囊（黑色染料）、damage 15 是骨粉（白色染料），
	 * 而颜色索引 0 是白、15 是黑，所以这里 0 号位存 15、15 号位存 0。
	 * 写成全表而不是 15 - color，是为了能一眼对照原版染料。
	 */
	private static final int[] DYE_DAMAGE_BY_COLOR = new int[] {
		15, // 0  白  <- 骨粉
		14, // 1  橙
		13, // 2  品红
		12, // 3  浅蓝
		11, // 4  黄
		10, // 5  黄绿
		9,  // 6  粉
		8,  // 7  灰
		7,  // 8  浅灰
		6,  // 9  青
		5,  // 10 紫
		4,  // 11 蓝
		3,  // 12 棕  <- 可可豆
		2,  // 13 绿
		1,  // 14 红
		0   // 15 黑  <- 墨囊
	};

	private MatterColorHelper()
	{
	}

	/** 颜色索引 → 原版染料 ItemStack；索引非法时返回 null */
	public static ItemStack getDyeStack(int color)
	{
		if (color < 0 || color >= DYE_DAMAGE_BY_COLOR.length)
			return null;

		return new ItemStack(Items.dye, 1, DYE_DAMAGE_BY_COLOR[color]);
	}

	/**
	 * 手持物品对应的颜色索引，不是染料时返回 -1。
	 * 先查矿石字典：其他模组的染料只在这里能被认出来（它们的 damage 编号与本模组的色序无关）；
	 * 矿石字典认不出来时，再用原版 Items.dye 的 damage 表兜底。
	 */
	public static int getDyeColor(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null)
			return -1;

		int[] oreIds = OreDictionary.getOreIDs(stack);

		for (int i = 0; i < oreIds.length; i++)
		{
			String oreName = OreDictionary.getOreName(oreIds[i]);

			for (int color = 0; color < DYE_ORE_NAMES.length; color++)
			{
				if (DYE_ORE_NAMES[color].equals(oreName))
					return color;
			}
		}

		if (stack.getItem() == Items.dye)
		{
			int damage = stack.getItemDamage();

			for (int color = 0; color < DYE_DAMAGE_BY_COLOR.length; color++)
			{
				if (DYE_DAMAGE_BY_COLOR[color] == damage)
					return color;
			}
		}

		return -1;
	}

	/**
	 * 『方块名（颜色名）』。括号与空格由语言文件的 pe.matter.pipe.named 决定，
	 * 管道与接口共用同一个格式串，代码里不出现任何括号字面量。
	 */
	public static String getColorizedName(String baseName, int color)
	{
		String colorName = StatCollector.translateToLocal("pe.matter.color." + (color & 15));
		return StatCollector.translateToLocalFormatted("pe.matter.pipe.named", baseName, colorName);
	}
}
