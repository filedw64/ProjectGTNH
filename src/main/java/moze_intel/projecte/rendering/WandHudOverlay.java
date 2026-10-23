package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class WandHudOverlay {

	// 与 MercurialHUD 一致：TEXT 阶段画完后叠加，不会被其它 HUD 元素覆盖
	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;

		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;

		// 开 GUI 时不盖上界面；未瞄准方块时隐藏（瞄准框事件此时不会触发，快照会过期）
		if (mc.currentScreen != null || player == null) return;
		if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		ItemStack heldItem = player.getHeldItem();

		if (heldItem == null || !(heldItem.getItem() instanceof BuildersWand)) return;

		// 直接复用 WandRenderer 已算好的计划，这里不再跑一次 BFS
		WandHelper.PlacementPlan plan = WandRenderer.getLastPlan();

		if (plan == null) return;

		int blocks = WandRenderer.getLastAffordable();
		String line = StatCollector.translateToLocal("pe.builderswand.hud.blocks") + " " + blocks;

		if (plan.emcPerBlock > 0) {
			line += " / " + StatCollector.translateToLocal("pe.builderswand.gui.cost") + " "
				+ WandRenderer.formatEmc(WandRenderer.getLastEmcCost()) + " "
				+ StatCollector.translateToLocal("pe.builderswand.hud.emc");
		}

		ScaledResolution res = event.resolution;
		int y = res.getScaledHeight() - 20;

		GL11.glPushMatrix();
		mc.fontRenderer.drawStringWithShadow(line, 4, y, 0xFFFFFF);
		GL11.glPopMatrix();
	}
}
