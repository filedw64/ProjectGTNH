package moze_intel.projecte.events;

import com.google.common.collect.Lists;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.items.ItemMode;
import moze_intel.projecte.utils.MetaBlock;
import moze_intel.projecte.utils.WorldTransmutations;

import java.util.List;

@SideOnly(Side.CLIENT)
public class TransmutationRenderingEvent
{
	// 优化：使用轻量级的自定义坐标类，并作为对象池使用，避免每帧产生大量 AxisAlignedBB 对象导致 GC 卡顿
	private static class RenderPos {
		int x, y, z;
		RenderPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
		void set(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
	}

	private final List<RenderPos> renderList = Lists.newArrayList();
	private int renderCount = 0; // 记录当前帧需要渲染的数量

	private double playerX;
	private double playerY;
	private double playerZ;
	private MetaBlock transmutationResult;

	@SubscribeEvent
	public void preDrawHud(RenderGameOverlayEvent.Pre event)
	{
		if (event.type == ElementType.CROSSHAIRS && transmutationResult != null)
		{
			Minecraft mc = Minecraft.getMinecraft();
			RenderItem.getInstance().renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), transmutationResult.toItemStack(), 0, 0);
		}
	}

	@SubscribeEvent
	public void onOverlay(DrawBlockHighlightEvent event)
	{
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		World world = player.worldObj;
		ItemStack stack = player.getHeldItem();

		if (stack == null || stack.getItem() != ObjHandler.philosStone)
		{
			transmutationResult = null;
			return;
		}

		playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) event.partialTicks;
		playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) event.partialTicks;
		playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) event.partialTicks;

		MovingObjectPosition mop = event.target;

		if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK)
		{
			ForgeDirection orientation = ForgeDirection.getOrientation(mop.sideHit);
			MetaBlock current = new MetaBlock(world, mop.blockX, mop.blockY, mop.blockZ);
			transmutationResult = WorldTransmutations.getWorldTransmutation(current, player.isSneaking());

			if (transmutationResult != null)
			{
				byte charge = ((ItemMode) stack.getItem()).getCharge(stack);
				renderCount = 0; // 重置渲染计数器

				switch (((ItemMode) stack.getItem()).getMode(stack))
				{
					case 0: // Cube
					{
						for (int x = mop.blockX - charge; x <= mop.blockX + charge; x++)
							for (int y = mop.blockY - charge; y <= mop.blockY + charge; y++)
								for (int z = mop.blockZ - charge; z <= mop.blockZ + charge; z++)
									addBlockToRenderList(world, current, x, y, z);
						break;
					}
					case 1: // Panel
					{
						int side = orientation.offsetY != 0 ? 0 : orientation.offsetX != 0 ? 1 : 2;
						if (side == 0)
						{
							for (int x = mop.blockX - charge; x <= mop.blockX + charge; x++)
								for (int z = mop.blockZ - charge; z <= mop.blockZ + charge; z++)
									addBlockToRenderList(world, current, x, mop.blockY, z);
						}
						else if (side == 1)
						{
							for (int y = mop.blockY - charge; y <= mop.blockY + charge; y++)
								for (int z = mop.blockZ - charge; z <= mop.blockZ + charge; z++)
									addBlockToRenderList(world, current, mop.blockX, y, z);
						}
						else
						{
							for (int x = mop.blockX - charge; x <= mop.blockX + charge; x++)
								for (int y = mop.blockY - charge; y <= mop.blockY + charge; y++)
									addBlockToRenderList(world, current, x, y, mop.blockZ);
						}
						break;
					}
					case 2: // Line
					{
						String dir = Direction.directions[MathHelper.floor_double((double)((player.rotationYaw * 4F) / 360F) + 0.5D) & 3];
						int side = orientation.offsetX != 0 ? 0 : orientation.offsetZ != 0 ? 1 : dir.equals("NORTH") || dir.equals("SOUTH") ? 0 : 1;

						if (side == 0)
						{
							for (int z = mop.blockZ - charge; z <= mop.blockZ + charge; z++)
								addBlockToRenderList(world, current, mop.blockX, mop.blockY, z);
						}
						else
						{
							for (int x = mop.blockX - charge; x <= mop.blockX + charge; x++)
								addBlockToRenderList(world, current, x, mop.blockY, mop.blockZ);
						}
						break;
					}
				}

				if (renderCount > 0)
				{
					drawAll();
				}
			}
		}
		else
		{
			transmutationResult = null;
		}
	}

	private void drawAll()
	{
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDepthMask(false);
		GL11.glColor4f(1.0f, 1.0f, 1.0f, ProjectEConfig.pulsatingOverlay ? getPulseProportion() * 0.60f : 0.35f);

		Tessellator tessellator = Tessellator.instance;

		// 批量渲染
		tessellator.startDrawingQuads();

		// 使用平移功能代替每个方块的运算
		tessellator.setTranslation(-playerX, -playerY, -playerZ);

		for (int i = 0; i < renderCount; i++)
		{
			RenderPos pos = renderList.get(i);
			double minX = pos.x - 0.02D;
			double minY = pos.y - 0.02D;
			double minZ = pos.z - 0.02D;
			double maxX = pos.x + 1.02D;
			double maxY = pos.y + 1.02D;
			double maxZ = pos.z + 1.02D;

			// Top
			tessellator.addVertex(minX, maxY, minZ);
			tessellator.addVertex(maxX, maxY, minZ);
			tessellator.addVertex(maxX, maxY, maxZ);
			tessellator.addVertex(minX, maxY, maxZ);
			// Bottom
			tessellator.addVertex(minX, minY, minZ);
			tessellator.addVertex(maxX, minY, minZ);
			tessellator.addVertex(maxX, minY, maxZ);
			tessellator.addVertex(minX, minY, maxZ);
			// Front
			tessellator.addVertex(maxX, maxY, maxZ);
			tessellator.addVertex(minX, maxY, maxZ);
			tessellator.addVertex(minX, minY, maxZ);
			tessellator.addVertex(maxX, minY, maxZ);
			// Back
			tessellator.addVertex(maxX, minY, minZ);
			tessellator.addVertex(minX, minY, minZ);
			tessellator.addVertex(minX, maxY, minZ);
			tessellator.addVertex(maxX, maxY, minZ);
			// Left
			tessellator.addVertex(minX, maxY, maxZ);
			tessellator.addVertex(minX, maxY, minZ);
			tessellator.addVertex(minX, minY, minZ);
			tessellator.addVertex(minX, minY, maxZ);
			// Right
			tessellator.addVertex(maxX, maxY, maxZ);
			tessellator.addVertex(maxX, maxY, minZ);
			tessellator.addVertex(maxX, minY, minZ);
			tessellator.addVertex(maxX, minY, maxZ);
		}

		tessellator.draw();
		tessellator.setTranslation(0, 0, 0); // 恢复平移状态

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
	}

	private void addBlockToRenderList(World world, MetaBlock current, int x, int y, int z)
	{
		if (new MetaBlock(world, x, y, z).equals(current))
		{
			// 对象池复用
			if (renderCount >= renderList.size()) {
				renderList.add(new RenderPos(x, y, z));
			} else {
				renderList.get(renderCount).set(x, y, z);
			}
			renderCount++;
		}
	}

	private float getPulseProportion()
	{
		return (float) (0.5F * Math.sin(System.currentTimeMillis() / 350.0) + 0.5F);
	}
}
