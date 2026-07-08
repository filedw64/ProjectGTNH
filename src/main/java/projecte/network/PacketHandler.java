package projecte.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraftforge.common.util.FakePlayer;
import projecte.emc.EMCMapper;
import projecte.emc.SimpleStack;
import projecte.network.packets.CheckUpdatePKT;
import projecte.network.packets.CollectorSyncPKT;
import projecte.network.packets.CondenserSyncPKT;
import projecte.network.packets.KeyPressPKT;
import projecte.network.packets.KnowledgeClearPKT;
import projecte.network.packets.KnowledgeSyncPKT;
import projecte.network.packets.OrientationSyncPKT;
import projecte.network.packets.ParticlePKT;
import projecte.network.packets.RelaySyncPKT;
import projecte.network.packets.SearchUpdatePKT;
import projecte.network.packets.SetFlyPKT;
import projecte.network.packets.StepHeightPKT;
import projecte.network.packets.SwingItemPKT;
import projecte.network.packets.SyncBagDataPKT;
import projecte.network.packets.SyncEmcPKT;
import projecte.network.packets.SyncPedestalPKT;
import projecte.network.packets.UpdateGemModePKT;
import projecte.utils.PELogger;

import java.util.ArrayList;
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
	}

	public static Packet getMCPacket(IMessage message)
	{
		return HANDLER.getPacketFrom(message);
	}

	public static void sendFragmentedEmcPacket(EntityPlayerMP player)
	{
		ArrayList<Object[]> list = Lists.newArrayList();
		int counter = 0;

		for (Map.Entry<SimpleStack, Double> entry : Maps.newLinkedHashMap(EMCMapper.emc).entrySet()) // Copy constructor to prevent race condition CME in SP
		{
			SimpleStack stack = entry.getKey();

			if (stack == null)
			{
				continue;
			}

            Object[] data = new Object[] {stack.id, stack.qnty, stack.damage, entry.getValue()};
			list.add(data);

			if (list.size() >= MAX_PKT_SIZE)
			{
				PacketHandler.sendTo(new SyncEmcPKT(counter, list), player);
				list.clear();
				counter++;
			}
		}

		if (!list.isEmpty())
		{
			PacketHandler.sendTo(new SyncEmcPKT(-1, list), player);
			list.clear();
			counter++;
		}

		PELogger.logInfo("Sent EMC data packets to: " + player.getCommandSenderName());
		PELogger.logDebug("Total packets: " + counter);
	}

	public static void sendFragmentedEmcPacketToAll()
	{
		ArrayList<Object[]> list = Lists.newArrayList();
		int counter = 0;

		for (Map.Entry<SimpleStack, Double> entry : Maps.newLinkedHashMap(EMCMapper.emc).entrySet()) // Copy constructor to prevent race condition CME in SP
		{
			SimpleStack stack = entry.getKey();

			if (stack == null)
			{
				continue;
			}

            Object[] data = new Object[] {stack.id, stack.qnty, stack.damage, entry.getValue()};
			list.add(data);

			if (list.size() >= MAX_PKT_SIZE)
			{
				PacketHandler.sendToAll(new SyncEmcPKT(counter, list));
				list.clear();
				counter++;
			}
		}

		if (!list.isEmpty())
		{
			PacketHandler.sendToAll(new SyncEmcPKT(-1, list));
			list.clear();
			counter++;
		}

		PELogger.logInfo("Sent EMC data packets to all players.");
		PELogger.logDebug("Total packets per player: " + counter);
	}

	/**
	 * Sends a packet to the server.<br>
	 * Must be called Client side.
	 */
	public static void sendToServer(IMessage msg)
	{
		HANDLER.sendToServer(msg);
	}

	/**
	 * Sends a packet to all the clients.<br>
	 * Must be called Server side.
	 */
	public static void sendToAll(IMessage msg)
	{
		HANDLER.sendToAll(msg);
	}

	/**
	 * Send a packet to all players around a specific point.<br>
	 * Must be called Server side.
	 */
	public static void sendToAllAround(IMessage msg, TargetPoint point)
	{
		HANDLER.sendToAllAround(msg, point);
	}

	/**
	 * Send a packet to a specific player.<br>
	 * Must be called Server side.
	 */
	public static void sendTo(IMessage msg, EntityPlayerMP player)
	{
		if (!(player instanceof FakePlayer))
		{
			HANDLER.sendTo(msg, player);
		}
	}

	/**
	 * Send a packet to all the players in the specified dimension.<br>
	 *  Must be called Server side.
	 */
	public static void sendToDimension(IMessage msg, int dimension)
	{
		HANDLER.sendToDimension(msg, dimension);
	}
}
