package moze_intel.projecte.primordial;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import net.minecraftforge.client.event.MouseEvent;
import moze_intel.projecte.network.PacketHandler;

public final class AccelerationClientEvents {
    private final KeyBinding management = new KeyBinding("pe.primordial.management", Keyboard.KEY_X, "key.categories.projecte");
    public AccelerationClientEvents() { ClientRegistry.registerKeyBinding(management); }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent e) { if (management.isPressed()) send(90); }
    @SubscribeEvent public void mouse(MouseEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.dwheel != 0 && mc.thePlayer != null && mc.thePlayer.isSneaking() && held()) { send(91); e.setCanceled(true); }
    }
    private boolean held() { Minecraft mc = Minecraft.getMinecraft(); ItemStack s = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem(); return s != null && s.getItem() instanceof EmcAcceleratorItem; }
    private void send(int action) { if (Minecraft.getMinecraft().currentScreen == null && held()) PacketHandler.sendToServer(new PrimordialActionPacket(-1, action, 0)); }
}
