package moze_intel.projecte.gameObjs.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import moze_intel.projecte.gameObjs.container.AlchBagPageContainer;
import moze_intel.projecte.config.ProjectEConfig;

public class GUIAlchBagPage extends GuiContainer {
	private static final ResourceLocation texture = new ResourceLocation("textures/gui/container/dispenser.png");

	public GUIAlchBagPage(AlchBagPageContainer container) {
		super(container);
		this.xSize = 176;
		this.ySize = 80;
	}

	@Override
	public void initGui() {
		super.initGui();
		// 根据 Config 动态生成按钮（最大支持10页，防止按钮溢出屏幕）
		int pages = Math.min(10, ProjectEConfig.alchBagPages);
		for (int i = 0; i < pages; i++) {
			// 生成页码按钮
			this.buttonList.add(new GuiButton(i, guiLeft + 15 + (i % 5) * 30, guiTop + 25 + (i / 5) * 25, 25, 20, String.valueOf(i + 1)));
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		// 向服务器发送点击事件
		this.mc.playerController.sendEnchantPacket(this.inventorySlots.windowId, button.id);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
		this.mc.getTextureManager().bindTexture(texture);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int x, int y) {
		this.fontRendererObj.drawString("Select Page", 8, 6, 4210752);
	}
}
