package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

public class ArcaneRecipeTransferPKT implements IMessage, IMessageHandler<ArcaneRecipeTransferPKT, IMessage> {
	public ItemStack[] recipe = new ItemStack[9];

	public ArcaneRecipeTransferPKT() {}

	public ArcaneRecipeTransferPKT(ItemStack[] recipe) {
		this.recipe = recipe;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		for (int i = 0; i < 9; i++) {
			recipe[i] = ByteBufUtils.readItemStack(buf);
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		for (int i = 0; i < 9; i++) {
			ByteBufUtils.writeItemStack(buf, recipe[i]);
		}
	}

	@Override
	public IMessage onMessage(ArcaneRecipeTransferPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player.openContainer instanceof ArcaneTransmutationContainer) {
			((ArcaneTransmutationContainer) player.openContainer).fillRecipe(player, message.recipe);
		}
		return null;
	}
}
