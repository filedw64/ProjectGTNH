package moze_intel.projecte.primordial;

import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

public final class PrimordialActionPacket implements IMessage, IMessageHandler<PrimordialActionPacket, IMessage> {
    private int window, action, value;
    private String token = "";
    public PrimordialActionPacket() {}
    public PrimordialActionPacket(int window, int action, int value) { this(window, action, value, ""); }
    public PrimordialActionPacket(int window, int action, int value, String token) { this.window = window; this.action = action; this.value = value; this.token = token; }
    @Override public void toBytes(ByteBuf b) { b.writeInt(window); b.writeInt(action); b.writeInt(value); ByteBufUtils.writeUTF8String(b, token); }
    @Override public void fromBytes(ByteBuf b) { window = b.readInt(); action = b.readInt(); value = b.readInt(); token = ByteBufUtils.readUTF8String(b); if (token.length() > 64) token = ""; }
    @Override public IMessage onMessage(final PrimordialActionPacket m, MessageContext ctx) {
        final EntityPlayerMP p = ctx.getServerHandler().playerEntity;
        PrimordialEvents.enqueue(new Runnable() { @Override public void run() {
            if (p.isDead || p.playerNetServerHandler == null || PrimordialEvents.player(p.getUniqueID()) != p) return;
            ItemStack held = p.getHeldItem();
            if (m.window == -1 && held != null && held.getItem() instanceof EmcAcceleratorItem) {
                if (m.action == 90) p.openGui(moze_intel.projecte.PECore.instance, EmcAcceleratorItem.GUI, p.worldObj, 0, 0, 0);
                if (m.action == 91) AccelerationService.cycle(held);
                return;
            }
            if (p.openContainer == null || p.openContainer.windowId != m.window) return;
            if (p.openContainer instanceof ConfiguratorContainer) ((ConfiguratorContainer) p.openContainer).action(m.action, m.value);
            else if (p.openContainer instanceof AccelerationContainer) ((AccelerationContainer) p.openContainer).action(m.action, m.value, m.token);
        }}); return null;
    }
}
