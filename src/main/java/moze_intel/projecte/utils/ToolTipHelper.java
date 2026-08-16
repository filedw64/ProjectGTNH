package moze_intel.projecte.utils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.EnumChatFormatting;

public final class ToolTipHelper {
	private final static EnumChatFormatting[] rainbowColors = {
		EnumChatFormatting.RED, EnumChatFormatting.GOLD, EnumChatFormatting.YELLOW,
		EnumChatFormatting.GREEN, EnumChatFormatting.AQUA, EnumChatFormatting.BLUE,
		EnumChatFormatting.LIGHT_PURPLE
	};

	@SideOnly(Side.CLIENT)
	public static String getRainbowGlitch(final int length) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++)
			sb.append(rainbowColors[i % 7]).append(EnumChatFormatting.OBFUSCATED).append("X"); // 交替颜色 + 混淆乱码 + 占位符
		return sb.toString();
	}
}
