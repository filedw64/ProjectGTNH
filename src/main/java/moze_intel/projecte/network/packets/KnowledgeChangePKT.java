package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.PECore;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;

public class KnowledgeChangePKT implements IMessage {
	private boolean isRemove;
	private ItemStack stack;

	public KnowledgeChangePKT() {}

	// 增量同步
	public KnowledgeChangePKT(ItemStack stack, boolean isRemove) {
		this.stack = stack;
		this.isRemove = isRemove;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		isRemove = buf.readBoolean();
		stack = ByteBufUtils.readItemStack(buf);
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeBoolean(isRemove);
		ByteBufUtils.writeItemStack(buf, stack);
	}

	public static class Handler implements IMessageHandler<KnowledgeChangePKT, IMessage> {
		@Override
		public IMessage onMessage(final KnowledgeChangePKT message, MessageContext ctx) {
			if (message.isRemove)
				Transmutation.removeKnowledge(message.stack, PECore.proxy.getClientPlayer());
			else
				Transmutation.addKnowledge(message.stack, PECore.proxy.getClientPlayer());
			PELogger.logDebug("** RECEIVED KNOWLEDGE CHANGE DATA CLIENTSIDE **");
			return null;
		}
	}
}
