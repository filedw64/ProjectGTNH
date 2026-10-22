package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import moze_intel.projecte.handlers.PlayerChecks;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.handlers.StoneFoodStats;
import net.minecraft.entity.player.EntityPlayerMP;

public class TickEvents
{
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END)
			PlayerTimers.update();
	}

	@SubscribeEvent
	public void playerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.side == Side.SERVER)
			PlayerChecks.update((EntityPlayerMP) event.player);
	}

	// 双端都要替换，否则客户端 canEat 仍为 false，没有进食动画且与服务端不同步
	@SubscribeEvent
	public void onPlayerTickBothSides(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END)
			StoneFoodStats.ensureApplied(event.player);
	}
}
