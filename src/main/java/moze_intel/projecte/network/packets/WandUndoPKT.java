package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.events.BuildersWandEventHandler;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * 客户端面板的撤销按钮。
 */
public class WandUndoPKT implements IMessage, IMessageHandler<WandUndoPKT, IMessage> {

	public WandUndoPKT() {}

	@Override
	public void fromBytes(ByteBuf buf) {}

	@Override
	public void toBytes(ByteBuf buf) {}

	@Override
	public IMessage onMessage(WandUndoPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;

		if (player == null || player.worldObj == null) return null;

		ItemStack held = player.getHeldItem();

		if (held != null && held.getItem() instanceof BuildersWand) {
			BuildersWandEventHandler.undo(player, player.worldObj);
		}

		return null;
	}
}
