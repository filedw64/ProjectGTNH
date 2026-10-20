package moze_intel.projecte.primordial;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import moze_intel.projecte.network.PacketHandler;
import org.lwjgl.opengl.GL11;

public final class GuiConfigurator extends GuiContainer {
    private final ConfiguratorContainer container;
    private GuiTextField amount, interval;
    private int edited = -1;
    public GuiConfigurator(EntityPlayer p, int x, int y, int z, boolean inventory) { super(new ConfiguratorContainer(p, x, y, z, inventory)); container = (ConfiguratorContainer) inventorySlots; xSize = 350; ySize = 284; }
    private String tr(String s) { return StatCollector.translateToLocal("pe.primordial." + s); }
    @Override public void initGui() {
        super.initGui(); buttonList.clear();
        buttonList.add(new GuiButton(1, guiLeft + 8, guiTop + 108, 76, 20, tr("enabled")));
        buttonList.add(new GuiButton(2, guiLeft + 88, guiTop + 108, 82, 20, tr("mode")));
        buttonList.add(new GuiButton(3, guiLeft + 8, guiTop + 155, 76, 20, tr("apply")));
        buttonList.add(new GuiButton(8, guiLeft + 88, guiTop + 155, 82, 20, tr("unbind")));
        for (int i = 0; i < 7; i++) buttonList.add(new GuiButton(20 + i, guiLeft + 180 + i % 4 * 40, guiTop + 20 + i / 4 * 22, 38, 20, i == 6 ? "INT" : new String[] { "D", "U", "N", "S", "W", "E" }[i]));
        for (int i = 0; i < 27; i++) buttonList.add(new GuiButton(100 + i, guiLeft + 180 + i % 3 * 52, guiTop + 70 + i / 3 * 18, 50, 17, ""));
        buttonList.add(new GuiButton(30, guiLeft + 180, guiTop + 238, 50, 20, "<"));
        buttonList.add(new GuiButton(31, guiLeft + 286, guiTop + 238, 50, 20, ">"));
        if (!container.config.playerInventory()) for (int i = 0; i < 5; i++) buttonList.add(new GuiButton(40 + i, guiLeft + 8 + i * 55, guiTop + 262, 54, 18, tr(new String[] { "rf_in", "rf_out", "mana_in", "mana_out", "eu_in" }[i])));
        amount = new GuiTextField(fontRendererObj, guiLeft + 8, guiTop + 134, 76, 17); amount.setMaxStringLength(10);
        interval = new GuiTextField(fontRendererObj, guiLeft + 88, guiTop + 134, 82, 17); interval.setMaxStringLength(5);
    }
    private void send(int action, int value) { PacketHandler.sendToServer(new PrimordialActionPacket(container.windowId, action, value)); }
    @Override protected void actionPerformed(GuiButton b) {
        if (b.id >= 100) send(7, b.id - 100);
        else if (b.id >= 40 && b.id < 45) send(0, b.id - 40 + 36);
        else if (b.id >= 20 && b.id <= 26) send(5, b.id - 20);
        else if (b.id == 30 || b.id == 31) send(6, container.page + (b.id == 30 ? -1 : 1));
        else if (b.id == 3) { try { send(3, Integer.parseInt(amount.getText())); send(4, Integer.parseInt(interval.getText())); } catch (NumberFormatException ignored) {} }
        else send(b.id, 0);
    }
    @Override public void updateScreen() {
        super.updateScreen(); MachineConfig.Rule r = container.config.rules[container.selected];
        if (edited != container.selected || !amount.isFocused() && !interval.isFocused()) { amount.setText(Integer.toString(r.amount)); interval.setText(Integer.toString(r.interval)); edited = container.selected; }
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == 1) b.displayString = tr(r.enabled ? "enabled" : "paused");
            if (b.id == 2) b.displayString = tr(r.keep ? "keep" : "fixed");
            if (b.id >= 100) { int i = container.page * 27 + b.id - 100; b.enabled = i < container.available.tagCount();
                if (b.enabled) { net.minecraft.nbt.NBTTagCompound port = container.available.getCompoundTagAt(i); boolean bound = false; for (int j = 0; j < r.ports.tagCount(); j++) if (port.equals(r.ports.getCompoundTagAt(j))) bound = true; b.displayString = (bound ? "* " : "") + port.getInteger("index"); } else b.displayString = "-";
            }
        }
        amount.updateCursorCounter(); interval.updateCursorCounter();
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button); amount.mouseClicked(x, y, button); interval.mouseClicked(x, y, button);
        if (button == 0 && x >= guiLeft + 8 && x < guiLeft + 170 && y >= guiTop + 32 && y < guiTop + 104) send(0, (y - guiTop - 32) / 18 * 9 + (x - guiLeft - 8) / 18);
    }
    @Override protected void keyTyped(char c, int key) { if (amount.textboxKeyTyped(c, key) || interval.textboxKeyTyped(c, key)) return; super.keyTyped(c, key); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        for (Object o : inventorySlots.inventorySlots) { net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) o; drawRect(guiLeft + slot.xDisplayPosition - 1, guiTop + slot.yDisplayPosition - 1, guiLeft + slot.xDisplayPosition + 17, guiTop + slot.yDisplayPosition + 17, 0xff555555); }
        int s = container.selected; if (s < 36) drawRect(guiLeft + 7 + s % 9 * 18, guiTop + 31 + s / 9 * 18, guiLeft + 25 + s % 9 * 18, guiTop + 49 + s / 9 * 18, 0x6699ff99);
        amount.drawTextBox(); interval.drawTextBox(); GL11.glColor4f(1, 1, 1, 1);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRendererObj.drawString(tr("configurator"), 8, 6, 0x404040);
        String mode = container.config.playerInventory() ? "inventory" : container.selected >= 36 ? new String[] { "rf_in", "rf_out", "mana_in", "mana_out", "eu_in" }[container.selected - 36] : container.selected < 9 ? "item_in" : container.selected < 18 ? "item_out" : container.selected < 27 ? "fluid_in" : "fluid_out";
        fontRendererObj.drawString(tr(mode) + " #" + container.selected, 8, 20, 0x404040);
        fontRendererObj.drawString("#" + container.page + " / " + container.side, 240, 244, 0x404040);
    }
}
