package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.gameObjs.items.ItemBuildersWand;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class WandRenderer {

	@SubscribeEvent
	public void onDrawHighlight(DrawBlockHighlightEvent event) {
		EntityPlayer player = event.player;
		ItemStack heldItem = player.getHeldItem();

		if (heldItem == null || !(heldItem.getItem() instanceof ItemBuildersWand)) return;
		if (event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		List<ChunkCoordinates> blocks = WandHelper.getBlocksToPlace(player.worldObj,
			event.target.blockX, event.target.blockY, event.target.blockZ, event.target.sideHit, 1024);

		if (blocks.isEmpty()) return;

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glLineWidth(2.0F);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(false);

		double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		double maxFadeDistSq = 32.0 * 32.0;

		for (ChunkCoordinates coord : blocks) {
			double dx = (coord.posX + 0.5) - playerX;
			double dy = (coord.posY + 0.5) - playerY;
			double dz = (coord.posZ + 0.5) - playerZ;
			double distSq = dx * dx + dy * dy + dz * dz;

			// 【优化】直接剔除超出距离的方块渲染，节省 OpenGL DrawCall 绘制开销
			if (distSq > maxFadeDistSq) {
				continue;
			}

			float alpha = (float) (0.5F - (distSq / maxFadeDistSq) * 0.45F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);

			AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(coord.posX, coord.posY, coord.posZ, coord.posX + 1, coord.posY + 1, coord.posZ + 1)
				.offset(-playerX, -playerY, -playerZ);

			RenderGlobal.drawOutlinedBoundingBox(aabb, -1);
		}

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);

		event.setCanceled(true);
	}
}
