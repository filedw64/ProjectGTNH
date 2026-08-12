package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.KeyPressPKT;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.PEKeybind;

import java.util.Map;

@SideOnly(Side.CLIENT)
public class KeyPressEvent
{
	@SubscribeEvent
	public void keyPress(KeyInputEvent event)
	{
		// 避免高频事件下的重复哈希查找开销
		for (Map.Entry<KeyBinding, PEKeybind> entry : ClientKeyHelper.mcToPe.entrySet())
		{
			if (entry.getKey().isPressed())
			{
				PacketHandler.sendToServer(new KeyPressPKT(entry.getValue()));
			}
		}
	}
}
