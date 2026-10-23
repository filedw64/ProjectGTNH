package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * 客户端面板选中的建筑之杖聚焦方块；focus 为 null 表示清除。
 */
public class WandFocusPKT implements IMessage, IMessageHandler<WandFocusPKT, IMessage> {

	private ItemStack focus;

	public WandFocusPKT() {}

	public WandFocusPKT(ItemStack focus) {
		this.focus = focus;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		focus = ByteBufUtils.readItemStack(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBufUtils.writeItemStack(buf, focus);
	}

	@Override
	public IMessage onMessage(WandFocusPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;

		if (player == null) return null;

		ItemStack held = player.getHeldItem();

		if (held == null || !(held.getItem() instanceof BuildersWand wand)) return null;

		ItemStack focus = message.focus;

		// 只接受方块物品，其余一律丢弃包、不动 NBT
		if (focus != null && !(focus.getItem() instanceof ItemBlock)) {
			PELogger.logDebug("WandFocusPKT: rejected non-block focus");
			return null;
		}

		wand.setFocusStack(held, focus);
		player.inventoryContainer.detectAndSendChanges();

		return null;
	}
}
