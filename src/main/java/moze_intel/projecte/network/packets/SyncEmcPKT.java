package moze_intel.projecte.network.packets;

import com.google.common.collect.Maps;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;

import java.util.List;

public class SyncEmcPKT implements IMessage
{
	private int packetNum;
	private Object[] data;

	public SyncEmcPKT() {}

	public SyncEmcPKT(int packetNum, List<Object[]> arrayList)
	{
		this.packetNum = packetNum;
		data = arrayList.toArray();
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		packetNum = buf.readInt();
		int size = buf.readInt();
		data = new Object[size];

		for (int i = 0; i < size; i++)
		{
			Object[] array = new Object[4];

			for (int j = 0; j < 3; j++)
			{
                array[j] = buf.readInt();
			}
            array[3] = buf.readDouble();
			data[i] = array;
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(packetNum);
		buf.writeInt(data.length);

		for (Object obj : data)
		{
			Object[] array = (Object[]) obj;

			for (int i = 0; i < 3; i++)
			{
				buf.writeInt((int) array[i]);
			}
            buf.writeDouble((double) array[3]);
		}
	}

	public static class Handler implements IMessageHandler<SyncEmcPKT, IMessage>
	{
		@Override
		public IMessage onMessage(final SyncEmcPKT pkt, MessageContext ctx)
		{
			if (pkt.packetNum == 0)
			{
				PELogger.logInfo("Receiving EMC data from server.");

				EMCMapper.emc.clear();
				EMCMapper.emc = Maps.newLinkedHashMap();
			}

			for (Object obj : pkt.data)
			{
                Object[] array = (Object[]) obj;

				SimpleStack stack = new SimpleStack((int) array[0], (int) array[1], (int) array[2]);

				if (stack.isValid())
				{
					EMCMapper.emc.put(stack, (double) array[3]);
				}
			}

			if (pkt.packetNum == -1)
			{
				PELogger.logInfo("Received all packets!");

				Transmutation.cacheFullKnowledge();
				FuelMapper.loadMap();
			}
			return null;
		}
	}
}
