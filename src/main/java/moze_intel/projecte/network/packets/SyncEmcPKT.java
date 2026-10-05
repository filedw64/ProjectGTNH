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
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.nbt.NBTTagCompound;
import java.util.ArrayList;
import java.util.List;

/** Versioned exact price fragments, independent of the player balance packet. */
public class SyncEmcPKT implements IMessage {
    private boolean first, last;
    private List<Object[]> data = new ArrayList<>();
    public SyncEmcPKT() {}
    public SyncEmcPKT(boolean first, boolean last, List<Object[]> data) {
        this.first = first; this.last = last; this.data = new ArrayList<>(data);
    }
    public void toBytes(ByteBuf buf) {
        buf.writeByte(1); buf.writeBoolean(first); buf.writeBoolean(last); buf.writeInt(data.size());
        for (Object[] entry : data) {
            buf.writeByte(entry.length);
            buf.writeInt((Integer) entry[0]);
            if (entry.length != 2) buf.writeInt((Integer) entry[1]);
            ExactEMCCodec.write(buf, (ExactEMC) entry[entry.length == 2 ? 1 : 2]);
            if (entry.length == 4) ByteBufUtils.writeTag(buf, (NBTTagCompound) entry[3]);
        }
    }
    public void fromBytes(ByteBuf buf) {
        if (buf.readableBytes() < 7 || buf.readUnsignedByte() != 1)
            throw new IllegalArgumentException("Unsupported price protocol");
        first = buf.readBoolean(); last = buf.readBoolean();
        int count = buf.readInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid price entry count");
        data = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (buf.readableBytes() < 5) throw new IllegalArgumentException("Truncated price entry");
            int type = buf.readUnsignedByte();
            if (type < 2 || type > 4) throw new IllegalArgumentException("Invalid price entry type");
            Object[] entry = new Object[type];
            entry[0] = buf.readInt();
            if (type != 2) entry[1] = buf.readInt();
            entry[type == 2 ? 1 : 2] = ExactEMCCodec.validateBalance(ExactEMCCodec.read(buf));
            if (type == 4) entry[3] = ByteBufUtils.readTag(buf);
            data.add(entry);
        }
    }
    public static class Handler implements IMessageHandler<SyncEmcPKT, IMessage> {
        public IMessage onMessage(final SyncEmcPKT packet, MessageContext ctx) {
            moze_intel.projecte.network.ClientEMCUpdates.enqueue(() -> {
                if (packet.first) EMCMapper.clearMaps();
                for (Object[] entry : packet.data) {
                    SimpleStack key = entry.length == 2 ? new FluidSimpleStack((Integer) entry[0]) :
                        entry.length == 4 ? new NBTSimpleStack((Integer) entry[0], (Integer) entry[1],
                            (NBTTagCompound) entry[3]) : new SimpleStack((Integer) entry[0], (Integer) entry[1]);
                    if (key.isValid()) EMCMapper.putExact(key, (ExactEMC) entry[entry.length == 2 ? 1 : 2]);
                }
                if (packet.last) { Transmutation.cacheFullKnowledge(); FuelMapper.loadMap(); }
            });
            return null;
        }
    }
}
