package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FluidSimpleStack;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.integration.GregTech.GTSimpleStack;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
            int arraylen = buf.readInt();
			Object[] array = new Object[arraylen];
			if (arraylen == 3) {
				array[0] = buf.readInt();
				array[1] = buf.readInt();
				array[2] = buf.readDouble();
				data[i] = array;
				continue;
			}
			for (int j = 0; j < 3; j++) {
                array[j] = buf.readInt();
			}
            array[3] = buf.readDouble();
            if (arraylen == 6) {
                int len1 = buf.readInt();
                byte[] bytes1 = new byte[len1];
                buf.readBytes(bytes1);
                int len2 = buf.readInt();
                byte[] bytes2 = new byte[len2];
                buf.readBytes(bytes2);
                array[4] = new String(bytes1, StandardCharsets.UTF_8);
                array[5] = new String(bytes2, StandardCharsets.UTF_8);
            }
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
            buf.writeInt(array.length);
			if (array.length == 3) {
				buf.writeInt((int) array[0]);
				buf.writeInt((int) array[1]);
				buf.writeDouble((double) array[2]);
				continue;
			}
			for (int i = 0; i < 3; i++) {
				buf.writeInt((int) array[i]);
			}
            buf.writeDouble((double) array[3]);
            if (array.length == 6) {
                String str1 = (String) array[4],
                    str2 = (String) array[5];
                byte[] bytes1 = str1.getBytes(StandardCharsets.UTF_8),
                    bytes2 = str2.getBytes(StandardCharsets.UTF_8);
                buf.writeInt(bytes1.length);
                buf.writeBytes(bytes1);
                buf.writeInt(bytes2.length);
                buf.writeBytes(bytes2);
            }
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
				EMCMapper.emc = new HashMap<>();
			}

			for (Object obj : pkt.data)
			{
                Object[] array = (Object[]) obj;

                SimpleStack stack;
				
				if (array.length == 3) {
					stack = new FluidSimpleStack((int) array[0], (int) array[1]);
					if (stack.isValid())
						EMCMapper.emc.put(stack, (double) array[2]);
					continue;
				}

                if (array.length == 6)
					stack = new GTSimpleStack((int) array[0], (int) array[1], (int) array[2], (String) array[4], (String) array[5]);
                else stack = new SimpleStack((int) array[0], (int) array[1], (int) array[2]);

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