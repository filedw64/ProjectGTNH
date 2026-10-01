package moze_intel.projecte.gameObjs.gui;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import moze_intel.projecte.gameObjs.gui.component.RefinedButton;
import moze_intel.projecte.utils.SearchHistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GUITransmutation extends GuiContainer {
	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/transmute.png");
	TransmutationInventory inv;
	private GuiTextField textBoxFilter;

	int xLocation;
	int yLocation;

	private int searchDelayTicks = 0;

	public GUITransmutation(InventoryPlayer invPlayer, TransmutationInventory inventory, boolean portable) {
		super(new TransmutationContainer(invPlayer, inventory, portable));
		this.inv = inventory;
		this.xSize = 228;
		this.ySize = 196;
	}

	@Override
	public void initGui() {
		super.initGui();

		this.xLocation = (this.width - this.xSize) / 2;
		this.yLocation = (this.height - this.ySize) / 2;

		this.textBoxFilter = new GuiTextField(this.fontRendererObj, this.xLocation + 88, this.yLocation + 8, 45, 10);
		this.textBoxFilter.setText(inv.filter);

		this.buttonList.add(new RefinedButton(1, this.xLocation + 125, this.yLocation + 98, 14, 16, "<"));
		this.buttonList.add(new RefinedButton(2, this.xLocation + 193, this.yLocation + 98, 14, 16, ">"));
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		this.textBoxFilter.drawTextBox();
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int var1, int var2) {
		this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.transmute"), 6, 8, 4210752);
		String emc = StatCollector.translateToLocal("pe.emc.emc_tooltip_prefix") + String.format(inv.emc < 1e5 ? " %.2f": " %.3e", inv.emc);
		this.fontRendererObj.drawString(emc, 6, this.ySize - 94, 4210752);

		if (inv.learnFlag > 0) {
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned0"), 98, 30, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned1"), 99, 38, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned2"), 100, 46, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned3"), 101, 54, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned4"), 102, 62, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned5"), 103, 70, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned6"), 104, 78, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.learned7"), 107, 86, 4210752);

			inv.learnFlag--;
		}

		if (inv.unlearnFlag > 0) {
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned0"), 97, 22, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned1"), 98, 30, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned2"), 99, 38, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned3"), 100, 46, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned4"), 101, 54, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned5"), 102, 62, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned6"), 103, 70, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned7"), 104, 78, 4210752);
			this.fontRendererObj.drawString(StatCollector.translateToLocal("pe.transmutation.unlearned8"), 107, 86, 4210752);

			inv.unlearnFlag--;
		}
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		this.textBoxFilter.updateCursorCounter();

		// 每一 tick 减少一次倒计时，归零时执行搜索
		if (this.searchDelayTicks > 0) {
			this.searchDelayTicks--;
			if (this.searchDelayTicks == 0) {
				performSearch();
			}
		}
	}

	// 将实际的搜索逻辑抽离出来，方便复用
	private void performSearch() {
		String srch = this.textBoxFilter.getText();
		if (!inv.filter.equals(srch)) {
			inv.filter = srch;
			inv.searchpage = 0;
			inv.updateOutputs();
		}
		this.searchDelayTicks = 0; // 清除可能存在的待定搜索
	}

	@Override
	public void handleMouseInput() {
		// 直接吃掉所有在转化桌界面里的鼠标滚轮事件
		if (Mouse.getEventDWheel() != 0) {
			return;
		}
		super.handleMouseInput();
	}

	@Override
	protected void keyTyped(char par1, int par2) {
		if (this.textBoxFilter.isFocused()) {
			// 如果按下的是 ESC 键，取消焦点、立即应用搜索，并直接返回
			if (par2 == 1) {
				this.textBoxFilter.setFocused(false);
				SearchHistoryManager.resetCursor();
				performSearch();
				return;
			}

			// 处理搜索历史记录快捷键
			if (par2 == Keyboard.KEY_UP) {
				String hist = SearchHistoryManager.navigateUp(this.textBoxFilter.getText());
				this.textBoxFilter.setText(hist);
			}
			else if (par2 == Keyboard.KEY_DOWN) {
				String hist = SearchHistoryManager.navigateDown(this.textBoxFilter.getText());
				this.textBoxFilter.setText(hist);
			}
			// 处理回车键确认
			else if (par2 == Keyboard.KEY_RETURN || par2 == Keyboard.KEY_NUMPADENTER) {
				SearchHistoryManager.addHistory(this.textBoxFilter.getText());
				this.textBoxFilter.setFocused(false);
				SearchHistoryManager.resetCursor();
				performSearch();
			}
			else {
				// 正常的字符输入
				this.textBoxFilter.textboxKeyTyped(par1, par2);
				SearchHistoryManager.resetCursor();

				// 玩家正在打字，重置 1 秒的倒计时
				if (!inv.filter.equals(this.textBoxFilter.getText())) {
					this.searchDelayTicks = 20;
				}
			}
		}
		else {
			super.keyTyped(par1, par2);
		}
	}

	@Override
	protected void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType) {
		if (slotIn instanceof SlotOutput && clickType == 4)
			return; // 禁止从输出槽位中丢弃物品

		if (slotIn != null)
			slotId = slotIn.slotNumber;

		this.mc.playerController.windowClick(this.inventorySlots.windowId, slotId, clickedButton, clickType, this.mc.thePlayer);
	}

	@Override
	protected void mouseClicked(int x, int y, int mouseButton) {
		super.mouseClicked(x, y, mouseButton);

		final int minX = textBoxFilter.xPosition, maxX = minX + textBoxFilter.width;
		final int minY = textBoxFilter.yPosition, maxY = minY + textBoxFilter.height;

		// 右键清空搜索框
		if (mouseButton == 1 && x >= minX && x <= maxX && y <= maxY) {
			this.textBoxFilter.setText("");
			SearchHistoryManager.resetCursor();
			performSearch();
		}

		this.textBoxFilter.mouseClicked(x, y, mouseButton);
	}

	@Override
	public void onGuiClosed() {
		super.onGuiClosed();
		inv.learnFlag = 0;
		inv.unlearnFlag = 0;
		SearchHistoryManager.resetCursor(); // GUI 关闭时重置游标
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		// 如果玩家在倒计时还没结束时点击了翻页按钮，强制先应用当前的搜索文本
		performSearch();

		if (button.id == 1) {
			if (inv.searchpage != 0)
				inv.searchpage--;
		}
		else if (button.id == 2) {
			if (inv.hasNextPage())
				inv.searchpage++;
		}

		inv.updateOutputs();
	}
}
