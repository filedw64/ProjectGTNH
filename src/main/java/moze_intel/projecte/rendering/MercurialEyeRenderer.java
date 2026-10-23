package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import moze_intel.projecte.utils.BuildShape;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.RayTraceMath;
import moze_intel.projecte.utils.mercurial.TransformMatrix;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class MercurialEyeRenderer {

	// 逐块线框的块数上限：超过后只画整体外壳（8192 块逐帧线框约 20 万顶点）
	private static final int PER_BLOCK_PREVIEW_LIMIT = 2048;
	// 缓存仍有效时的重扫间隔（tick）：选区内的方块内容可能被外部改动，需要定期刷新
	private static final int PREVIEW_REBUILD_INTERVAL = 5;

	// setBlockBoundsBasedOnState 失败的累计次数，用于日志节流
	private static int blockBoundsFailures = 0;
	private final java.util.List<AxisAlignedBB> previewBoxes = new java.util.ArrayList<>();
	private String previewKey;
	private World previewWorld;
	private int previewCount;
	private long previewBuiltTick = Long.MIN_VALUE;

	@SubscribeEvent
	public void onWorldRenderLast(RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;
		if (player == null || player.worldObj == null) {
			clearPreview();
			return;
		}
		World world = player.worldObj;
		ItemStack held = player.getHeldItem();

		if (held == null || !(held.getItem() instanceof MercurialEye)) {
			clearPreview();
			return;
		}
		NBTTagCompound nbt = held.getTagCompound();
		if (nbt == null) {
			clearPreview();
			return;
		}

		double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
		GL11.glPushMatrix();
		try {
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glLineWidth(2.5F);

		Tessellator tess = Tessellator.instance;
		tess.setTranslation(-px, -py, -pz);

		MercurialEye eyeItem = (MercurialEye) held.getItem();
		int mode = eyeItem.getMode(held);

		// 新版的 复制/剪切/粘贴 模式
		if (mode == MercurialEye.COPY_PASTE_MODE || mode == MercurialEye.CUT_MOVE_MODE) {

			// 本分支不画选区缓存，立即释放上一次的方块线框，避免拿着旧缓存跨模式
			clearPreview();

			if (nbt.getBoolean("HasClipboard")) {
				MovingObjectPosition mop = mc.objectMouseOver;
				if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
					ForgeDirection side = ForgeDirection.getOrientation(mop.sideHit);
					int pasteX = mop.blockX + side.offsetX;
					int pasteY = mop.blockY + side.offsetY;
					int pasteZ = mop.blockZ + side.offsetZ;

					TransformMatrix matrix = new TransformMatrix();
					matrix.readFromNBT(nbt);

					int[] rotBounds = matrix.getRotatedBounds(
						nbt.getInteger("LocalMinX"), nbt.getInteger("LocalMinY"), nbt.getInteger("LocalMinZ"),
						nbt.getInteger("LocalMaxX"), nbt.getInteger("LocalMaxY"), nbt.getInteger("LocalMaxZ")
					);

					int rotSizeX = rotBounds[3] - rotBounds[0] + 1;
					int rotSizeY = rotBounds[4] - rotBounds[1] + 1;
					int rotSizeZ = rotBounds[5] - rotBounds[2] + 1;

					if (mode == MercurialEye.COPY_PASTE_MODE) GL11.glColor4f(0.2F, 0.8F, 1.0F, 0.6F);
					else GL11.glColor4f(0.8F, 0.2F, 1.0F, 0.6F);

					tess.startDrawing(GL11.GL_LINES);
					for (int step = 0; step < Math.min(64, matrix.stackCount); step++) {
						int offsetX = matrix.stackAxis.offsetX * rotSizeX * step;
						int offsetY = matrix.stackAxis.offsetY * rotSizeY * step;
						int offsetZ = matrix.stackAxis.offsetZ * rotSizeZ * step;

						AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
							pasteX + rotBounds[0] + offsetX,
							pasteY + rotBounds[1] + offsetY,
							pasteZ + rotBounds[2] + offsetZ,
							pasteX + rotBounds[3] + 1 + offsetX,
							pasteY + rotBounds[4] + 1 + offsetY,
							pasteZ + rotBounds[5] + 1 + offsetZ
						);
						drawWireframe(tess, bb);
					}
					tess.draw();
				}
			}
			else if (nbt.hasKey("AnchorX")) {
				MovingObjectPosition mop = mc.objectMouseOver;
				if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
					int anchorX = nbt.getInteger("AnchorX");
					int anchorY = nbt.getInteger("AnchorY");
					int anchorZ = nbt.getInteger("AnchorZ");

					int endX = mop.blockX; int endY = mop.blockY; int endZ = mop.blockZ;

					AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
						Math.min(anchorX, endX), Math.min(anchorY, endY), Math.min(anchorZ, endZ),
						Math.max(anchorX, endX) + 1, Math.max(anchorY, endY) + 1, Math.max(anchorZ, endZ) + 1
					);

					GL11.glColor4f(1.0F, 1.0F, 0.2F, 0.6F);
					tess.startDrawing(GL11.GL_LINES);
					drawWireframe(tess, bb);
					tess.draw();
				}
			}
		}

		// 恢复原版的 Normal / Transmutation 模式渲染
		else {
			if (!nbt.hasKey("AnchorX")) {
				clearPreview();
				return;
			}

			MovingObjectPosition mop = mc.objectMouseOver;
			int endX, endY, endZ;

			// 寻找目标点的光线追踪计算
			if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
				endX = mop.blockX; endY = mop.blockY; endZ = mop.blockZ;
			} else {
				// 与服务端共用同一套隔空选点（含未命中时退回前方 5 格）
				Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
				Vec3 lookVec = player.getLookVec();
				int[] target = RayTraceMath.getPlaneTarget(eyePos, lookVec, nbt.getInteger("AnchorX"), nbt.getInteger("AnchorY"), nbt.getInteger("AnchorZ"));
				endX = target[0]; endY = target[1]; endZ = target[2];
			}

			BuildShape shape = eyeItem.getBuildShape(held);
			ItemStack targetItem = nbt.hasKey("TargetItem") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("TargetItem")) : null;
			if (targetItem == null || !(targetItem.getItem() instanceof net.minecraft.item.ItemBlock)) {
				clearPreview();
				return;
			}

			Block newBlock = Block.getBlockFromItem(targetItem.getItem());
			int newMeta = targetItem.getItemDamage();
			int startX = nbt.getInteger("AnchorX"); int startY = nbt.getInteger("AnchorY"); int startZ = nbt.getInteger("AnchorZ");

			if (shape.getRequiredClicks() == 3) {
				if (!nbt.hasKey("MidX")) {
					endY = startY;
				} else {
					int midX = nbt.getInteger("MidX"); int midZ = nbt.getInteger("MidZ");
					startX = Math.min(startX, midX); startZ = Math.min(startZ, midZ);
					endX = Math.max(nbt.getInteger("AnchorX"), midX); endZ = Math.max(nbt.getInteger("AnchorZ"), midZ);
					startY = nbt.getInteger("AnchorY");
				}
			}

			int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
			int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
			int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

			// 用 long 计算方块个数，避免远距离锚点 int 溢出成负数后进入渲染循环；与服务端口径一致（含两端）
			long sizeX = (long) maxX - minX + 1;
			long sizeY = (long) maxY - minY + 1;
			long sizeZ = (long) maxZ - minZ + 1;
			if (sizeX <= MercurialEye.MAX_BUILD_VOLUME && sizeY <= MercurialEye.MAX_BUILD_VOLUME
				&& sizeZ <= MercurialEye.MAX_BUILD_VOLUME && sizeX * sizeY * sizeZ <= (long) MercurialEye.MAX_BUILD_VOLUME) {
				if (mode == MercurialEye.NORMAL_MODE) GL11.glColor4f(0.2F, 0.8F, 1.0F, 0.6F);
				else GL11.glColor4f(1.0F, 0.2F, 0.2F, 0.6F);

				String key = startX + ":" + startY + ":" + startZ
					+ ":" + endX + ":" + endY + ":" + endZ + ":" + shape.ordinal() + ":" + mode
					+ ":" + Block.getIdFromBlock(newBlock) + ":" + newMeta;
				long worldTime = world.getTotalWorldTime();
				boolean keyChanged = previewWorld != world || !key.equals(previewKey);
				// 同一 tick 内会渲染多帧，previewBuiltTick 保证节流窗口内只重建一次
				boolean rebuildDue = worldTime != previewBuiltTick && worldTime % PREVIEW_REBUILD_INTERVAL == 0L;
				if (keyChanged || rebuildDue) {
					previewKey = null;
					rebuildPreview(world, shape, startX, startY, startZ, endX, endY, endZ, newBlock, newMeta, mode);
					previewWorld = world;
					previewKey = key;
					previewBuiltTick = worldTime;
				}

				tess.startDrawing(GL11.GL_LINES);
				if (previewCount <= PER_BLOCK_PREVIEW_LIMIT) {
					for (AxisAlignedBB box : previewBoxes) drawWireframe(tess, box);
				} else {
					// 大选区只画整体外壳，不再逐块生成 AABB
					drawWireframe(tess, AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
				}
				tess.draw();
			}
		}

		} finally {
			Tessellator.instance.setTranslation(0, 0, 0);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private void clearPreview() {
		previewBoxes.clear();
		previewKey = null;
		previewWorld = null;
		previewCount = 0;
		previewBuiltTick = Long.MIN_VALUE;
	}

	/**
	 * 先数块数再决定渲染路径：超过阈值的选区不分配任何 AABB，由调用方画整体外壳。
	 */
	private void rebuildPreview(World world, BuildShape shape, int startX, int startY, int startZ, int endX, int endY, int endZ,
		Block newBlock, int newMeta, int mode) {
		previewBoxes.clear();
		int[] count = {0};
		shape.forEachBlock(startX, startY, startZ, endX, endY, endZ, (x, y, z) -> count[0]++);
		previewCount = count[0];
		if (previewCount > PER_BLOCK_PREVIEW_LIMIT) return;
		shape.forEachBlock(startX, startY, startZ, endX, endY, endZ,
			(x, y, z) -> renderBlockOutline(world, x, y, z, newBlock, newMeta, mode));
	}

	private void drawWireframe(Tessellator tess, AxisAlignedBB bb) {
		tess.addVertex(bb.minX, bb.minY, bb.minZ); tess.addVertex(bb.maxX, bb.minY, bb.minZ);
		tess.addVertex(bb.maxX, bb.minY, bb.minZ); tess.addVertex(bb.maxX, bb.minY, bb.maxZ);
		tess.addVertex(bb.maxX, bb.minY, bb.maxZ); tess.addVertex(bb.minX, bb.minY, bb.maxZ);
		tess.addVertex(bb.minX, bb.minY, bb.maxZ); tess.addVertex(bb.minX, bb.minY, bb.minZ);

		tess.addVertex(bb.minX, bb.maxY, bb.minZ); tess.addVertex(bb.maxX, bb.maxY, bb.minZ);
		tess.addVertex(bb.maxX, bb.maxY, bb.minZ); tess.addVertex(bb.maxX, bb.maxY, bb.maxZ);
		tess.addVertex(bb.maxX, bb.maxY, bb.maxZ); tess.addVertex(bb.minX, bb.maxY, bb.maxZ);
		tess.addVertex(bb.minX, bb.maxY, bb.maxZ); tess.addVertex(bb.minX, bb.maxY, bb.minZ);

		tess.addVertex(bb.minX, bb.minY, bb.minZ); tess.addVertex(bb.minX, bb.maxY, bb.minZ);
		tess.addVertex(bb.maxX, bb.minY, bb.minZ); tess.addVertex(bb.maxX, bb.maxY, bb.minZ);
		tess.addVertex(bb.maxX, bb.minY, bb.maxZ); tess.addVertex(bb.maxX, bb.maxY, bb.maxZ);
		tess.addVertex(bb.minX, bb.minY, bb.maxZ); tess.addVertex(bb.minX, bb.maxY, bb.maxZ);
	}

	private void renderBlockOutline(World world, int x, int y, int z, Block newBlock, int newMeta, int mode) {
		if (!world.blockExists(x, y, z)) return;
		Block oldBlock = world.getBlock(x, y, z);
		int oldMeta = world.getBlockMetadata(x, y, z);
		boolean willModify = false;

		if (mode == MercurialEye.NORMAL_MODE) {
			if (oldBlock.isReplaceable(world, x, y, z)) willModify = true;
		} else {
			if (oldBlock != Blocks.air && (oldBlock != newBlock || oldMeta != newMeta) && world.getTileEntity(x, y, z) == null
				&& net.minecraft.item.Item.getItemFromBlock(oldBlock) != null) {
				ItemStack oldStack = new ItemStack(oldBlock, 1, oldMeta);
				// 与服务端转化模式一致：无 EMC 或硬度 < 0（不可破坏）的方块不会被转化
				if (EMCHelper.doesItemHaveEmc(oldStack) && oldBlock.getBlockHardness(world, x, y, z) >= 0.0F) willModify = true;
			}
		}

		if (willModify) {
			double originalMinX = newBlock.getBlockBoundsMinX(), originalMinY = newBlock.getBlockBoundsMinY(), originalMinZ = newBlock.getBlockBoundsMinZ();
			double originalMaxX = newBlock.getBlockBoundsMaxX(), originalMaxY = newBlock.getBlockBoundsMaxY(), originalMaxZ = newBlock.getBlockBoundsMaxZ();
			try {
			try {
				newBlock.setBlockBoundsBasedOnState(world, x, y, z);
			} catch (Exception e) {
				// 节流：第 1 次以及之后每 200 次失败记录一条，避免逐帧刷屏
				newBlock.setBlockBounds(0, 0, 0, 1, 1, 1);
				blockBoundsFailures++;
				if (blockBoundsFailures % 200 == 1)
					PELogger.logDebug("Mercurial Eye 预览：%s 设置包围盒失败（%d, %d, %d），第 %d 次，%s: %s",
						newBlock.getUnlocalizedName(), x, y, z, blockBoundsFailures, e.getClass().getName(), e.getMessage());
			}
			double bMinX = newBlock.getBlockBoundsMinX(); double bMinY = newBlock.getBlockBoundsMinY(); double bMinZ = newBlock.getBlockBoundsMinZ();
			double bMaxX = newBlock.getBlockBoundsMaxX(); double bMaxY = newBlock.getBlockBoundsMaxY(); double bMaxZ = newBlock.getBlockBoundsMaxZ();
			previewBoxes.add(AxisAlignedBB.getBoundingBox(x + bMinX, y + bMinY, z + bMinZ, x + bMaxX, y + bMaxY, z + bMaxZ));
			} finally {
				newBlock.setBlockBounds((float) originalMinX, (float) originalMinY, (float) originalMinZ,
					(float) originalMaxX, (float) originalMaxY, (float) originalMaxZ);
			}
		}
	}
}
