package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import moze_intel.projecte.utils.BuildShape;
import moze_intel.projecte.utils.EMCHelper;
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

	@SubscribeEvent
	public void onWorldRenderLast(RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;
		World world = player.worldObj;
		ItemStack held = player.getHeldItem();

		if (held == null || !(held.getItem() instanceof MercurialEye)) return;
		NBTTagCompound nbt = held.getTagCompound();
		if (nbt == null) return;

		double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		GL11.glPushMatrix();
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
					for (int step = 0; step < matrix.stackCount; step++) {
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
				// 原版代码中，如果连第一个锚点都没点，什么都不画
				// (清理残留的 OpenGL 状态)
				tess.setTranslation(0, 0, 0);
				GL11.glDepthMask(true);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glPopMatrix();
				return;
			}

			MovingObjectPosition mop = mc.objectMouseOver;
			int endX, endY, endZ;

			// 寻找目标点的光线追踪计算
			if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
				endX = mop.blockX; endY = mop.blockY; endZ = mop.blockZ;
			} else {
				Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
				Vec3 lookVec = player.getLookVec();
				int anchorX = nbt.getInteger("AnchorX");
				int anchorY = nbt.getInteger("AnchorY");
				int anchorZ = nbt.getInteger("AnchorZ");
				double absX = Math.abs(lookVec.xCoord); double absY = Math.abs(lookVec.yCoord); double absZ = Math.abs(lookVec.zCoord);
				RayTraceMath.Plane plane; double planeVal;

				if (absX > absY && absX > absZ) { plane = RayTraceMath.Plane.X; planeVal = anchorX; }
				else if (absY > absX && absY > absZ) { plane = RayTraceMath.Plane.Y; planeVal = anchorY; }
				else { plane = RayTraceMath.Plane.Z; planeVal = anchorZ; }

				Vec3 hit = RayTraceMath.getIntersection(eyePos, lookVec, plane, planeVal);
				if (hit != null) {
					endX = (int) Math.round(hit.xCoord);
					endY = (int) Math.round(hit.yCoord);
					endZ = (int) Math.round(hit.zCoord);
				} else {
					endX = (int) Math.round(eyePos.xCoord + lookVec.xCoord * 5);
					endY = (int) Math.round(eyePos.yCoord + lookVec.yCoord * 5);
					endZ = (int) Math.round(eyePos.zCoord + lookVec.zCoord * 5);
				}
			}

			BuildShape shape = eyeItem.getBuildShape(held);
			ItemStack targetItem = nbt.hasKey("TargetItem") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("TargetItem")) : null;
			if (targetItem == null) {
				// 清理并退出
				tess.setTranslation(0, 0, 0);
				GL11.glDepthMask(true);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glPopMatrix();
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
					startY = nbt.getInteger("AnchorY"); endY = mop != null ? mop.blockY : endY;
				}
			}

			int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
			int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
			int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

			if ((maxX - minX) * (maxY - minY) * (maxZ - minZ) <= 8192) {
				if (mode == MercurialEye.NORMAL_MODE) GL11.glColor4f(0.2F, 0.8F, 1.0F, 0.6F);
				else GL11.glColor4f(1.0F, 0.2F, 0.2F, 0.6F);

				tess.startDrawing(GL11.GL_LINES);

				if (shape == BuildShape.LINE) {
					int dist = (int) Math.ceil(Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2) + Math.pow(endZ - startZ, 2)));
					for (int i = 0; i <= dist; i++) {
						double t = dist == 0 ? 0 : (double) i / dist;
						int x = (int) Math.round(startX + (endX - startX) * t);
						int y = (int) Math.round(startY + (endY - startY) * t);
						int z = (int) Math.round(startZ + (endZ - startZ) * t);
						renderBlockOutline(world, x, y, z, newBlock, newMeta, mode, tess);
					}
				} else {
					for (int x = minX; x <= maxX; x++) {
						for (int y = minY; y <= maxY; y++) {
							for (int z = minZ; z <= maxZ; z++) {
								if (!shape.isBlockInShape(x, y, z, startX, startY, startZ, endX, endY, endZ)) continue;
								renderBlockOutline(world, x, y, z, newBlock, newMeta, mode, tess);
							}
						}
					}
				}
				tess.draw();
			}
		}

		tess.setTranslation(0, 0, 0);
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
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

	private void renderBlockOutline(World world, int x, int y, int z, Block newBlock, int newMeta, int mode, Tessellator tess) {
		Block oldBlock = world.getBlock(x, y, z);
		int oldMeta = world.getBlockMetadata(x, y, z);
		boolean willModify = false;

		if (mode == MercurialEye.NORMAL_MODE) {
			if (oldBlock.isReplaceable(world, x, y, z)) willModify = true;
		} else {
			if (oldBlock != Blocks.air && oldBlock != newBlock && world.getTileEntity(x, y, z) == null) {
				ItemStack oldStack = new ItemStack(oldBlock, 1, oldMeta);
				if (EMCHelper.doesItemHaveEmc(oldStack)) willModify = true;
			}
		}

		if (willModify) {
			try { newBlock.setBlockBoundsBasedOnState(world, x, y, z); } catch (Exception e) {}
			double bMinX = newBlock.getBlockBoundsMinX(); double bMinY = newBlock.getBlockBoundsMinY(); double bMinZ = newBlock.getBlockBoundsMinZ();
			double bMaxX = newBlock.getBlockBoundsMaxX(); double bMaxY = newBlock.getBlockBoundsMaxY(); double bMaxZ = newBlock.getBlockBoundsMaxZ();
			AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(x + bMinX, y + bMinY, z + bMinZ, x + bMaxX, y + bMaxY, z + bMaxZ);
			drawWireframe(tess, bb);
		}
	}
}
