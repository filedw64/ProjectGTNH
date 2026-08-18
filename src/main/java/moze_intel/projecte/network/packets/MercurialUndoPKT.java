package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.utils.MercurialHistory;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.UUID;

public class MercurialUndoPKT implements IMessage, IMessageHandler<MercurialUndoPKT, IMessage> {
	private String targetId = "";

	public MercurialUndoPKT() {}
	public MercurialUndoPKT(String id) { this.targetId = id; }

	@Override
	public void fromBytes(ByteBuf buf) { targetId = ByteBufUtils.readUTF8String(buf); }

	@Override
	public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, targetId); }

	@Override
	public IMessage onMessage(MercurialUndoPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player != null && player.worldObj != null) {
			if (message.targetId.isEmpty()) {
				MercurialHistory.tryUndo(player, player.worldObj); // 快捷撤销最新一条
			} else {
				MercurialHistory.doUndo(player, player.worldObj, UUID.fromString(message.targetId), false); // 精准撤销某一条
			}
		}
		return null;
	}
}
