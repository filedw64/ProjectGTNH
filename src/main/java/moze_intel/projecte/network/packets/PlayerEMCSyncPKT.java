package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.PECore;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;

/** Server-to-client balance only. Never register a server-side balance setter. */
public final class PlayerEMCSyncPKT implements IMessage {
    private ExactEMC balance;
    public PlayerEMCSyncPKT() {}
    public PlayerEMCSyncPKT(ExactEMC balance) { this.balance = ExactEMCCodec.validateBalance(balance); }
    public void toBytes(ByteBuf buf) { ExactEMCCodec.write(buf, balance); }
    public void fromBytes(ByteBuf buf) { balance = ExactEMCCodec.validateBalance(ExactEMCCodec.read(buf)); }
    public static class Handler implements IMessageHandler<PlayerEMCSyncPKT, IMessage> {
        public IMessage onMessage(final PlayerEMCSyncPKT message, MessageContext ctx) {
            moze_intel.projecte.network.ClientEMCUpdates.enqueue(
                () -> PECore.proxy.getClientTransmutationProps().applyBalancePacket(message.balance));
            return null;
        }
    }
}
