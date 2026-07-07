package projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import projecte.handlers.PlayerChecks;
import projecte.handlers.PlayerTimers;
import projecte.network.PacketHandler;
import projecte.network.packets.CheckUpdatePKT;
import projecte.utils.PELogger;

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
