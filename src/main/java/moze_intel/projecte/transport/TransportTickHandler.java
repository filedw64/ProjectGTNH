package moze_intel.projecte.transport;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * 服务端 tick 驱动：只在这里调用网络 tick，客户端不跑物流逻辑。
 * START / END 成对喂给 LogisticsScheduler：END 里算出的间隔必须赶在 tickAll 之前就绪，
 * 物流才会用上本 tick 的最新间隔。这是物流唯一的驱动入口 —— 没有任何方块实体自己驱动物流。
 */
public class TransportTickHandler
{
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.START)
			LogisticsScheduler.onTickStart();
		else
		{
			LogisticsScheduler.onTickEnd();
			MatterNetworkManager.INSTANCE.tickAll();
		}
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event)
	{
		if (event.world != null && !event.world.isRemote)
			MatterNetworkManager.INSTANCE.unloadWorld(event.world);
	}
}
