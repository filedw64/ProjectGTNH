package moze_intel.projecte.gameObjs.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.WandFocusPKT;
import moze_intel.projecte.network.packets.WandModePKT;
import moze_intel.projecte.network.packets.WandUndoPKT;
import moze_intel.projecte.rendering.WandRenderer;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.WandHelper;
import moze_intel.projecte.utils.WandMode;
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
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑之杖模式面板：环形 6 模式 + 撤销按钮 + 成本预览 + 聚焦方块选择器。
 * 环形布局数学、GL 状态保存恢复、发包方式、分页方块选择器照抄 GUIMercurialShape。
 */
@SideOnly(Side.CLIENT)
public class GUIEMCBuildersWand extends GuiScreen {

	private static final double TAU = Math.PI * 2;
	private static final double INNER_RADIUS = 29;
	private static final double OUTER_RADIUS = 86;
	private static final int UNDO_WIDTH = 120;
	private static final int UNDO_HEIGHT = 20;
	// 聚焦方块选择器：格 24 + 间距 4，排布与 GUIMercurialShape 一致
	private static final int BLOCK_BOX = 24;
	private static final int BLOCK_STEP = 28;
	private static final int CLEAR_WIDTH = 56;
	private static final int CLEAR_HEIGHT = 16;
	// 环最小缩到 60%，再小图案就看不清了
	private static final double MIN_RING_SCALE = 0.6D;
	private static final RenderItem renderItem = new RenderItem();
	// 图标索引与 NBT Mode 字节一致
	private static final double[][] ICONS = createIcons();

	private final WandMode[] modes = WandMode.values();
	private final double[][] ringVertices = new double[this.modes.length][];
	private final ItemStack targetBlock;
	// 背包内可选作聚焦方块的物品，已去重，stackSize 统一为 1
	private final List<ItemStack> usableBlocks = new ArrayList<>();

	private byte currentMode;
	private byte previewMode;
	private int hoveredModeIndex = -1;
	private boolean hoveredUndo = false;
	private int hoveredBlockIndex = -1;
	private int blockPage;
	private boolean hoveredClear = false;
	private ItemStack focusBlock;
	// 环形半径按可用高度收缩，保证底部聚焦行与方块网格不被挤掉
	private double ringScale = 1.0D;
	private int centerX;
	private int centerY;
	private WandHelper.PlacementPlan previewPlan;
	private int previewBlocks;
	private double previewEmc;

	public GUIEMCBuildersWand(byte currentMode, ItemStack targetBlock, ItemStack focusBlock) {
		this.currentMode = WandMode.fromId(currentMode).getId();
		this.previewMode = this.currentMode;
		this.targetBlock = targetBlock == null ? null : targetBlock.copy();
		this.focusBlock = focusBlock == null ? null : focusBlock.copy();
	}

