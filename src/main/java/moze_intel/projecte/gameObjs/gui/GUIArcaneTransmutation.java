
package moze_intel.projecte.gameObjs.gui;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ArcaneTabletButtonPKT;
import moze_intel.projecte.utils.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class GUIArcaneTransmutation extends GuiContainer {

	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/arcane_transmutation_tablet.png");

	private static final int BTN_ROTATE = 0;
	private static final int BTN_BALANCE = 1;
	private static final int BTN_FOCUS = 2;
	private static final int BTN_CLEAR = 3;
	private static final int BTN_PREV = 4;
	private static final int BTN_NEXT = 5;

	private final ArcaneTransmutationContainer container;
	private GuiTextField textBoxFilter;
	private TabletButton prevButton;
	private TabletButton nextButton;

	public GUIArcaneTransmutation(InventoryPlayer invPlayer, EntityPlayer player) {
		super(new ArcaneTransmutationContainer(invPlayer, player));
		this.container = (ArcaneTransmutationContainer) this.inventorySlots;
		this.xSize = 252;
		this.ySize = 217;
	}

	@Override
	public void initGui() {
		super.initGui();

		// 搜索框：使用绝对坐标
		this.textBoxFilter = new GuiTextField(this.fontRendererObj, guiLeft + 83, guiTop + 6, 160, 12);
		this.textBoxFilter.setText(container.transmutationInventory.filter);
		this.textBoxFilter.setEnableBackgroundDrawing(false);
		this.textBoxFilter.setTextColor(0xFFFFFF);

		// 左侧功能键：9x9 精灵居中放在 11x10 热区里；纵向只比精灵多 1px，避免相邻热区互相重叠
		buttonList.add(new TabletButton(BTN_ROTATE, guiLeft + 4, guiTop + 16, 11, 10, 234, 0, false,
				"pe.arcane_tablet.rotate", "pe.arcane_tablet.rotate.shift"));
		buttonList.add(new TabletButton(BTN_BALANCE, guiLeft + 4, guiTop + 26, 11, 10, 234, 0, false,
				"pe.arcane_tablet.balance", "pe.arcane_tablet.balance.shift"));
		buttonList.add(new TabletButton(BTN_FOCUS, guiLeft + 4, guiTop + 36, 11, 10, 234, 0, false,
				"pe.arcane_tablet.focus_search", null));
		buttonList.add(new TabletButton(BTN_CLEAR, guiLeft + 4, guiTop + 61, 11, 10, 234, 0, false,
				"pe.arcane_tablet.clear", null));

		// 翻页键：18x18 精灵与热区一致
		this.prevButton = new TabletButton(BTN_PREV, guiLeft + 83, guiTop + 20, 18, 18, 196, 0, true,
				"pe.arcane_tablet.page_prev", null);
		this.nextButton = new TabletButton(BTN_NEXT, guiLeft + 227, guiTop + 20, 18, 18, 215, 0, true,
				"pe.arcane_tablet.page_next", null);
		buttonList.add(prevButton);
		buttonList.add(nextButton);

		updatePageButtons();
	}

	private static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		this.textBoxFilter.updateCursorCounter();
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		container.transmutationInventory.learnFlag = 0;
		container.transmutationInventory.unlearnFlag = 0;
	}

	// NEI 用 ASM 把 GuiContainer.handleMouseClick 换成了自己的 GuiContainerManager.handleSlotClick，
	// 而 TransmutationContainer.slotClick 的 isCalledByNEI() 会凭这个调用栈认定是 NEI 操作并直接返回 null，
	// 导致客户端每次点击都被服务端判为事务不一致而回滚（点了没反应、关界面才生效）。
	// 这里与 GUITransmutation 保持一致，直接走 playerController.windowClick，绕开被替换的父类方法。
	@Override
	protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType) {
		if (slotIn instanceof SlotOutput && clickType == 4)
			return; // 禁止从输出槽位中丢弃物品

		if (slotIn != null)
			slotId = slotIn.slotNumber;

		this.mc.playerController.windowClick(this.inventorySlots.windowId, slotId, clickedButton, clickType, this.mc.thePlayer);
	}

	// 翻页可用性随页码/过滤结果变化，翻页、改过滤条件后都要重算
	private void updatePageButtons() {
		if (prevButton != null)
			prevButton.enabled = container.transmutationInventory.searchpage > 0;
		if (nextButton != null)
			nextButton.enabled = container.transmutationInventory.hasNextPage();
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		// 搜索框：右键清空过滤条件并重新聚焦
		if (mouseButton == 1 && isMouseOver(mouseX, mouseY, guiLeft + 83, guiTop + 6, 160, 12)) {
			this.textBoxFilter.setText("");
			this.textBoxFilter.setFocused(true);
			container.transmutationInventory.filter = "";
			container.transmutationInventory.searchpage = 0;
			container.transmutationInventory.updateOutputs();
			updatePageButtons();
			return;
		}

		super.mouseClicked(mouseX, mouseY, mouseButton);
		this.textBoxFilter.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	protected void keyTyped(char c, int keyCode) {
		if (this.textBoxFilter.isFocused()) {
			if (keyCode == Keyboard.KEY_ESCAPE)
				this.textBoxFilter.setFocused(false);
			else {
				this.textBoxFilter.textboxKeyTyped(c, keyCode);
				container.transmutationInventory.filter = this.textBoxFilter.getText().toLowerCase();
				container.transmutationInventory.searchpage = 0;
				container.transmutationInventory.updateOutputs();
				updatePageButtons();
			}
		}
		else super.keyTyped(c, keyCode);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		boolean isShift = GuiScreen.isShiftKeyDown();

		switch (button.id) {
			case BTN_ROTATE:
				PacketHandler.sendToServer(new ArcaneTabletButtonPKT(isShift ? 1 : 0));
				break;
			case BTN_BALANCE:
				PacketHandler.sendToServer(new ArcaneTabletButtonPKT(isShift ? 3 : 2));
				break;
			case BTN_FOCUS:
				this.textBoxFilter.setFocused(true);
				break;
			case BTN_CLEAR:
				PacketHandler.sendToServer(new ArcaneTabletButtonPKT(4));
				break;
			case BTN_PREV:
				container.transmutationInventory.searchpage--;
				container.transmutationInventory.updateOutputs();
				break;
			case BTN_NEXT:
				container.transmutationInventory.searchpage++;
				container.transmutationInventory.updateOutputs();
				break;
			default:
				break;
		}

		updatePageButtons();
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		this.mc.renderEngine.bindTexture(texture);

		// 画主体
		this.drawTexturedModalRect(guiLeft + 76, guiTop, 0, 0, 176, ySize);
		// 画左侧 3x3 区域
		this.drawTexturedModalRect(guiLeft + 1, guiTop + 10, 180, 32, 76, 89);
		// 画左侧面板下半部分的延伸块，让左栏与主面板底部齐平
		this.drawTexturedModalRect(guiLeft + 1, guiTop + 99, 180, 130, 76, 118);
		// 画合成箭头
		this.drawTexturedModalRect(guiLeft + 26, guiTop + 76, 177, 19, 18, 12);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String emc = StatCollector.translateToLocal("pe.emc.emc_tooltip_prefix") + " " + Constants.EMC_FORMATTER.format(container.transmutationInventory.emc);
		int fontWidth = this.fontRendererObj.getStringWidth(emc);
		this.fontRendererObj.drawString(emc, 76 + (176 - fontWidth) / 2, -10, 0xFFFFFF);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);

		this.textBoxFilter.drawTextBox();

		for (int i = 0; i < buttonList.size(); i++) {
			Object entry = buttonList.get(i);
			if (!(entry instanceof TabletButton))
				continue;

			TabletButton button = (TabletButton) entry;
			if (!button.isHovered(mouseX, mouseY))
				continue;

			List<String> tooltip = new ArrayList<String>();
			tooltip.add(StatCollector.translateToLocal(button.getTooltipKey()));
			this.drawHoveringText(tooltip, mouseX, mouseY, this.fontRendererObj);
			break;
		}
	}

	/**
	 * 平板按钮：只负责在鼠标悬停/不可用时画出对应的精灵，图标本身是画在面板贴图里的。
	 */
	private class TabletButton extends GuiButton {

		private final int u;
		private final int v;
		private final int spriteWidth;
		private final int spriteHeight;
		private final String tooltipKey;
		private final String shiftTooltipKey;

		TabletButton(int id, int x, int y, int width, int height, int u, int v, boolean fullSprite, String tooltipKey, String shiftTooltipKey) {
			super(id, x, y, width, height, "");
			this.u = u;
			this.v = v;
			this.spriteWidth = fullSprite ? 18 : 9;
			this.spriteHeight = fullSprite ? 18 : 9;
			this.tooltipKey = tooltipKey;
			this.shiftTooltipKey = shiftTooltipKey;
		}

		@Override
		public void drawButton(Minecraft mc, int mouseX, int mouseY) {
			if (!this.visible)
				return;

			mc.renderEngine.bindTexture(texture);

			int spriteX = this.xPosition + (this.width - this.spriteWidth) / 2;
			int spriteY = this.yPosition + (this.height - this.spriteHeight) / 2;

			if (!this.enabled) {
				GL11.glColor4f(0.4F, 0.4F, 0.4F, 1F);
				this.drawTexturedModalRect(spriteX, spriteY, this.u, this.v, this.spriteWidth, this.spriteHeight);
				GL11.glColor4f(1F, 1F, 1F, 1F);
			}
			else if (isHovered(mouseX, mouseY)) {
				int offset = Mouse.isButtonDown(0) ? 1 : 0;
				this.drawTexturedModalRect(spriteX + offset, spriteY + offset, this.u, this.v, this.spriteWidth, this.spriteHeight);
			}
		}

		boolean isHovered(int mouseX, int mouseY) {
			return isMouseOver(mouseX, mouseY, this.xPosition, this.yPosition, this.width, this.height);
		}

		String getTooltipKey() {
			return GuiScreen.isShiftKeyDown() && this.shiftTooltipKey != null ? this.shiftTooltipKey : this.tooltipKey;
		}
	}
}
