package projectgtnh.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import projectgtnh.network.PacketHandler;
import projectgtnh.network.packets.KeyPressPKT;
import projectgtnh.utils.ClientKeyHelper;
import net.minecraft.client.settings.KeyBinding;

@SideOnly(Side.CLIENT)
public class KeyPressEvent 
{
	@SubscribeEvent
	public void keyPress(KeyInputEvent event)
	{
		for (KeyBinding k : ClientKeyHelper.mcToPe.keySet())
		{
			if (k.isPressed())
			{
				PacketHandler.sendToServer(new KeyPressPKT(ClientKeyHelper.mcToPe.get(k)));
			}
		}
	}
}
