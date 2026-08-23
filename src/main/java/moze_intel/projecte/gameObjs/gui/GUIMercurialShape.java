package moze_intel.projecte.gameObjs.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.MercurialShapePKT;
import moze_intel.projecte.network.packets.HistorySyncPKT;
import moze_intel.projecte.network.packets.HistoryRequestPKT;
import moze_intel.projecte.network.packets.MercurialUndoPKT;
import moze_intel.projecte.utils.BuildShape;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import moze_intel.projecte.utils.mercurial.TransformMatrix;
import moze_intel.projecte.network.packets.MercurialTransformPKT;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GUIMercurialShape extends GuiScreen {

	// 变量声明区
	private int hoveredShapeIndex = -1;
	private int hoveredBlockIndex = -1;
	private boolean hoveredUndo = false;
	private String hoveredHistoryId = "";

	private byte currentShape = 0;
	private ItemStack currentBlock = null;

	private TransformMatrix currentMatrix = new TransformMatrix();
	private boolean hasClipboard = false;

	private final BuildShape[] shapes = BuildShape.values();
	private final List<ItemStack> usableBlocks = new ArrayList<>();
	private static final RenderItem renderItem = new RenderItem();

	// 静态缓存，存储服务端发来的历史数据
	public static List<HistorySyncPKT.ClientNode> clientHistoryCache = new ArrayList<>();

	public GUIMercurialShape(byte currentShape, ItemStack currentBlock) {
		this.currentShape = currentShape;
		this.currentBlock = currentBlock;
	}

	@Override
	public void initGui() {
		super.initGui();
		// 打开 GUI 时请求最新历史记录
		PacketHandler.sendToServer(new HistoryRequestPKT());

		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		usableBlocks.clear();

		// 扫描玩家背包，找出所有带有 EMC 的方块
		for (ItemStack stack : player.inventory.mainInventory) {
			if (stack != null && stack.getItem() instanceof ItemBlock && EMCHelper.doesItemHaveEmc(stack)) {
				boolean exists = false;
				for (ItemStack existing : usableBlocks) {
					if (existing.isItemEqual(stack)) {
						exists = true;
						break;
					}
				}
				if (!exists) {
					ItemStack copy = stack.copy();
					copy.stackSize = 1; // 仅作为图标展示
					usableBlocks.add(copy);
				}
			}
		}
		// 读取当前手持物品的剪贴板状态
		ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
		if (held != null && held.getItem() instanceof moze_intel.projecte.gameObjs.items.MercurialEye) {
			if (held.hasTagCompound() && held.getTagCompound().getBoolean("HasClipboard")) {
				hasClipboard = true;
				currentMatrix.readFromNBT(held.getTagCompound());
			}
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawGradientRect(0, 0, this.width, this.height, 0xC0000000, 0xC0000000);

		int centerX = this.width / 2;
		int centerY = this.height / 2 - 30; // 轮盘稍微靠上一点，给底部的方块栏留空间
		double radius = 60.0;

		double dx = mouseX - centerX;
		double dy = mouseY - centerY;
		double mouseDist = Math.sqrt(dx * dx + dy * dy);
		double mouseAngle = Math.atan2(dy, dx);
		if (mouseAngle < 0) mouseAngle += Math.PI * 2;

		hoveredShapeIndex = -1;
		hoveredBlockIndex = -1;

		// 渲染形状轮盘
		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		// 绘制中心点
		drawCircle(centerX, centerY, 3, 0xFFFFFFFF);

		// 判断鼠标是否在轮盘区域
		if (mouseDist > 15.0 && mouseDist < 120.0) {
			double segmentAngle = (Math.PI * 2) / shapes.length;
			double offsetAngle = mouseAngle + (Math.PI / 2) + (segmentAngle / 2);
			if (offsetAngle >= Math.PI * 2) offsetAngle -= Math.PI * 2;
			hoveredShapeIndex = (int) (offsetAngle / segmentAngle) % shapes.length;
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();

		// 渲染轮盘文字
		for (int i = 0; i < shapes.length; i++) {
			BuildShape shape = shapes[i];
			double angle = -Math.PI / 2 + i * ((Math.PI * 2) / shapes.length);
			int textX = (int) (centerX + Math.cos(angle) * radius);
			int textY = (int) (centerY + Math.sin(angle) * radius);
			String text = shape.getDisplayName();
			int strWidth = this.fontRendererObj.getStringWidth(text);

			textX -= strWidth / 2;
			textY -= this.fontRendererObj.FONT_HEIGHT / 2;

			if (i == hoveredShapeIndex || (hoveredShapeIndex == -1 && i == currentShape)) {
				GL11.glPushMatrix();
				GL11.glTranslatef(textX + strWidth/2f, textY + 4, 0);
				GL11.glScalef(1.2f, 1.2f, 1.2f);
				this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.AQUA + text, -strWidth/2, -4, 0xFFFFFF);
				GL11.glPopMatrix();
			} else {
				this.fontRendererObj.drawStringWithShadow(text, textX, textY, 0x888888);
			}
		}

		// 渲染底部可用方块列表
		if (!usableBlocks.isEmpty()) {
			int boxSize = 24;
			int padding = 4;
			int totalWidth = usableBlocks.size() * boxSize + (usableBlocks.size() - 1) * padding;
			int startX = (this.width - totalWidth) / 2;
			int blockY = this.height - 60;

			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.enableGUIStandardItemLighting();

			for (int i = 0; i < usableBlocks.size(); i++) {
				ItemStack stack = usableBlocks.get(i);
				int bx = startX + i * (boxSize + padding);

				if (mouseX >= bx && mouseX <= bx + boxSize && mouseY >= blockY && mouseY <= blockY + boxSize) {
					hoveredBlockIndex = i;
				}

				boolean isSelected = (currentBlock != null && currentBlock.isItemEqual(stack)) || i == hoveredBlockIndex;

				drawRect(bx, blockY, bx + boxSize, blockY + boxSize, isSelected ? 0xAA00AAFF : 0x55000000);
				renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, bx + 4, blockY + 4);

				if (i == hoveredBlockIndex) {
					this.drawCenteredString(this.fontRendererObj, stack.getDisplayName(), this.width / 2, blockY - 15, 0xFFFFFF);
				}
			}
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		} else {
			this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.RED + "No valid blocks with EMC found in inventory!", this.width / 2, this.height - 50, 0xFFFFFF);
		}

		// 渲染右上角 Undo 按钮
		int undoWidth = 60;
		int undoHeight = 20;
		int undoX = this.width - undoWidth - 10;
		int undoY = 10;

		hoveredUndo = mouseX >= undoX && mouseX <= undoX + undoWidth && mouseY >= undoY && mouseY <= undoY + undoHeight;
		drawRect(undoX, undoY, undoX + undoWidth, undoY + undoHeight, hoveredUndo ? 0xAAFF5555 : 0x55AA0000);
		this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.WHITE + "UNDO", undoX + undoWidth / 2, undoY + 6, 0xFFFFFF);

		// 渲染左侧树状历史记录
		hoveredHistoryId = "";
		if (clientHistoryCache != null && !clientHistoryCache.isEmpty()) {
			int listX = 20;
			int startY = 40;
			int rowHeight = 22;

			this.drawString(this.fontRendererObj, EnumChatFormatting.GOLD + "Operation History", listX, 20, 0xFFFFFF);

			int index = 0;
			for (int i = clientHistoryCache.size() - 1; i >= 0; i--) {
				HistorySyncPKT.ClientNode node = clientHistoryCache.get(i);

				boolean isChild = !node.parentId.isEmpty();
				int xOffset = isChild ? 15 : 0;
				int drawY = startY + index * rowHeight;

				if (isChild) {
					this.drawString(this.fontRendererObj, EnumChatFormatting.DARK_GRAY + "|-", listX + 2, drawY + 6, 0xFFFFFF);
				}

				int itemX = listX + xOffset + 12;

				if (mouseX >= itemX && mouseX <= itemX + 100 && mouseY >= drawY && mouseY <= drawY + rowHeight) {
					hoveredHistoryId = node.id;
					drawRect(itemX - 2, drawY - 2, itemX + 80, drawY + rowHeight - 2, 0x44FFFFFF);
				}

				ItemStack iconStack = new ItemStack(net.minecraft.block.Block.getBlockById(node.blockId), 1, node.meta);
				GL11.glEnable(GL12.GL_RESCALE_NORMAL);
				RenderHelper.enableGUIStandardItemLighting();
				renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), iconStack, itemX, drawY);
				RenderHelper.disableStandardItemLighting();
				GL11.glDisable(GL12.GL_RESCALE_NORMAL);

				this.drawString(this.fontRendererObj, "x" + node.count, itemX + 20, drawY + 4, 0xAAAAAA);

				index++;
			}
		}

		if (hasClipboard) {
			int panelWidth = 100;
			int panelHeight = 60;
			// 计算右下角坐标，留出 15 像素边距
			int panelX = this.width - panelWidth - 15;
			int panelY = this.height - panelHeight - 15;

			this.drawString(this.fontRendererObj, EnumChatFormatting.LIGHT_PURPLE + "矩阵控制台", panelX + 15, panelY - 12, 0xFFFFFF);

			// 旋转按钮
			drawRect(panelX, panelY, panelX + 90, panelY + 15, 0x550000AA);
			this.drawString(this.fontRendererObj, "旋转: " + (currentMatrix.rotations * 90) + "°", panelX + 5, panelY + 4, 0xFFFFFF);

			// 堆叠轴切换按钮
			drawRect(panelX, panelY + 20, panelX + 90, panelY + 35, 0x5500AA00);
			String axisName = currentMatrix.stackAxis == ForgeDirection.UNKNOWN ? "无" : currentMatrix.stackAxis.name();
			this.drawString(this.fontRendererObj, "堆叠轴: " + axisName, panelX + 5, panelY + 24, 0xFFFFFF);

			// 堆叠数量按钮
			drawRect(panelX, panelY + 40, panelX + 20, panelY + 55, 0x55AA0000);
			this.drawString(this.fontRendererObj, "-", panelX + 7, panelY + 44, 0xFFFFFF);

			this.drawString(this.fontRendererObj, "数量: " + currentMatrix.stackCount, panelX + 25, panelY + 44, 0xFFFFFF);

			drawRect(panelX + 70, panelY + 40, panelX + 90, panelY + 55, 0x5500AA00);
			this.drawString(this.fontRendererObj, "+", panelX + 77, panelY + 44, 0xFFFFFF);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		if (mouseButton == 0) {

			// 响应矩阵控制台点击
			if (hasClipboard) {
				int panelWidth = 100;
				int panelHeight = 60;
				int panelX = this.width - panelWidth - 15;
				int panelY = this.height - panelHeight - 15;
				boolean matrixChanged = false;

				// 点击旋转
				if (mouseX >= panelX && mouseX <= panelX + 90 && mouseY >= panelY && mouseY <= panelY + 15) {
					currentMatrix.rotations = (currentMatrix.rotations + 1) % 4;
					matrixChanged = true;
				}
				// 点击切换堆叠轴
				else if (mouseX >= panelX && mouseX <= panelX + 90 && mouseY >= panelY + 20 && mouseY <= panelY + 35) {
					int nextOrd = (currentMatrix.stackAxis.ordinal() + 1) % 7; // 0~6
					currentMatrix.stackAxis = ForgeDirection.VALID_DIRECTIONS[nextOrd == 6 ? 0 : nextOrd];
					if(nextOrd == 6) currentMatrix.stackAxis = ForgeDirection.UNKNOWN;
					matrixChanged = true;
				}
				// 点击数量 -
				else if (mouseX >= panelX && mouseX <= panelX + 20 && mouseY >= panelY + 40 && mouseY <= panelY + 55) {
					if (currentMatrix.stackCount > 1) {
						currentMatrix.stackCount--;
						matrixChanged = true;
					}
				}
				// 点击数量 +
				else if (mouseX >= panelX + 70 && mouseX <= panelX + 90 && mouseY >= panelY + 40 && mouseY <= panelY + 55) {
					if (currentMatrix.stackCount < 64) { // 限制最大堆叠数
						currentMatrix.stackCount++;
						matrixChanged = true;
					}
				}

				// 发送包到服务端同步
				if (matrixChanged) {
					PacketHandler.sendToServer(new MercurialTransformPKT(
						currentMatrix.rotations,
						currentMatrix.stackAxis.ordinal(),
						currentMatrix.stackCount
					));

					ItemStack held = this.mc.thePlayer.getHeldItem();
					if (held != null && held.hasTagCompound()) {
						currentMatrix.writeToNBT(held.getTagCompound());
					}

					this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
					return; // 拦截点击，防止触发原有代码关闭 GUI
				}
			}
			// 优先拦截树状历史记录的精准撤销点击
			if (hoveredHistoryId != null && !hoveredHistoryId.isEmpty()) {
				PacketHandler.sendToServer(new MercurialUndoPKT(hoveredHistoryId));
				this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
				this.mc.displayGuiScreen(null);
				return;
			}

			// 拦截右上角快捷 Undo 按钮的点击
			if (hoveredUndo) {
				PacketHandler.sendToServer(new MercurialUndoPKT("")); // 传空字符串表示撤销最新一条
				this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
				this.mc.displayGuiScreen(null);
				return;
			}

			// 正常修改形状/方块的点击
			boolean changed = false;

			if (hoveredShapeIndex != -1) {
				currentShape = (byte) hoveredShapeIndex;
				changed = true;
			}
			if (hoveredBlockIndex != -1) {
				currentBlock = usableBlocks.get(hoveredBlockIndex);
				changed = true;
			}

			if (changed) {
				PacketHandler.sendToServer(new MercurialShapePKT(currentShape, currentBlock));
				this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
				this.mc.displayGuiScreen(null);
			}
		} else {
			super.mouseClicked(mouseX, mouseY, mouseButton);
		}
	}

	@Override
	public boolean doesGuiPauseGame() { return false; }

	private void drawCircle(double x, double y, double radius, int color) {
		float a = (float)(color >> 24 & 255) / 255.0F;
		float r = (float)(color >> 16 & 255) / 255.0F;
		float g = (float)(color >> 8 & 255) / 255.0F;
		float b = (float)(color & 255) / 255.0F;
		Tessellator tessellator = Tessellator.instance;
		GL11.glColor4f(r, g, b, a);
		tessellator.startDrawing(GL11.GL_POLYGON);
		for (int i = 0; i <= 360; i += 10) {
			double angle = i * Math.PI / 180.0;
			tessellator.addVertex(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, 0);
		}
		tessellator.draw();
	}
}
