package moze_intel.projecte.primordial;

import java.util.concurrent.ConcurrentLinkedQueue;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

public final class PrimordialEvents {
    private static final ConcurrentLinkedQueue<Runnable> actions = new ConcurrentLinkedQueue<>();
    public static boolean enqueue(Runnable action) { if (actions.size() >= 1024) return false; actions.add(action); return true; }
    public static EntityPlayerMP player(UUID id) {
        MinecraftServer server = MinecraftServer.getServer(); if (server == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList) { EntityPlayerMP p = (EntityPlayerMP) o; if (p.getUniqueID().equals(id)) return p; } return null;
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 256; i++) { Runnable action = actions.poll(); if (action == null) break; action.run(); }
        MachineTasks.tick(); AccelerationService.beginTick();
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o; PrimordialSavedData.get().settle(p);
            for (int i = 0; i < 36; i++) { ItemStack s = p.inventory.getStackInSlot(i); if (s != null && s.getItem() instanceof EmcAcceleratorItem) AccelerationService.tick(p, s); }
            if (p.worldObj.getTotalWorldTime() % 10 == 0) EmcTransactions.sync(p);
        }
    }
    public static void clear() { actions.clear(); MachineTasks.clear(); AccelerationService.clear(); EmcTransactions.clear(); }
}
