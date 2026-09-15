package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FluidSimpleStack;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.NBTSimpleStack;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashMap;
import java.util.List;

public class SyncEmcPKT implements IMessage {
	private int packetNum;
	private Object[] data;

    public SyncEmcPKT() {}

	public SyncEmcPKT(int packetNum, List<Object[]> arrayList) {
		this.packetNum = packetNum;
		data = arrayList.toArray();
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		packetNum = buf.readInt();
		int size = buf.readInt();
		data = new Object[size];

		for (int i = 0; i < size; i++) {
            int arraylen = buf.readInt();
			Object[] array = new Object[arraylen];
			if (arraylen == 2) {
				array[0] = buf.readInt();
				array[1] = buf.readDouble();
				data[i] = array;
				continue;
			}
			array[0] = buf.readInt();
			array[1] = buf.readInt();
            array[2] = buf.readDouble();
			if (array.length == 4)
				array[3] = ByteBufUtils.readTag(buf);
			data[i] = array;
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(packetNum);
		buf.writeInt(data.length);

		for (Object obj : data) {
			Object[] array = (Object[]) obj;
            buf.writeInt(array.length);
			if (array.length == 2) {
				buf.writeInt((int) array[0]);
				buf.writeDouble((double) array[1]);
				continue;
			}
			buf.writeInt((int) array[0]);
			buf.writeInt((int) array[1]);
            buf.writeDouble((double) array[2]);
			if (array.length == 4)
				ByteBufUtils.writeTag(buf, (NBTTagCompound) array[3]);
		}
	}

	public static class Handler implements IMessageHandler<SyncEmcPKT, IMessage> {
		@Override
		public IMessage onMessage(final SyncEmcPKT pkt, MessageContext ctx) {
			if (pkt.packetNum == 0) {
				PELogger.logInfo("Receiving EMC data from server.");
				EMCMapper.emc.clear();
				EMCMapper.emc = new HashMap<>();
			}

			for (Object obj : pkt.data) {
                Object[] array = (Object[]) obj;

                SimpleStack stack;

				if (array.length == 2) {
					stack = new FluidSimpleStack((int) array[0]);
					if (stack.isValid())
						EMCMapper.emc.put(stack, (double) array[1]);
					continue;
				}

				if (array.length == 4)
					stack = new NBTSimpleStack((int) array[0], (int) array[1], (NBTTagCompound) array[3]);
                else stack = new SimpleStack((int) array[0], (int) array[1]);

				if (stack.isValid())
					EMCMapper.emc.put(stack, (double) array[2]);
			}

			if (pkt.packetNum == -1) {
				PELogger.logInfo("Received all packets!");
				Transmutation.cacheFullKnowledge();
				FuelMapper.loadMap();
			}
			return null;
		}
	}
}
