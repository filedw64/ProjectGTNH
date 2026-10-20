package moze_intel.projecte.primordial;

import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

public final class PrimordialSyncPacket implements IMessage, IMessageHandler<PrimordialSyncPacket, IMessage> {
    private int window;
    private NBTTagCompound state;
    public PrimordialSyncPacket() {}
    public PrimordialSyncPacket(int window, NBTTagCompound state) { this.window = window; this.state = state; }
    @Override public void toBytes(ByteBuf b) { b.writeInt(window); ByteBufUtils.writeTag(b, state); }
    @Override public void fromBytes(ByteBuf b) { window = b.readInt(); state = ByteBufUtils.readTag(b); }
    @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final PrimordialSyncPacket m, MessageContext ctx) {
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        mc.func_152344_a(new Runnable() { @Override public void run() {
            if (mc.thePlayer == null || mc.thePlayer.openContainer == null || mc.thePlayer.openContainer.windowId != m.window || m.state == null) return;
            if (mc.thePlayer.openContainer instanceof ConfiguratorContainer) ((ConfiguratorContainer) mc.thePlayer.openContainer).receive(m.state);
            if (mc.thePlayer.openContainer instanceof AccelerationContainer) ((AccelerationContainer) mc.thePlayer.openContainer).state = m.state;
        }}); return null;
    }
}
