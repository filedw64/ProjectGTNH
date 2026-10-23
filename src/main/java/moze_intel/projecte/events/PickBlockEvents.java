package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.PickBlockPKT;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

@SideOnly(Side.CLIENT)
public class PickBlockEvents
{
	private boolean lastPressed;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event)
	{
		if (event.phase != TickEvent.Phase.END) return;

		Minecraft mc = Minecraft.getMinecraft();
		// 用原始按键状态做上升沿检测，按住不连发
		boolean pressed = mc.gameSettings.keyBindPickBlock.getIsKeyPressed();
		boolean rising = pressed && !lastPressed;
		lastPressed = pressed;
		if (!rising) return;

		if (mc.thePlayer == null || mc.currentScreen != null) return;

		MovingObjectPosition mop = mc.objectMouseOver;
		if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

		PacketHandler.sendToServer(new PickBlockPKT(mop.blockX, mop.blockY, mop.blockZ, mop.sideHit));
	}
}
