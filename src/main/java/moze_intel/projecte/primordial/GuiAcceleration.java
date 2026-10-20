package moze_intel.projecte.primordial;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.*;
import net.minecraft.util.StatCollector;
import moze_intel.projecte.network.PacketHandler;
import org.lwjgl.input.Keyboard;

public final class GuiAcceleration extends GuiContainer {
    private final AccelerationContainer container;
    private GuiTextField multiplier;
    private int page, displayedRate = -1;
    public GuiAcceleration(EntityPlayer p) {
        super(new AccelerationContainer(p)); container = (AccelerationContainer) inventorySlots;
        xSize = 320; ySize = 272;
    }
    private String tr(String s) { return StatCollector.translateToLocal("pe.primordial." + s); }
    private int selectedRate() {
        try { int rate = Integer.parseInt(multiplier.getText()); return rate >= 2 && rate <= 1024 ? rate : -1; }
        catch (NumberFormatException ignored) { return -1; }
    }
    @Override public void initGui() {
        super.initGui(); buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        for (int i = 0; i < 7; i++) buttonList.add(new GuiButton(20 + i, guiLeft + 8 + i % 4 * 76, guiTop + 20 + i / 4 * 22, 74, 20, AccelerationSettings.Kind.values()[i].name()));
        multiplier = new GuiTextField(fontRendererObj, guiLeft + 8, guiTop + 70, 64, 18);
        multiplier.setMaxStringLength(4);
        displayedRate = Math.max(2, Math.min(1024, container.state.getInteger("rate")));
        multiplier.setText(Integer.toString(displayedRate));
        buttonList.add(new GuiButton(1, guiLeft + 80, guiTop + 68, 74, 20, tr("apply_rate")));
        buttonList.add(new GuiButton(2, guiLeft + 8, guiTop + 116, 94, 20, tr("toggle")));
        buttonList.add(new GuiButton(3, guiLeft + 108, guiTop + 116, 94, 20, tr("confirm")));
        buttonList.add(new GuiButton(4, guiLeft + 208, guiTop + 116, 98, 20, tr("clear")));
        for (int i = 0; i < 5; i++) buttonList.add(new GuiButton(100 + i, guiLeft + 284, guiTop + 149 + i * 20, 22, 18, "X"));
        buttonList.add(new GuiButton(5, guiLeft + 8, guiTop + 252, 36, 18, "<"));
        buttonList.add(new GuiButton(6, guiLeft + 50, guiTop + 252, 36, 18, ">"));
    }
    @Override protected void actionPerformed(GuiButton b) {
        if (b.id == 5 || b.id == 6) { page = Math.max(0, Math.min(51, page + (b.id == 5 ? -1 : 1))); return; }
        int action = b.id, value = 0;
        if (b.id == 1) { value = selectedRate(); if (value < 0) return; multiplier.setFocused(false); }
        else if (b.id >= 100) { action = 5; value = page * 5 + b.id - 100; }
        else if (b.id >= 20) { action = 0; value = b.id - 20; }
        PacketHandler.sendToServer(new PrimordialActionPacket(container.windowId, action, value, container.state.getCompoundTag("confirmation").getString("token")));
    }
    @Override public void updateScreen() {
        super.updateScreen(); multiplier.updateCursorCounter();
        int rate = Math.max(2, Math.min(1024, container.state.getInteger("rate")));
        if (rate != displayedRate && !multiplier.isFocused()) { multiplier.setText(Integer.toString(rate)); displayedRate = rate; }
        NBTTagCompound c = container.state.getCompoundTag("confirmation");
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (b.id == 1) b.enabled = selectedRate() >= 2;
            if (b.id == 2) b.enabled = selectedRate() == rate;
            if (b.id == 3) b.enabled = selectedRate() == rate && c.hasKey("token") && container.state.getLong("now") - c.getLong("time") >= 20;
            if (b.id >= 100) b.enabled = page * 5 + b.id - 100 < container.state.getTagList("targets", 10).tagCount();
        }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button); multiplier.mouseClicked(x, y, button);
    }
    @Override protected void keyTyped(char c, int key) {
        if (multiplier.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                int rate = selectedRate();
                if (rate >= 2) { PacketHandler.sendToServer(new PrimordialActionPacket(container.windowId, 1, rate)); multiplier.setFocused(false); }
                return;
            }
            if (key != Keyboard.KEY_ESCAPE) { multiplier.textboxKeyTyped(c, key); return; }
        }
        super.keyTyped(c, key);
    }
    @Override public void onGuiClosed() { super.onGuiClosed(); Keyboard.enableRepeatEvents(false); }
    @Override protected void drawGuiContainerBackgroundLayer(float p, int x, int y) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        multiplier.drawTextBox();
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRendererObj.drawString(tr("accelerator") + " " + Math.max(2, container.state.getInteger("rate")) + "x", 8, 6, 0x404040);
        fontRendererObj.drawString(tr("rate_range"), 162, 74, 0x404040);
        int rate = selectedRate();
        if (rate < 0) fontRendererObj.drawString(tr("invalid_rate"), 8, 94, 0xaa0000);
        else if (container.state.hasKey("costCoefficient")) {
            double cost = AccelerationSettings.costPerSecond(rate, container.state.getDouble("costCoefficient"));
            fontRendererObj.drawString(StatCollector.translateToLocalFormatted("pe.primordial.cost_preview", String.format(java.util.Locale.ROOT, "%,.2f", cost)), 8, 94, 0x404040);
        }
        if (container.state.hasKey("confirmation")) fontRendererObj.drawString(tr("high_rate"), 8, 138, 0xaa0000);
        NBTTagList list = container.state.getTagList("targets", 10);
        for (int i = 0; i < 5 && page * 5 + i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(page * 5 + i);
            String line = e.getString("kind") + " " + e.getInteger("dim") + ":" + e.getInteger("x") + "," + e.getInteger("y") + "," + e.getInteger("z") + " " + e.getInteger("rate") + "x " + (e.getBoolean("enabled") ? ">" : "||");
            fontRendererObj.drawString(line, 8, 154 + i * 20, 0x404040);
        }
    }
}
