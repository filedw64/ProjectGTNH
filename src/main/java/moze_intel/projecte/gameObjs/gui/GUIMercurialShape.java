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
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;
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
	private static final double TAU = Math.PI * 2;
	private static final double INNER_RADIUS = 29;
	private static final double OUTER_RADIUS = 86;
	private static final int UNDO_WIDTH = 60;
	private static final int UNDO_HEIGHT = 20;
	private static final int UNDO_MARGIN = 10;
	private static final int UNDO_TOP = 10;
	private static final double[][] ICONS = createIcons();
	private final double[][] ringVertices = new double[BuildShape.values().length][];
	private int blockPage;
	private final java.util.Map<String, ItemStack> historyIcons = new java.util.HashMap<>();

	// 静态缓存，存储服务端发来的历史数据
	public static List<HistorySyncPKT.ClientNode> clientHistoryCache = new ArrayList<>();

	// 堆叠轴的循环顺序，末尾的 UNKNOWN 表示「无」
	private static final ForgeDirection[] STACK_AXES = {
		ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH,
		ForgeDirection.WEST, ForgeDirection.EAST, ForgeDirection.UNKNOWN
	};

	public GUIMercurialShape(byte currentShape, ItemStack currentBlock) {
		this.currentShape = currentShape >= 0 && currentShape < shapes.length ? currentShape : (byte) BuildShape.CUBE.ordinal();
		this.currentBlock = currentBlock == null ? null : currentBlock.copy();
		for (int i = 0; i < shapes.length; i++) {
			double[] vertices = new double[9 * 4];
			double step = TAU / shapes.length;
			for (int j = 0; j <= 8; j++) {
				double angle = -Math.PI / 2 + (i - 0.5) * step + 0.015 + (step - 0.03) * j / 8;
				vertices[j * 4] = Math.cos(angle) * INNER_RADIUS;
				vertices[j * 4 + 1] = Math.sin(angle) * INNER_RADIUS;
				vertices[j * 4 + 2] = Math.cos(angle) * OUTER_RADIUS;
				vertices[j * 4 + 3] = Math.sin(angle) * OUTER_RADIUS;
			}
			ringVertices[i] = vertices;
		}
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
		hasClipboard = false;
		historyIcons.clear();
		ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
		if (held != null && held.getItem() instanceof moze_intel.projecte.gameObjs.items.MercurialEye) {
			if (held.hasTagCompound() && held.getTagCompound().getBoolean("HasClipboard")) {
				hasClipboard = true;
				currentMatrix.readFromNBT(held.getTagCompound());
			}
		}
		// blocksPerPage() 依赖 hasClipboard，夹紧必须放在最后
		blockPage = Math.min(blockPage, pageCount() - 1);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawGradientRect(0, 0, this.width, this.height, 0xC0000000, 0xC0000000);

		int centerX = wheelX();
		int centerY = wheelY();
		updateHover(mouseX, mouseY);
		drawWheel(centerX, centerY);

		// 渲染底部可用方块列表
		if (!usableBlocks.isEmpty()) {
			int boxSize = 24;
			int padding = 4;
			int page = Math.min(blockPage, pageCount() - 1);
			int first = page * blocksPerPage();
			int last = Math.min(usableBlocks.size(), first + blocksPerPage());
			int totalWidth = (last - first) * (boxSize + padding) - padding;
			int startX = (this.width - totalWidth) / 2;
			int blockY = this.height - 60;

			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.enableGUIStandardItemLighting();

			for (int i = first; i < last; i++) {
				ItemStack stack = usableBlocks.get(i);
				int bx = startX + (i - first) * (boxSize + padding);
				boolean isSelected = (currentBlock != null && currentBlock.isItemEqual(stack)) || i == hoveredBlockIndex;
				drawRect(bx, blockY, bx + boxSize, blockY + boxSize, isSelected ? 0xAA00AAFF : 0x55000000);
				renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, bx + 4, blockY + 4);
				GL11.glColor4f(1, 1, 1, 1);

				if (i == hoveredBlockIndex) {
					this.drawCenteredString(this.fontRendererObj, stack.getDisplayName(), this.width / 2, blockY - 15, 0xFFFFFF);
				}
			}
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			if (usableBlocks.size() > blocksPerPage()) {
				this.drawCenteredString(this.fontRendererObj, StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.page", page + 1, pageCount()), this.width / 2, blockY + 29, 0xAAAAAA);
			}
		} else {
			this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.no_blocks"), this.width / 2, this.height - 50, 0xFFFFFF);
		}

		// 渲染右上角 Undo 按钮
		int btnX = undoX();
		int btnY = undoY();
		drawRect(btnX, btnY, btnX + UNDO_WIDTH, btnY + UNDO_HEIGHT, hoveredUndo ? 0xAAFF5555 : 0x55AA0000);
		this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.WHITE + StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.undo"), btnX + UNDO_WIDTH / 2, btnY + 6, 0xFFFFFF);

		// 渲染左侧树状历史记录
		if (clientHistoryCache != null && !clientHistoryCache.isEmpty()) {
			int listX = 20;
			int startY = 40;
			int rowHeight = 22;

			this.drawString(this.fontRendererObj, EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.history"), listX, 20, 0xFFFFFF);

			int index = 0;
			for (int i = clientHistoryCache.size() - 1; i >= 0 && index < historyRows(); i--) {
				HistorySyncPKT.ClientNode node = clientHistoryCache.get(i);

				boolean isChild = !node.parentId.isEmpty();
				int xOffset = isChild ? 15 : 0;
				int drawY = startY + index * rowHeight;

				if (isChild) {
					this.drawString(this.fontRendererObj, EnumChatFormatting.DARK_GRAY + "|-", listX + 2, drawY + 6, 0xFFFFFF);
				}

				int itemX = listX + xOffset + 12;

				if (node.id.equals(hoveredHistoryId)) {
					drawRect(itemX - 2, drawY - 2, itemX + 80, drawY + rowHeight - 2, 0x44FFFFFF);
				}

				ItemStack iconStack = historyIcons.get(node.id);
				if (iconStack == null) {
					net.minecraft.block.Block block = net.minecraft.block.Block.getBlockById(node.blockId);
					if (block != null && net.minecraft.item.Item.getItemFromBlock(block) != null) {
						iconStack = new ItemStack(block, 1, node.meta);
						historyIcons.put(node.id, iconStack);
					}
				}
				GL11.glEnable(GL12.GL_RESCALE_NORMAL);
				RenderHelper.enableGUIStandardItemLighting();
				if (iconStack != null) renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), iconStack, itemX, drawY);
				RenderHelper.disableStandardItemLighting();
				GL11.glDisable(GL12.GL_RESCALE_NORMAL);

				this.drawString(this.fontRendererObj, "x" + node.count, itemX + 20, drawY + 4, 0xAAAAAA);

				index++;
			}
		}

		if (hasClipboard) {
			int panelWidth = 100;
			// 计算右下角坐标，留出 15 像素边距
			int panelX = this.width - panelWidth - 15;
			int panelY = matrixY();

			this.drawString(this.fontRendererObj, EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.matrix_title"), panelX + 15, panelY - 12, 0xFFFFFF);

			// 旋转按钮
			drawRect(panelX, panelY, panelX + 90, panelY + 15, 0x550000AA);
			this.drawString(this.fontRendererObj, StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.rotation", currentMatrix.rotations * 90), panelX + 5, panelY + 4, 0xFFFFFF);

			// 堆叠轴切换按钮
			drawRect(panelX, panelY + 20, panelX + 90, panelY + 35, 0x5500AA00);
			String axisName = StatCollector.translateToLocalFormatted(stackAxisKey(currentMatrix.stackAxis));
			this.drawString(this.fontRendererObj, StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.axis", axisName), panelX + 5, panelY + 24, 0xFFFFFF);

			// 堆叠数量按钮
			drawRect(panelX, panelY + 40, panelX + 20, panelY + 55, 0x55AA0000);
			this.drawString(this.fontRendererObj, "-", panelX + 7, panelY + 44, 0xFFFFFF);

			this.drawString(this.fontRendererObj, StatCollector.translateToLocalFormatted("item.pe_mercurial_eye.gui.count", currentMatrix.stackCount), panelX + 25, panelY + 44, 0xFFFFFF);

			drawRect(panelX + 70, panelY + 40, panelX + 90, panelY + 55, 0x5500AA00);
			this.drawString(this.fontRendererObj, "+", panelX + 77, panelY + 44, 0xFFFFFF);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		updateHover(mouseX, mouseY);
		if (mouseButton == 0) {

			// 响应矩阵面板点击
			if (hasClipboard) {
				int panelWidth = 100;
				int panelX = this.width - panelWidth - 15;
				int panelY = matrixY();
				boolean matrixChanged = false;

				// 点击旋转
				if (mouseX >= panelX && mouseX <= panelX + 90 && mouseY >= panelY && mouseY <= panelY + 15) {
					currentMatrix.rotations = (currentMatrix.rotations + 1) % 4;
					matrixChanged = true;
				}
				// 点击切换堆叠轴
				else if (mouseX >= panelX && mouseX <= panelX + 90 && mouseY >= panelY + 20 && mouseY <= panelY + 35) {
					currentMatrix.stackAxis = STACK_AXES[(currentMatrix.stackAxis.ordinal() + 1) % STACK_AXES.length];
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

	private int undoX() { return width - UNDO_WIDTH - UNDO_MARGIN; }
	private int undoY() { return UNDO_TOP; }

	private static String stackAxisKey(ForgeDirection dir) {
		switch (dir) {
			case DOWN: return "item.pe_mercurial_eye.gui.dir.down";
			case UP: return "item.pe_mercurial_eye.gui.dir.up";
			case NORTH: return "item.pe_mercurial_eye.gui.dir.north";
			case SOUTH: return "item.pe_mercurial_eye.gui.dir.south";
			case WEST: return "item.pe_mercurial_eye.gui.dir.west";
			case EAST: return "item.pe_mercurial_eye.gui.dir.east";
			case UNKNOWN:
			default: return "item.pe_mercurial_eye.gui.axis_none";
		}
	}

	private int wheelX() { return width < 440 ? width - 100 : width / 2; }
	private int wheelY() { return Math.max(92, Math.min(height / 2 - 20, height - 165)); }
	private int matrixY() { return height >= 330 ? 40 : height - 75; }
	private int blocksPerPage() { return Math.max(1, (width - (hasClipboard && height < 330 ? 240 : 32)) / 28); }
	private int pageCount() { return Math.max(1, (usableBlocks.size() + blocksPerPage() - 1) / blocksPerPage()); }
	private int historyRows() { return Math.max(0, (height - 120) / 22); }

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int scroll = Mouse.getEventDWheel();
		if (scroll != 0) {
			blockPage = (blockPage + (scroll < 0 ? 1 : pageCount() - 1)) % pageCount();
			blockPage = Math.min(blockPage, pageCount() - 1);
		}
	}

	private void updateHover(int mouseX, int mouseY) {
		hoveredShapeIndex = -1;
		hoveredBlockIndex = -1;
		hoveredHistoryId = "";
		int btnX = undoX();
		int btnY = undoY();
		hoveredUndo = mouseX >= btnX && mouseX < btnX + UNDO_WIDTH && mouseY >= btnY && mouseY < btnY + UNDO_HEIGHT;
		double dx = mouseX - wheelX();
		double dy = mouseY - wheelY();
		double distance = dx * dx + dy * dy;
		if (distance >= INNER_RADIUS * INNER_RADIUS && distance <= OUTER_RADIUS * OUTER_RADIUS) {
			double step = TAU / shapes.length;
			double angle = (Math.atan2(dy, dx) + Math.PI / 2 + step / 2 + TAU) % TAU;
			int index = (int) (angle / step);
			// Match the tessellated sector, including the narrow gaps and straight outer edges.
			double[] v = ringVertices[index];
			for (int j = 0; j < 8; j++) {
				int a = j * 4, b = a + 4;
				if (inTriangle(dx, dy, v[a], v[a+1], v[a+2], v[a+3], v[b+2], v[b+3])
					|| inTriangle(dx, dy, v[a], v[a+1], v[b+2], v[b+3], v[b], v[b+1])) {
					hoveredShapeIndex = index;
					break;
				}
			}
		}
		int page = Math.min(blockPage, pageCount() - 1);
		int first = page * blocksPerPage();
		int count = Math.min(blocksPerPage(), usableBlocks.size() - first);
		int start = (width - (count * 28 - 4)) / 2;
		if (mouseY >= height - 60 && mouseY < height - 36 && mouseX >= start) {
			int offset = mouseX - start;
			if (offset / 28 < count && offset % 28 < 24) hoveredBlockIndex = first + offset / 28;
		}
		if (clientHistoryCache != null) {
			int row = (mouseY - 40) / 22;
			if (mouseY >= 40 && row < historyRows() && row < clientHistoryCache.size()) {
				HistorySyncPKT.ClientNode node = clientHistoryCache.get(clientHistoryCache.size() - 1 - row);
				int x = 32 + (node.parentId.isEmpty() ? 0 : 15);
				if (mouseX >= x && mouseX < x + 100) hoveredHistoryId = node.id;
			}
		}
	}

	private static boolean inTriangle(double x, double y, double ax, double ay, double bx, double by, double cx, double cy) {
		double a = (x - bx) * (ay - by) - (ax - bx) * (y - by);
		double b = (x - cx) * (by - cy) - (bx - cx) * (y - cy);
		double c = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
		return !((a < 0 || b < 0 || c < 0) && (a > 0 || b > 0 || c > 0));
	}

	private void drawWheel(int x, int y) {
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
		try {
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			for (int i = 0; i < shapes.length; i++) {
				boolean hover = i == hoveredShapeIndex;
				boolean selected = i == currentShape;
				tess.setColorRGBA(hover ? 32 : 14, hover ? 135 : selected ? 78 : 24, hover ? 180 : selected ? 105 : 35, hover ? 230 : 205);
				double[] v = ringVertices[i];
				for (int j = 0; j < 8; j++) {
					int a = j * 4, b = a + 4;
					tess.addVertex(x + v[a], y + v[a+1], 0);
					tess.addVertex(x + v[b], y + v[b+1], 0);
					tess.addVertex(x + v[b+2], y + v[b+3], 0);
					tess.addVertex(x + v[a+2], y + v[a+3], 0);
				}
			}
			tess.draw();
			GL11.glLineWidth(1.5F);
			tess.startDrawing(GL11.GL_LINES);
			for (int i = 0; i < shapes.length; i++) {
				double angle = -Math.PI / 2 + i * TAU / shapes.length;
				double ix = x + Math.cos(angle) * 58;
				double iy = y + Math.sin(angle) * 58;
				tess.setColorRGBA(i == currentShape || i == hoveredShapeIndex ? 120 : 195, 230, 255, 255);
				double[] icon = ICONS[i];
				for (int p = 0; p < icon.length; p += 2) tess.addVertex(ix + icon[p], iy + icon[p + 1], 0);
				if (i == currentShape) {
					double[] v = ringVertices[i];
					for (int j = 0; j < 8; j++) {
						tess.addVertex(x + v[j*4], y + v[j*4+1], 0);
						tess.addVertex(x + v[j*4+4], y + v[j*4+5], 0);
					}
				}
			}
			tess.draw();
		} finally {
			GL11.glPopAttrib();
		}
		int index = hoveredShapeIndex < 0 ? currentShape : hoveredShapeIndex;
		drawCenteredString(fontRendererObj, StatCollector.translateToLocal(shapes[index].getLangKey()), x, y + 91, 0xA9ECFF);
		drawCenteredString(fontRendererObj, Integer.toString(shapes[index].getRequiredClicks()), x, y - 4, 0xFFFFFF);
	}

	// Original procedural line art: each pair of points is a line segment, never a texture.
	private static double[][] createIcons() {
		double[][] icons = new double[BuildShape.values().length][];
		for (BuildShape shape : BuildShape.values()) {
			List<Double> lines = new ArrayList<>();
			switch (shape) {
				case LINE: line(lines, -9, 8, 9, -8); break;
				case WALL:
				case FLOOR:
					for (int n = 0; n <= 2; n++) {
						if (shape == BuildShape.WALL) {
							line(lines, -9 + n * 9, -8, -9 + n * 9, 8);
							line(lines, -9, -8 + n * 8, 9, -8 + n * 8);
						} else {
							line(lines, -10 + n * 5, n * 3, n * 5, -6 + n * 3);
							line(lines, -10 + n * 5, -n * 3, n * 5, 6 - n * 3);
						}
					}
					break;
				case SPHERE:
				case SPHERE_HOLLOW:
					ellipse(lines, 0, 0, 10, 10);
					ellipse(lines, 0, 0, 10, 3);
					if (shape == BuildShape.SPHERE) ellipse(lines, 0, 0, 4, 10);
					break;
				case CYLINDER:
				case CYLINDER_HOLLOW:
					ellipse(lines, 0, -7, 9, 3);
					ellipse(lines, 0, 7, 9, 3);
					line(lines, -9, -7, -9, 7); line(lines, 9, -7, 9, 7);
					if (shape == BuildShape.CYLINDER) line(lines, 0, -4, 0, 7);
					break;
				case SLOPE:
					line(lines, -10, 8, 8, -9); line(lines, 8, -9, 8, 8); line(lines, 8, 8, -10, 8);
					line(lines, -5, 8, 8, -4); line(lines, 0, 8, 8, 1);
					break;
				default:
					line(lines, -9, -4, 0, -10); line(lines, 0, -10, 9, -4); line(lines, 9, -4, 0, 2); line(lines, 0, 2, -9, -4);
					line(lines, -9, -4, -9, 6); line(lines, 9, -4, 9, 6); line(lines, 0, 2, 0, 12);
					line(lines, -9, 6, 0, 12); line(lines, 0, 12, 9, 6);
					if (shape == BuildShape.CUBE) {
						line(lines, -6, 0, -6, 6); line(lines, -3, 2, -3, 8);
					} else if (shape == BuildShape.CUBE_HOLLOW) {
						line(lines, 3, 3, 7, 0); line(lines, 7, 0, 7, 5); line(lines, 7, 5, 3, 8); line(lines, 3, 8, 3, 3);
					}
			}
			icons[shape.ordinal()] = new double[lines.size()];
			for (int p = 0; p < lines.size(); p++) icons[shape.ordinal()][p] = lines.get(p);
		}
		return icons;
	}

	private static void line(List<Double> v, double x1, double y1, double x2, double y2) {
		v.add(x1); v.add(y1); v.add(x2); v.add(y2);
	}

	private static void ellipse(List<Double> v, double x, double y, double rx, double ry) {
		for (int n = 0; n < 24; n++) {
			double a = n * TAU / 24, b = (n + 1) * TAU / 24;
			line(v, x + Math.cos(a) * rx, y + Math.sin(a) * ry, x + Math.cos(b) * rx, y + Math.sin(b) * ry);
		}
	}

}
