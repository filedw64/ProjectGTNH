package projectgtnh.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import projectgtnh.handlers.PlayerChecks;
import projectgtnh.handlers.PlayerTimers;
import projectgtnh.network.PacketHandler;
import projectgtnh.network.packets.CheckUpdatePKT;
import projectgtnh.utils.PELogger;
import net.minecraft.entity.player.EntityPlayerMP;

public class ConnectionHandler
{
	@SubscribeEvent
	public void playerConnect(PlayerLoggedInEvent event)
	{
		PacketHandler.sendFragmentedEmcPacket((EntityPlayerMP) event.player);
		PacketHandler.sendTo(new CheckUpdatePKT(), (EntityPlayerMP) event.player);

		PlayerTimers.registerPlayer(event.player);
		
	}

	@SubscribeEvent
	public void playerDisconnect(PlayerEvent.PlayerLoggedOutEvent event)
	{
		PlayerTimers.removePlayer(event.player);
		PELogger.logInfo("Removing " + event.player.getCommandSenderName() + " from scheduled timers: Player disconnected.");
		PlayerChecks.removePlayerFromLists(((EntityPlayerMP) event.player));
	}

}
