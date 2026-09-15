package moze_intel.projecte.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FluidSimpleStack;
import moze_intel.projecte.emc.NBTSimpleStack;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.network.packets.CheckUpdatePKT;
import moze_intel.projecte.network.packets.CollectorSyncPKT;
import moze_intel.projecte.network.packets.CondenserSyncPKT;
import moze_intel.projecte.network.packets.KeyPressPKT;
import moze_intel.projecte.network.packets.KnowledgeChangePKT;
import moze_intel.projecte.network.packets.KnowledgeClearPKT;
import moze_intel.projecte.network.packets.KnowledgeSyncPKT;
import moze_intel.projecte.network.packets.OrientationSyncPKT;
import moze_intel.projecte.network.packets.ParticlePKT;
import moze_intel.projecte.network.packets.RelaySyncPKT;
import moze_intel.projecte.network.packets.SearchUpdatePKT;
import moze_intel.projecte.network.packets.SetFlyPKT;
import moze_intel.projecte.network.packets.StepHeightPKT;
import moze_intel.projecte.network.packets.SwingItemPKT;
import moze_intel.projecte.network.packets.SyncBagDataPKT;
import moze_intel.projecte.network.packets.SyncEmcPKT;
import moze_intel.projecte.network.packets.SyncPedestalPKT;
import moze_intel.projecte.network.packets.UpdateGemModePKT;
import moze_intel.projecte.network.packets.ArcaneTabletButtonPKT;
import moze_intel.projecte.network.packets.ArcaneRecipeTransferPKT;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class PacketHandler
{
	private static final int MAX_PKT_SIZE = 256;
	private static final SimpleNetworkWrapper HANDLER = NetworkRegistry.INSTANCE.newSimpleChannel("projecte");

	public static void register()
	{
		HANDLER.registerMessage(SyncEmcPKT.Handler.class, SyncEmcPKT.class, 0, Side.CLIENT);
		HANDLER.registerMessage(KeyPressPKT.Handler.class, KeyPressPKT.class, 1, Side.SERVER);
		HANDLER.registerMessage(ParticlePKT.Handler.class, ParticlePKT.class, 2, Side.CLIENT);
		HANDLER.registerMessage(SwingItemPKT.Handler.class, SwingItemPKT.class, 3, Side.CLIENT);
		HANDLER.registerMessage(StepHeightPKT.Handler.class, StepHeightPKT.class, 4, Side.CLIENT);
		HANDLER.registerMessage(SetFlyPKT.Handler.class, SetFlyPKT.class, 5, Side.CLIENT);
		HANDLER.registerMessage(KnowledgeSyncPKT.Handler.class, KnowledgeSyncPKT.class, 6, Side.CLIENT);
		HANDLER.registerMessage(CondenserSyncPKT.Handler.class, CondenserSyncPKT.class, 8, Side.CLIENT);
		HANDLER.registerMessage(CollectorSyncPKT.Handler.class, CollectorSyncPKT.class, 9, Side.CLIENT);
		HANDLER.registerMessage(RelaySyncPKT.Handler.class, RelaySyncPKT.class, 10, Side.CLIENT);
		HANDLER.registerMessage(CheckUpdatePKT.Handler.class, CheckUpdatePKT.class, 11, Side.CLIENT);
		HANDLER.registerMessage(SyncBagDataPKT.Handler.class, SyncBagDataPKT.class, 12, Side.CLIENT);
		HANDLER.registerMessage(SearchUpdatePKT.Handler.class, SearchUpdatePKT.class, 13, Side.SERVER);
		HANDLER.registerMessage(KnowledgeClearPKT.Handler.class, KnowledgeClearPKT.class, 14, Side.CLIENT);
		HANDLER.registerMessage(OrientationSyncPKT.Handler.class, OrientationSyncPKT.class, 15, Side.CLIENT);
		HANDLER.registerMessage(UpdateGemModePKT.Handler.class, UpdateGemModePKT.class, 16, Side.SERVER);
		HANDLER.registerMessage(SyncPedestalPKT.Handler.class, SyncPedestalPKT.class, 17, Side.CLIENT);
		HANDLER.registerMessage(KnowledgeChangePKT.Handler.class, KnowledgeChangePKT.class, 18, Side.CLIENT);
		HANDLER.registerMessage(ArcaneTabletButtonPKT.class, ArcaneTabletButtonPKT.class, 19, Side.SERVER);
		HANDLER.registerMessage(ArcaneRecipeTransferPKT.class, ArcaneRecipeTransferPKT.class, 20, Side.SERVER);


	}

	public static Packet getMCPacket(IMessage message)
	{
		return HANDLER.getPacketFrom(message);
	}

	public static void sendFragmentedEmcPacket(EntityPlayerMP player)
	{
		ArrayList<Object[]> list = new ArrayList<>();
		int counter = 0;

		// Copy constructor to prevent race condition CME in SP
		for (Map.Entry<SimpleStack, Double> entry : new HashMap<>(EMCMapper.emc).entrySet()) {
			SimpleStack stack = entry.getKey();

			if (stack == null)
				continue;

            Object[] data;
            if (stack instanceof NBTSimpleStack nbtss)
                data = new Object[] {stack.id, stack.damage, entry.getValue(), nbtss.nbt};
			else if (stack instanceof FluidSimpleStack)
				data = new Object[] {stack.id, entry.getValue()};
            else data = new Object[] {stack.id, stack.damage, entry.getValue()};

            list.add(data);

			if (list.size() >= MAX_PKT_SIZE)
			{
				PacketHandler.sendTo(new SyncEmcPKT(counter, list), player);
				list.clear();
				counter++;
			}
		}

        PacketHandler.sendTo(new SyncEmcPKT(-1, list), player);
        list.clear();
        counter++;

		PELogger.logInfo("Sent EMC data packets to: " + player.getCommandSenderName());
		PELogger.logDebug("Total packets: " + counter);
	}

	public static void sendFragmentedEmcPacketToAll()
	{
		ArrayList<Object[]> list = new ArrayList<>();
		int counter = 0;

		// Copy constructor to prevent race condition CME in SP
		for (Map.Entry<SimpleStack, Double> entry : new HashMap<>(EMCMapper.emc).entrySet()) {
			SimpleStack stack = entry.getKey();

			if (stack == null)
				continue;

            Object[] data;
			if (stack instanceof NBTSimpleStack nbtss)
				data = new Object[] {stack.id, stack.damage, entry.getValue(), nbtss.nbt};
			else if (stack instanceof FluidSimpleStack)
				data = new Object[] {stack.id, entry.getValue()};
            else data = new Object[] {stack.id, stack.damage, entry.getValue()};

			list.add(data);

			if (list.size() >= MAX_PKT_SIZE)
			{
				PacketHandler.sendToAll(new SyncEmcPKT(counter, list));
				list.clear();
				counter++;
			}
		}

        PacketHandler.sendToAll(new SyncEmcPKT(-1, list));
        list.clear();
        counter++;

		PELogger.logInfo("Sent EMC data packets to all players.");
		PELogger.logDebug("Total packets per player: " + counter);
	}

	/**
	 * Sends a packet to the server.<br>
	 * Must be called Client side.
	 */
	public static void sendToServer(IMessage msg) {
		HANDLER.sendToServer(msg);
	}

	/**
	 * Sends a packet to all the clients.<br>
	 * Must be called Server side.
	 */
	public static void sendToAll(IMessage msg) {
		HANDLER.sendToAll(msg);
	}

	/**
	 * Send a packet to all players around a specific point.<br>
	 * Must be called Server side.
	 */
	public static void sendToAllAround(IMessage msg, TargetPoint point) {
		HANDLER.sendToAllAround(msg, point);
	}

	/**
	 * Send a packet to a specific player.<br>
	 * Must be called Server side.
	 */
	public static void sendTo(IMessage msg, EntityPlayerMP player) {
		if (!(player instanceof FakePlayer))
			HANDLER.sendTo(msg, player);
	}

	/**
	 * Send a packet to all the players in the specified dimension.<br>
	 * Must be called Server side.
	 */
	public static void sendToDimension(IMessage msg, int dimension) {
		HANDLER.sendToDimension(msg, dimension);
	}
}
