package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * 客户端面板选中的建筑之杖模式。
 */
public class WandModePKT implements IMessage, IMessageHandler<WandModePKT, IMessage> {

	private byte mode;

	public WandModePKT() {}

	public WandModePKT(byte mode) {
		this.mode = mode;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		mode = buf.readByte();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(mode);
	}

	@Override
	public IMessage onMessage(WandModePKT message, MessageContext ctx) {
		// 越界模式直接丢弃，绝不写进 NBT
		if (message.mode < 0 || message.mode >= WandHelper.MODE_COUNT) {
			PELogger.logDebug("WandModePKT: rejected mode " + message.mode);
			return null;
		}

		EntityPlayerMP player = ctx.getServerHandler().playerEntity;

		if (player == null) return null;

		ItemStack held = player.getHeldItem();

		if (held != null && held.getItem() instanceof BuildersWand wand) {
			wand.setMode(held, message.mode);
			player.inventoryContainer.detectAndSendChanges();
		}

		return null;
	}
}
