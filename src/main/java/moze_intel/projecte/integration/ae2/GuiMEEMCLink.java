package moze_intel.projecte.integration.ae2;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

@SideOnly(Side.CLIENT)
public class GuiMEEMCLink extends GuiContainer {
    private final ContainerMEEMCLink container;

    public GuiMEEMCLink(InventoryPlayer inventory, TileMEEMCLink tile) {
        super(new ContainerMEEMCLink(inventory, tile));
        container = (ContainerMEEMCLink) inventorySlots;
        xSize = 176;
        ySize = 214;
    }

    private static String tr(String key) { return StatCollector.translateToLocal(key); }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new GuiButton(0, guiLeft + 8, guiTop + 20, 78, 18, ""));
        buttonList.add(new GuiButton(1, guiLeft + 90, guiTop + 20, 78, 18, ""));
        buttonList.add(new GuiButton(2, guiLeft + 8, guiTop + 40, 160, 18, ""));
        buttonList.add(new GuiButton(3, guiLeft + 8, guiTop + 108, 20, 18, "-"));
        buttonList.add(new GuiButton(4, guiLeft + 148, guiTop + 108, 20, 18, "+"));
        updateLabels();
    }

    private void updateLabels() {
        for (Object raw : buttonList) {
            GuiButton button = (GuiButton) raw;
            if (button.id == 0) button.displayString = tr("pe.ae2.access." + container.accessMode);
            if (button.id == 1) button.displayString = tr("pe.ae2.filter." + container.filterMode);
            if (button.id == 2) button.displayString = tr("pe.ae2.precision." + container.filterPrecision);
        }
    }

    @Override
    public void updateScreen() { super.updateScreen(); updateLabels(); }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.enabled) mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(tr("container.pe.me_emc_link"), 8, 6, 0x404040);
        String label = tr("pe.ae2.priority") + ": " + container.priority;
        fontRendererObj.drawString(label, (xSize - fontRendererObj.getStringWidth(label)) / 2, 113, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        for (Object raw : inventorySlots.inventorySlots) {
            net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) raw;
            int x = guiLeft + slot.xDisplayPosition, y = guiTop + slot.yDisplayPosition;
            drawRect(x - 1, y - 1, x + 17, y + 17, 0xff373737);
            drawRect(x, y, x + 16, y + 16, 0xff8b8b8b);
        }
    }
}
