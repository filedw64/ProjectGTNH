package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.network.packets.MercurialNotifyPKT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

@SideOnly(Side.CLIENT)
public class MercurialHUD {
	private static final int COLOR_INFO = 0x00FFFF;
	private static final int COLOR_WARN = 0xFFAA00;
	private static final int COLOR_ERROR = 0xFF5555;

	// 同时最多显示的行数：超出后挤掉最早的一条，连续操作也不会把屏幕铺满
	private static final int MAX_LINES = 3;
	// 1.7.10 字体高 9，留 2 像素行距
	private static final int LINE_HEIGHT = 11;
	private static final int BASE_OFFSET = 65;

	private static final class Line {
		final String text;
		final int level;
		int ticks;

		Line(String text, int level, int ticks) {
			this.text = text;
			this.level = level;
			this.ticks = ticks;
		}
	}

	// 最新的在队尾，渲染时从队尾往上叠
	private static final Deque<Line> lines = new ArrayDeque<>();

	public static void showMessage(String text, int level, int ticks) {
		if (text == null || text.isEmpty()) {
			return;
		}

		// 同一内容还没消失时只续期不重复入队：连点同一份剪贴板不该刷出多行一样的字
		for (Line line : lines) {
			if (line.text.equals(text)) {
				if (line.ticks < ticks) {
					line.ticks = ticks;
				}
				return;
			}
		}

		if (lines.size() >= MAX_LINES) {
			lines.removeFirst();
		}
		lines.addLast(new Line(text, level, ticks));
	}

	private static int colorFor(int level, int alpha) {
		int rgb;
		switch (level) {
			case MercurialNotifyPKT.LEVEL_ERROR:
				rgb = COLOR_ERROR;
				break;
			case MercurialNotifyPKT.LEVEL_WARN:
				rgb = COLOR_WARN;
				break;
			default:
				rgb = COLOR_INFO;
				break;
		}
		return rgb | (alpha << 24);
	}

	@SubscribeEvent
	public void onRenderHUD(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT || lines.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution res = event.resolution;
		int y = res.getScaledHeight() - BASE_OFFSET;

		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);

		Iterator<Line> it = lines.descendingIterator();
		while (it.hasNext()) {
			Line line = it.next();
			// 计算淡出 Alpha 效果 (最后 20 tick 逐渐变透明)
			int alpha = (int) (Math.min(line.ticks, 20) / 20.0f * 255);
			int width = mc.fontRenderer.getStringWidth(line.text);
			mc.fontRenderer.drawStringWithShadow(line.text, (res.getScaledWidth() - width) / 2, y, colorFor(line.level, alpha));
			y -= LINE_HEIGHT;
		}

		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || lines.isEmpty()) {
			return;
		}
		Iterator<Line> it = lines.iterator();
		while (it.hasNext()) {
			Line line = it.next();
			if (--line.ticks <= 0) {
				it.remove();
			}
		}
	}
}
