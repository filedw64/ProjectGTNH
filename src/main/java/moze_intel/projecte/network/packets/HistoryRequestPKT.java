package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;

public class HistoryRequestPKT implements IMessage, IMessageHandler<HistoryRequestPKT, IMessage> {
	public HistoryRequestPKT() {}
	@Override public void fromBytes(ByteBuf buf) {}
	@Override public void toBytes(ByteBuf buf) {}

	@Override
	public IMessage onMessage(HistoryRequestPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player != null) {
			// 收到请求后，将数据发回给该客户端
			PacketHandler.sendTo(new HistorySyncPKT(player), player);
		}
		return null;
	}
}
