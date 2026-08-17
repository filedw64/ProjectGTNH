package moze_intel.projecte.gameObjs.gui;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ArcaneTabletButtonPKT;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class GUIArcaneTransmutation extends GuiContainer {

	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/arcane_transmutation_tablet.png");
	private ArcaneTransmutationContainer container;
	private GuiTextField textBoxFilter;

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
	}

	// 判断鼠标是否在指定矩形区域内
	private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		// --- 搜索框点击检测 (右键清空功能) ---
		if (isMouseOver(mouseX, mouseY, guiLeft + 83, guiTop + 6, 160, 12)) {
			if (mouseButton == 1) { // 1 代表鼠标右键
				this.textBoxFilter.setText("");
				this.textBoxFilter.setFocused(true);
				// 同步清空后端的过滤条件并刷新
				container.transmutationInventory.filter = "";
				container.transmutationInventory.searchpage = 0;
				container.transmutationInventory.updateOutputs();
			} else {
				this.textBoxFilter.mouseClicked(mouseX, mouseY, mouseButton);
			}
		} else {
			this.textBoxFilter.mouseClicked(mouseX, mouseY, mouseButton);
		}

		boolean isShift = GuiScreen.isShiftKeyDown();

		// 左侧按键区域
		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 16, 9, 9)) {
			PacketHandler.sendToServer(new ArcaneTabletButtonPKT(isShift ? 1 : 0));
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 26, 9, 9)) {
			PacketHandler.sendToServer(new ArcaneTabletButtonPKT(isShift ? 3 : 2));
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 36, 9, 9)) {
			this.textBoxFilter.setFocused(true);
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 61, 9, 9)) {
			PacketHandler.sendToServer(new ArcaneTabletButtonPKT(4));
		}

		// 右侧转化桌翻页按键
		if (isMouseOver(mouseX, mouseY, guiLeft + 83, guiTop + 20, 18, 18)) {
			if (container.transmutationInventory.searchpage != 0) {
				container.transmutationInventory.searchpage--;
				container.transmutationInventory.updateOutputs();
			}
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 227, guiTop + 20, 18, 18)) {
			container.transmutationInventory.searchpage++;
			container.transmutationInventory.updateOutputs();
		}
	}

	@Override
	protected void keyTyped(char c, int keyCode) {
		if (this.textBoxFilter.isFocused()) {
			if (keyCode == Keyboard.KEY_ESCAPE) {
				this.textBoxFilter.setFocused(false);
			} else {
				this.textBoxFilter.textboxKeyTyped(c, keyCode);
				container.transmutationInventory.filter = this.textBoxFilter.getText().toLowerCase();
				container.transmutationInventory.searchpage = 0;
				container.transmutationInventory.updateOutputs();
			}
		} else {
			super.keyTyped(c, keyCode);
		}
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1F, 1F, 1F, 1F);
		this.mc.renderEngine.bindTexture(texture);

		// 画主体
		this.drawTexturedModalRect(guiLeft + 76, guiTop, 0, 0, 176, ySize);
		// 画左侧 3x3 区域
		this.drawTexturedModalRect(guiLeft + 1, guiTop + 10, 180, 32, 76, 89);
		// 画合成箭头
		this.drawTexturedModalRect(guiLeft + 26, guiTop + 76, 177, 19, 18, 12);

		// 渲染原生按键高亮贴图
		boolean isShift = GuiScreen.isShiftKeyDown();

		if (isMouseOver(mouseX, mouseY, guiLeft + 83, guiTop + 20, 18, 18)) {
			this.drawTexturedModalRect(guiLeft + 83, guiTop + 20, 196, 0, 18, 18);
		}
		if (isMouseOver(mouseX, mouseY, guiLeft + 227, guiTop + 20, 18, 18)) {
			this.drawTexturedModalRect(guiLeft + 227, guiTop + 20, 215, 0, 18, 18);
		}
		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 16, 9, 9)) {
			this.drawTexturedModalRect(guiLeft + 5, guiTop + 16, 234, 0, 9, 9);
		}
		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 26, 9, 9)) {
			this.drawTexturedModalRect(guiLeft + 5, guiTop + 26, 234, 0, 9, 9);
		}
		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 36, 9, 9)) {
			this.drawTexturedModalRect(guiLeft + 5, guiTop + 36, 234, 0, 9, 9);
		}
		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 61, 9, 9)) {
			this.drawTexturedModalRect(guiLeft + 5, guiTop + 61, 234, isShift ? 10 : 0, 9, 9);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		String emc = "EMC: " + moze_intel.projecte.utils.Constants.EMC_FORMATTER.get().format(container.transmutationInventory.emc);
		int fontWidth = this.fontRendererObj.getStringWidth(emc);
		this.fontRendererObj.drawString(emc, 76 + (176 - fontWidth) / 2, -10, 0xFFFFFF);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);

		this.textBoxFilter.drawTextBox();

		List<String> tooltip = new ArrayList<String>();
		boolean isShift = GuiScreen.isShiftKeyDown();

		if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 16, 9, 9)) {
			tooltip.add("Rotate " + (isShift ? "(Counter-Clockwise)" : "(Clockwise)"));
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 26, 9, 9)) {
			tooltip.add(isShift ? "Spread" : "Balance");
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 36, 9, 9)) {
			tooltip.add("Focus Search Box");
		} else if (isMouseOver(mouseX, mouseY, guiLeft + 5, guiTop + 61, 9, 9)) {
			tooltip.add("Clear Crafting Grid");
		}

		if (!tooltip.isEmpty()) {
			this.drawHoveringText(tooltip, mouseX, mouseY, this.fontRendererObj);
		}
	}
}
