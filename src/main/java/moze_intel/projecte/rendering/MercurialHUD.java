package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MercurialHUD {
	private static String currentMessage = "";
	private static int messageTimer = 0;

	public static void showMessage(String msg, int time) {
		currentMessage = msg;
		messageTimer = time;
	}

	@SubscribeEvent
	public void onRenderHUD(RenderGameOverlayEvent.Post event) {
		if (event.type == RenderGameOverlayEvent.ElementType.TEXT && messageTimer > 0) {
			Minecraft mc = Minecraft.getMinecraft();
			ScaledResolution res = event.resolution;

			// 计算淡出 Alpha 效果 (最后 20 tick 逐渐变透明)
			int alpha = (int) (Math.min(messageTimer, 20) / 20.0f * 255);
			int color = 0x00FFFF | (alpha << 24); // 默认青色
			if (currentMessage.contains("Warning") || currentMessage.contains("too large")) {
				color = 0xFF5555 | (alpha << 24); // 警告变红
			}

			int width = mc.fontRenderer.getStringWidth(currentMessage);
			int x = (res.getScaledWidth() - width) / 2;
			int y = res.getScaledHeight() - 65; // 位于快捷栏上方

			GL11.glPushMatrix();
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(770, 771, 1, 0);
			mc.fontRenderer.drawStringWithShadow(currentMessage, x, y, color);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glPopMatrix();
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END && messageTimer > 0) {
			messageTimer--;
		}
	}
}
