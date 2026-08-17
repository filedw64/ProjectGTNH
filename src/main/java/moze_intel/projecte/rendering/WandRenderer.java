package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.gameObjs.items.ItemBuildersWand; // 替换为你的类
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class WandRenderer {

	@SubscribeEvent
	public void onDrawHighlight(DrawBlockHighlightEvent event) {
		EntityPlayer player = event.player;
		ItemStack heldItem = player.getHeldItem();

		if (heldItem == null || !(heldItem.getItem() instanceof ItemBuildersWand)) return;
		if (event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		int x = event.target.blockX;
		int y = event.target.blockY;
		int z = event.target.blockZ;
		int side = event.target.sideHit;

		List<ChunkCoordinates> blocks = WandHelper.getBlocksToPlace(player.worldObj, x, y, z, side, 1024);
		ForgeDirection dir = ForgeDirection.getOrientation(side);

		if (blocks.isEmpty()) return;

		// 渲染准备
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.4F); // 白色半透明
		GL11.glLineWidth(2.0F);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(false);

		double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		for (ChunkCoordinates coord : blocks) {
			int placeX = coord.posX + dir.offsetX;
			int placeY = coord.posY + dir.offsetY;
			int placeZ = coord.posZ + dir.offsetZ;

			AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(placeX, placeY, placeZ, placeX + 1, placeY + 1, placeZ + 1)
				.offset(-playerX, -playerY, -playerZ);

			RenderGlobal.drawOutlinedBoundingBox(aabb, -1); // 1.7.10原版画框方法
		}

		// 渲染收尾
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);

		event.setCanceled(true); // 取消原版的单个黑色高亮框
	}
}
