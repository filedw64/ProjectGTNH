package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import moze_intel.projecte.PECore;
import moze_intel.projecte.utils.PELogger;

public class KnowledgeSyncPKT implements IMessage
{
	private NBTTagCompound nbt;
	private boolean isIncremental = false;
	private boolean isRemove = false;
	private ItemStack stack = null;

	public KnowledgeSyncPKT() {}

	// 全量同步
	public KnowledgeSyncPKT(NBTTagCompound nbt)
	{
		this.nbt = nbt;
		this.isIncremental = false;
	}

	// 增量同步
	public KnowledgeSyncPKT(ItemStack stack, boolean isRemove)
	{
		this.stack = stack;
		this.isRemove = isRemove;
		this.isIncremental = true;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		isIncremental = buf.readBoolean();
		if (isIncremental) {
			isRemove = buf.readBoolean();
			stack = ByteBufUtils.readItemStack(buf);
		} else {
			nbt = ByteBufUtils.readTag(buf);
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeBoolean(isIncremental);
		if (isIncremental) {
			buf.writeBoolean(isRemove);
			ByteBufUtils.writeItemStack(buf, stack);
		} else {
			ByteBufUtils.writeTag(buf, nbt);
		}
	}

	public static class Handler implements IMessageHandler<KnowledgeSyncPKT, IMessage>
	{
		@Override
		public IMessage onMessage(final KnowledgeSyncPKT message, MessageContext ctx)
		{
			if (message.isIncremental) {
				// 调用=客户端侧更新方法，避免直接访问受保护的方法
				moze_intel.projecte.playerData.Transmutation.updateKnowledgeClient(message.stack, message.isRemove);
				PELogger.logDebug("** RECEIVED INCREMENTAL TRANSMUTATION DATA CLIENTSIDE **");
			} else {
				PECore.proxy.getClientTransmutationProps().readFromPacket(message.nbt);
				PELogger.logDebug("** RECEIVED FULL TRANSMUTATION DATA CLIENTSIDE **");
			}

			return null;
		}
	}
}
