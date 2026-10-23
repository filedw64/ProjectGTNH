package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class WandRenderer {

	// 同一瞄准目标最多每 5 tick（20 tps ≈ 100ms）重算一次
	private static final long CACHE_TTL_MS = 100L;
	private static final double MAX_FADE_DIST_SQ = 32.0 * 32.0;

	private final List<ChunkCoordinates> cachedBlocks = new ArrayList<>();
	private boolean cacheValid = false;
	private World cacheWorld;
	private int cacheX;
	private int cacheY;
	private int cacheZ;
	private int cacheSide;
	private byte cacheMode;
	private long cacheTime;

	@SubscribeEvent
	public void onDrawHighlight(DrawBlockHighlightEvent event) {
		EntityPlayer player = event.player;
		ItemStack heldItem = player.getHeldItem();

		if (heldItem == null || !(heldItem.getItem() instanceof BuildersWand)) return;
		if (event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		List<ChunkCoordinates> blocks = getBlocksForRender(player, heldItem, (BuildersWand) heldItem.getItem(), event.target);

		if (blocks.isEmpty()) return;

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glLineWidth(2.0F);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(false);

		double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		for (ChunkCoordinates coord : blocks) {
			double dx = (coord.posX + 0.5) - playerX;
			double dy = (coord.posY + 0.5) - playerY;
			double dz = (coord.posZ + 0.5) - playerZ;
			double distSq = dx * dx + dy * dy + dz * dz;

			// 【优化】直接剔除超出距离的方块渲染，节省 OpenGL DrawCall 绘制开销
			if (distSq > MAX_FADE_DIST_SQ) {
				continue;
			}

			float alpha = (float) (0.5F - (distSq / MAX_FADE_DIST_SQ) * 0.45F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);

			AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(coord.posX, coord.posY, coord.posZ, coord.posX + 1, coord.posY + 1, coord.posZ + 1)
				.offset(-playerX, -playerY, -playerZ);

			RenderGlobal.drawOutlinedBoundingBox(aabb, -1);
		}

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);

		// 保留原版方块瞄准框：不取消原事件
	}

	/**
	 * 【性能】按"世界 + 坐标 + 面 + 模式"做 key 缓存，只有 key 变化或超过 5 tick 才重算形状。
	 * 渲染循环本身只读缓存，不触碰世界。
	 */
	private List<ChunkCoordinates> getBlocksForRender(EntityPlayer player, ItemStack heldItem, BuildersWand wand, MovingObjectPosition target) {
		World world = player.worldObj;
		byte mode = wand.getMode(heldItem);
		long now = System.currentTimeMillis();

		boolean sameKey = this.cacheValid
			&& this.cacheWorld == world
			&& this.cacheX == target.blockX
			&& this.cacheY == target.blockY
			&& this.cacheZ == target.blockZ
			&& this.cacheSide == target.sideHit
			&& this.cacheMode == mode;

		if (!sameKey || now - this.cacheTime > CACHE_TTL_MS) {
			this.cachedBlocks.clear();
			this.cachedBlocks.addAll(WandHelper.getBlocksToPlace(world, target.blockX, target.blockY, target.blockZ,
				target.sideHit, mode, ProjectEConfig.buildersWandMaxBlocks));

			this.cacheWorld = world;
			this.cacheX = target.blockX;
			this.cacheY = target.blockY;
			this.cacheZ = target.blockZ;
			this.cacheSide = target.sideHit;
			this.cacheMode = mode;
			this.cacheValid = true;
			this.cacheTime = now;
		}

		return this.cachedBlocks;
	}
}
