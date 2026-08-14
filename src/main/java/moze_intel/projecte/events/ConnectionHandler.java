package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import moze_intel.projecte.handlers.PlayerChecks;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.CheckUpdatePKT;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class ConnectionHandler {
	@SubscribeEvent
	public void playerConnect(PlayerLoggedInEvent event) {
		// 类型校验，提取局部变量避免多次强制类型转换
		if (event.player instanceof EntityPlayerMP playerMP) {
			PacketHandler.sendFragmentedEmcPacket(playerMP);
			PacketHandler.sendTo(new CheckUpdatePKT(), playerMP);
		}

		PlayerTimers.registerPlayer(event.player);
	}

	@SubscribeEvent
	public void playerDisconnect(PlayerLoggedOutEvent event) {
		EntityPlayer player = event.player;
		PlayerTimers.removePlayer(player);

		PELogger.logInfo(String.format("Removing %s from scheduled timers: Player disconnected.", player.getCommandSenderName()));

		// 增加类型校验，防止假玩家导致的潜在强转崩溃
		if (player instanceof EntityPlayerMP playerMP)
			PlayerChecks.removePlayerFromLists(playerMP);
	}
}
