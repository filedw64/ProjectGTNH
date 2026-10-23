package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.utils.PickTransmuteHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 客户端 -> 服务端：在容器/背包 GUI 中对某个槽位中键时，消耗 EMC 复制一组该物品。
 * 只传 windowId 与槽位序号，模板一律取服务端自己 openContainer 的槽位栈，不信任客户端物品。
 */
public class GuiSlotPickPKT implements IMessage
{
	private int windowId;
	private int slot;

	public GuiSlotPickPKT() {}

	public GuiSlotPickPKT(int windowId, int slot)
	{
		this.windowId = windowId;
		this.slot = slot;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		windowId = buf.readInt();
		slot = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(windowId);
		buf.writeInt(slot);
	}

	public static class Handler implements IMessageHandler<GuiSlotPickPKT, IMessage>
	{
		@Override
		public IMessage onMessage(GuiSlotPickPKT message, MessageContext ctx)
		{
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			if (player == null) return null;

			if (player.worldObj == null) return null;

			Container container = player.openContainer;
			if (container == null || container.windowId != message.windowId) return null;
			if (message.slot < 0 || message.slot >= container.inventorySlots.size()) return null;

			Slot slot = (Slot) container.inventorySlots.get(message.slot);
			ItemStack template = slot == null ? null : slot.getStack();
			if (template == null || template.getItem() == null) return null;

			// 服务端自己拷一份，原格子物品只当模板
			ItemStack copy = template.copy();
			copy.stackSize = 1;
			PickTransmuteHelper.tryTransmute(player, copy);

			return null;
		}
	}
}
