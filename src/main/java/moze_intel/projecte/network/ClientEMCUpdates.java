package moze_intel.projecte.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.concurrent.ConcurrentLinkedQueue;
import moze_intel.projecte.PECore;

/** 1.7.10 main-thread dispatch; avoids assuming modern scheduled-task APIs. */
@SideOnly(Side.CLIENT)
public final class ClientEMCUpdates {
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    public static void enqueue(Runnable task) { QUEUE.add(task); }
    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { QUEUE.clear(); }
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || PECore.proxy.getClientPlayer() == null) return;
        Runnable task;
        int budget = 256;
        while (budget-- > 0 && (task = QUEUE.poll()) != null) task.run();
    }
}