	@Override
	public void initGui() {
		super.initGui();
		updateLayout();
		collectBlocks();
		updatePreview();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawGradientRect(0, 0, this.width, this.height, 0xC0000000, 0xC0000000);

		updateHover(mouseX, mouseY);

		// 悬停到别的模式就重算预览；只有模式变化时才跑 BFS，不是每帧
		byte wanted = hoveredModeIndex < 0 ? currentMode : modes[hoveredModeIndex].getId();

		if (wanted != previewMode) {
			previewMode = wanted;
			updatePreview();
		}

		drawWheel(this.centerX, this.centerY);

		// 环心显示将被放置的方块图标
		if (targetBlock != null) {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.enableGUIStandardItemLighting();
			renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), targetBlock, this.centerX - 8, this.centerY - 8);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		}

		this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + StatCollector.translateToLocal("pe.builderswand.gui.title"),
			this.width / 2, 12, 0xFFFFFF);

		drawCostLine();
		drawUndoButton(mouseX, mouseY);
		drawFocusRow();
		drawBlockGrid();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		if (mouseButton != 0) {
			super.mouseClicked(mouseX, mouseY, mouseButton);
			return;
		}

		updateHover(mouseX, mouseY);

		// 清除 / 选方块都不关面板：玩家可能还想接着改模式
		if (hoveredClear) {
			applyFocus(null);
			return;
		}

		if (hoveredBlockIndex >= 0 && hoveredBlockIndex < usableBlocks.size()) {
			applyFocus(usableBlocks.get(hoveredBlockIndex));
			return;
		}

		if (isOverUndo(mouseX, mouseY)) {
			PacketHandler.sendToServer(new WandUndoPKT());
			playClick();
			this.mc.displayGuiScreen(null);
			return;
		}

		if (hoveredModeIndex >= 0) {
			// 选中的瞬间就发包：直接按 ESC 关闭也已经生效
			currentMode = modes[hoveredModeIndex].getId();
			previewMode = currentMode;
			PacketHandler.sendToServer(new WandModePKT(currentMode));
			playClick();
			this.mc.displayGuiScreen(null);
		}
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();

		int scroll = Mouse.getEventDWheel();
		int pages = pageCount();

		if (scroll != 0 && pages > 1) {
			blockPage = (blockPage + (scroll < 0 ? 1 : pages - 1)) % pages;
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private void playClick() {
		this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
	}

	/**
	 * 选中/清除聚焦方块：先改本地状态让面板立刻反映，再发包同步服务端 NBT。
	 */
	private void applyFocus(ItemStack stack) {
		this.focusBlock = stack == null ? null : stack.copy();
		PacketHandler.sendToServer(new WandFocusPKT(this.focusBlock));
		playClick();
		updatePreview();
	}

	/**
	 * 自适应布局：环半径取可用高度的上界（中心上方留 6px，下方留出成本行、撤销、聚焦行与网格），
	 * 再把环心放在剩余空间中间。屏幕越矮环越小，聚焦选择器始终有位置。
	 */
	private void updateLayout() {
		double outer = Math.min(OUTER_RADIUS, (this.height - 132) / 2.0D);
		this.ringScale = Math.max(MIN_RING_SCALE, outer / OUTER_RADIUS);

		int half = (int) Math.ceil(OUTER_RADIUS * this.ringScale);
		int low = half + 6;
		int high = this.height - half - 126;

		this.centerX = this.width / 2;
		this.centerY = Math.max(low, Math.min(this.height / 2, high));
		buildRingVertices();
	}

	private double innerRadius() {
		return INNER_RADIUS * this.ringScale;
	}

	private double outerRadius() {
		return OUTER_RADIUS * this.ringScale;
	}

	private int costY() {
		return this.centerY + (int) Math.round(outerRadius()) + 18;
	}

	private int undoY() {
		return costY() + 16;
	}

	private int focusRowY() {
		// 优先贴底；空间够时与撤销按钮保持 8px 间距
		return Math.max(undoY() + UNDO_HEIGHT + 8, this.height - 62);
	}

	private int gridY() {
		return focusRowY() + 28;
	}

	private int clearX() {
		return this.width / 2 + 90;
	}

	private int clearY() {
		return focusRowY() + 2;
	}

	private int focusIconX() {
		return this.width / 2 - 150;
	}

	private int blocksPerPage() {
		return Math.max(1, (this.width - 32) / BLOCK_STEP);
	}

	private int pageCount() {
		return Math.max(1, (usableBlocks.size() + blocksPerPage() - 1) / blocksPerPage());
	}

	private int indexOfMode(byte id) {
		for (int i = 0; i < modes.length; i++) {
			if (modes[i].getId() == id) return i;
		}

		return 0;
	}

	private void buildRingVertices() {
		double step = TAU / modes.length;

		for (int i = 0; i < modes.length; i++) {
			double[] vertices = new double[9 * 4];

			for (int j = 0; j <= 8; j++) {
				double angle = -Math.PI / 2 + (i - 0.5) * step + 0.015 + (step - 0.03) * j / 8;
				vertices[j * 4] = Math.cos(angle) * innerRadius();
				vertices[j * 4 + 1] = Math.sin(angle) * innerRadius();
				vertices[j * 4 + 2] = Math.cos(angle) * outerRadius();
				vertices[j * 4 + 3] = Math.sin(angle) * outerRadius();
			}

			ringVertices[i] = vertices;
		}
	}

	/**
	 * 扫描背包收集可选聚焦方块，按同种物品去重（照抄 GUIMercurialShape.initGui 的收集段）。
	 */
	private void collectBlocks() {
		usableBlocks.clear();

		if (this.mc == null || this.mc.thePlayer == null) return;

		for (ItemStack stack : this.mc.thePlayer.inventory.mainInventory) {
			if (stack == null || !(stack.getItem() instanceof ItemBlock) || !EMCHelper.doesItemHaveEmc(stack)) continue;

			boolean exists = false;

			for (ItemStack existing : usableBlocks) {
				if (existing.isItemEqual(stack)) {
					exists = true;
					break;
				}
			}

			if (exists) continue;

			ItemStack icon = stack.copy();
			icon.stackSize = 1; // 仅作为图标展示
			usableBlocks.add(icon);
		}
	}

	/**
	 * 成本预览以玩家当前瞄准的方块为准；有聚焦方块时按聚焦方块计费与计数量。
	 */
	private void updatePreview() {
		previewPlan = null;
		previewBlocks = 0;
		previewEmc = 0.0D;

		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer player = mc.thePlayer;

		if (player == null || mc.theWorld == null || mc.objectMouseOver == null) return;
		if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		MovingObjectPosition hit = mc.objectMouseOver;
		// 收费栈：有聚焦用聚焦，否则用被点击方块；区域计算仍按被点击方块
		ItemStack costStack = this.focusBlock != null ? this.focusBlock : WandRenderer.blockStackAt(mc.theWorld, hit);

		// 传真实 player，知识态才判断得对
		previewPlan = WandHelper.plan(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ, hit.sideHit,
			previewMode, ProjectEConfig.buildersWandMaxBlocks, player, costStack);

		if (previewPlan == null) return;

		// 与预览着色 / HUD 同一份计算，保证"显示多少 = 实际能放多少"
		previewBlocks = WandRenderer.previewCount(player, previewPlan, costStack);
		previewEmc = WandRenderer.previewEmcCost(player, previewPlan, previewBlocks);
	}

	private void updateHover(int mouseX, int mouseY) {
		hoveredModeIndex = -1;
		hoveredBlockIndex = -1;
		hoveredClear = false;
		blockPage = Math.min(blockPage, pageCount() - 1);

		// 清除按钮
		hoveredClear = mouseX >= clearX() && mouseX < clearX() + CLEAR_WIDTH
			&& mouseY >= clearY() && mouseY < clearY() + CLEAR_HEIGHT;

		// 分页方块格
		int first = blockPage * blocksPerPage();
		int count = Math.min(blocksPerPage(), usableBlocks.size() - first);

		if (count > 0 && mouseY >= gridY() && mouseY < gridY() + BLOCK_BOX) {
			int startX = (this.width - (count * BLOCK_STEP - (BLOCK_STEP - BLOCK_BOX))) / 2;
			int offset = mouseX - startX;

			if (offset >= 0 && offset / BLOCK_STEP < count && offset % BLOCK_STEP < BLOCK_BOX) {
				hoveredBlockIndex = first + offset / BLOCK_STEP;
			}
		}

		double dx = mouseX - this.centerX;
		double dy = mouseY - this.centerY;
		double distance = dx * dx + dy * dy;

		if (distance < innerRadius() * innerRadius() || distance > outerRadius() * outerRadius()) return;

		double step = TAU / modes.length;
		double angle = (Math.atan2(dy, dx) + Math.PI / 2 + step / 2 + TAU) % TAU;
		int index = (int) (angle / step);

		if (index < 0 || index >= modes.length) return;

		// 与扇区三角化一致：落在扇区内的窄缝直边上也算命中
		double[] v = ringVertices[index];

		for (int j = 0; j < 8; j++) {
			int a = j * 4;
			int b = a + 4;

			if (inTriangle(dx, dy, v[a], v[a + 1], v[a + 2], v[a + 3], v[b + 2], v[b + 3])
				|| inTriangle(dx, dy, v[a], v[a + 1], v[b + 2], v[b + 3], v[b], v[b + 1])) {
				hoveredModeIndex = index;
				break;
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

			for (int i = 0; i < modes.length; i++) {
				boolean hover = i == hoveredModeIndex;
				boolean selected = modes[i].getId() == currentMode;
				tess.setColorRGBA(hover ? 32 : 14, hover ? 135 : selected ? 78 : 24, hover ? 180 : selected ? 105 : 35, hover ? 230 : 205);

				double[] v = ringVertices[i];

				for (int j = 0; j < 8; j++) {
					int a = j * 4;
					int b = a + 4;
					tess.addVertex(x + v[a], y + v[a + 1], 0);
					tess.addVertex(x + v[b], y + v[b + 1], 0);
					tess.addVertex(x + v[b + 2], y + v[b + 3], 0);
					tess.addVertex(x + v[a + 2], y + v[a + 3], 0);
				}
			}

			tess.draw();
			GL11.glLineWidth(1.5F);
			tess.startDrawing(GL11.GL_LINES);

			for (int i = 0; i < modes.length; i++) {
				double angle = -Math.PI / 2 + i * TAU / modes.length;
				double ix = x + Math.cos(angle) * 58 * this.ringScale;
				double iy = y + Math.sin(angle) * 58 * this.ringScale;
				boolean selected = modes[i].getId() == currentMode;
				tess.setColorRGBA(selected || i == hoveredModeIndex ? 120 : 195, 230, 255, 255);

				double[] icon = ICONS[modes[i].getId()];

				for (int p = 0; p < icon.length; p += 2) {
					tess.addVertex(ix + icon[p], iy + icon[p + 1], 0);
				}

				// 当前模式的扇区外沿描一圈，明确高亮
				if (selected) {
					double[] v = ringVertices[i];

					for (int j = 0; j < 8; j++) {
						tess.addVertex(x + v[j * 4], y + v[j * 4 + 1], 0);
						tess.addVertex(x + v[j * 4 + 4], y + v[j * 4 + 5], 0);
					}
				}
			}

			tess.draw();
		} finally {
			GL11.glPopAttrib();
		}

		int index = hoveredModeIndex < 0 ? indexOfMode(currentMode) : hoveredModeIndex;
		this.drawCenteredString(this.fontRendererObj,
			EnumChatFormatting.AQUA + StatCollector.translateToLocal(modes[index].getLangKey()), x, y + (int) Math.round(outerRadius()) + 5, 0xA9ECFF);
	}

	/**
	 * 成本预览行：格数与 EMC 都来自同一份可负担性计算。
	 */
	private void drawCostLine() {
		String blocksLabel = StatCollector.translateToLocal("pe.builderswand.hud.blocks");
		String costLabel = StatCollector.translateToLocal("pe.builderswand.gui.cost");
		String emcLabel = StatCollector.translateToLocal("pe.builderswand.hud.emc");
		String line;

		if (previewPlan == null) {
			line = costLabel + " --";
		} else if (previewEmc > 0.0D) {
			line = blocksLabel + " " + previewBlocks + " / " + costLabel + " "
				+ WandRenderer.formatEmc(previewEmc) + " " + emcLabel;
		} else {
			line = blocksLabel + " " + previewBlocks;
		}

		this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.WHITE + line, this.width / 2, costY(), 0xFFFFFF);
	}

	private void drawUndoButton(int mouseX, int mouseY) {
		int x = this.width / 2 - UNDO_WIDTH / 2;
		int y = undoY();
		hoveredUndo = isOverUndo(mouseX, mouseY);
		drawRect(x, y, x + UNDO_WIDTH, y + UNDO_HEIGHT, hoveredUndo ? 0xAAFF5555 : 0x55AA0000);
		this.drawCenteredString(this.fontRendererObj,
			EnumChatFormatting.WHITE + StatCollector.translateToLocal("pe.builderswand.gui.undo"),
			x + UNDO_WIDTH / 2, y + 6, 0xFFFFFF);
	}

	private boolean isOverUndo(int mouseX, int mouseY) {
		int x = this.width / 2 - UNDO_WIDTH / 2;
		int y = undoY();
		return mouseX >= x && mouseX < x + UNDO_WIDTH && mouseY >= y && mouseY < y + UNDO_HEIGHT;
	}

	/**
	 * 当前聚焦方块一行：图标 + 名称（未设置时显示省略提示）+ 清除按钮。
	 */
	private void drawFocusRow() {
		int x = focusIconX();
		int y = focusRowY();

		if (focusBlock != null) {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			RenderHelper.enableGUIStandardItemLighting();
			renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), focusBlock, x, y + 2);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		}

		String label = StatCollector.translateToLocal("pe.builderswand.gui.focus") + "：";
		String text = focusBlock == null
			? EnumChatFormatting.GRAY + label + EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("pe.builderswand.gui.focus.none")
			: EnumChatFormatting.GRAY + label + EnumChatFormatting.WHITE + focusBlock.getDisplayName();

		int textX = x + 22;
		int room = clearX() - textX - 6;

		// 名字过长就截断，避免压到清除按钮
		this.drawString(this.fontRendererObj, this.fontRendererObj.trimStringToWidth(text, room), textX, y + 6, 0xFFFFFF);

		drawRect(clearX(), clearY(), clearX() + CLEAR_WIDTH, clearY() + CLEAR_HEIGHT,
			hoveredClear ? 0xAAFF5555 : (focusBlock == null ? 0x33000000 : 0x55AA0000));
		this.drawCenteredString(this.fontRendererObj,
			EnumChatFormatting.WHITE + StatCollector.translateToLocal("pe.builderswand.gui.focus.clear"),
			clearX() + CLEAR_WIDTH / 2, clearY() + 4, 0xFFFFFF);
	}

	/**
	 * 分页方块网格：只画一页，鼠标滚轮翻页（照抄 GUIMercurialShape 的方块列表段）。
	 */
	private void drawBlockGrid() {
		if (usableBlocks.isEmpty()) return;

		int first = blockPage * blocksPerPage();
		int last = Math.min(usableBlocks.size(), first + blocksPerPage());
		int totalWidth = (last - first) * BLOCK_STEP - (BLOCK_STEP - BLOCK_BOX);
		int startX = (this.width - totalWidth) / 2;
		int y = gridY();

		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.enableGUIStandardItemLighting();

		for (int i = first; i < last; i++) {
			ItemStack stack = usableBlocks.get(i);
			int bx = startX + (i - first) * BLOCK_STEP;
			boolean selected = focusBlock != null && focusBlock.isItemEqual(stack);
			drawRect(bx, y, bx + BLOCK_BOX, y + BLOCK_BOX, selected ? 0xAA00AAFF : (i == hoveredBlockIndex ? 0x88FFFFFF : 0x55000000));
			renderItem.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, bx + 4, y + 4);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}

		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);

		if (pageCount() > 1) {
			// 页码贴在网格右侧，靠右边界时回缩，避免出屏
			int pageX = Math.min(startX + totalWidth + 6, this.width - 26);
			// 枚举不是字符串，必须先用 String.valueOf 把它拉进字符串拼接
			String pageLabel = EnumChatFormatting.GRAY + String.valueOf(blockPage + 1) + "/" + pageCount();
			this.drawString(this.fontRendererObj, pageLabel, pageX, y + 8, 0xFFFFFF);
		}
	}

	// 程序化线稿，索引与模式 id 一致
	private static double[][] createIcons() {
		double[][] icons = new double[WandMode.values().length][];
		icons[0] = gridIcon(3);
		icons[1] = gridIcon(5);
		icons[2] = planeIcon();
		icons[3] = gridIcon(7);
		icons[4] = pillarIcon();
		icons[5] = cubeIcon();
		icons[6] = hPillarIcon();
		return icons;
	}

	private static double[] gridIcon(int cells) {
		List<Double> v = new ArrayList<>();

		for (int i = 0; i <= cells; i++) {
			double p = -9 + 18.0 * i / cells;
			line(v, -9, p, 9, p);
			line(v, p, -9, p, 9);
		}

		return toArray(v);
	}

	private static double[] planeIcon() {
		List<Double> v = new ArrayList<>();
		// 透视平面：两层菱形 + 中线
		line(v, -10, 0, 0, 7);
		line(v, 0, 7, 10, 0);
		line(v, -10, 0, 0, -7);
		line(v, 0, -7, 10, 0);
		line(v, -6, 0, 0, 4);
		line(v, 0, 4, 6, 0);
		line(v, -6, 0, 0, -4);
		line(v, 0, -4, 6, 0);
		line(v, -10, 0, 10, 0);
		return toArray(v);
	}

	private static double[] pillarIcon() {
		List<Double> v = new ArrayList<>();

		for (int i = -1; i <= 1; i++) {
			double cy = i * 8;
			line(v, -4, cy - 3, 4, cy - 3);
			line(v, 4, cy - 3, 4, cy + 3);
			line(v, 4, cy + 3, -4, cy + 3);
			line(v, -4, cy + 3, -4, cy - 3);
		}

		return toArray(v);
	}

	// 竖直柱转 90°：三段沿 x 铺开，顶点数与 pillarIcon 一致
	private static double[] hPillarIcon() {
		List<Double> v = new ArrayList<>();

		for (int i = -1; i <= 1; i++) {
			double cx = i * 8;
			line(v, cx - 3, -4, cx - 3, 4);
			line(v, cx - 3, 4, cx + 3, 4);
			line(v, cx + 3, 4, cx + 3, -4);
			line(v, cx + 3, -4, cx - 3, -4);
		}

		return toArray(v);
	}

	private static double[] cubeIcon() {
		List<Double> v = new ArrayList<>();
		line(v, -9, -4, 0, -10);
		line(v, 0, -10, 9, -4);
		line(v, 9, -4, 0, 2);
		line(v, 0, 2, -9, -4);
		line(v, -9, -4, -9, 6);
		line(v, 9, -4, 9, 6);
		line(v, 0, 2, 0, 12);
		line(v, -9, 6, 0, 12);
		line(v, 0, 12, 9, 6);
		return toArray(v);
	}

	private static double[] toArray(List<Double> v) {
		double[] out = new double[v.size()];

		for (int i = 0; i < v.size(); i++) {
			out[i] = v.get(i);
		}

		return out;
	}

	private static void line(List<Double> v, double x1, double y1, double x2, double y2) {
		v.add(x1);
		v.add(y1);
		v.add(x2);
		v.add(y2);
	}
}
