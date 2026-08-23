package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MercurialShapePKT implements IMessage {

	private byte shapeIndex;
	private ItemStack targetStack; // 玩家选中的方块

	public MercurialShapePKT() {}

	public MercurialShapePKT(byte shapeIndex, ItemStack targetStack) {
		this.shapeIndex = shapeIndex;
		this.targetStack = targetStack;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		shapeIndex = buf.readByte();
		targetStack = ByteBufUtils.readItemStack(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(shapeIndex);
		ByteBufUtils.writeItemStack(buf, targetStack);
	}

	public static class Handler implements IMessageHandler<MercurialShapePKT, IMessage> {
		@Override
		public IMessage onMessage(final MercurialShapePKT message, final MessageContext ctx) {
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			ItemStack held = player.getHeldItem();

			if (held != null && held.getItem() instanceof MercurialEye) {
				if (!held.hasTagCompound()) held.setTagCompound(new NBTTagCompound());

				// 保存形状
				held.getTagCompound().setByte("BuildShape", message.shapeIndex);

				// 保存目标方块
				if (message.targetStack != null) {
					NBTTagCompound itemNBT = new NBTTagCompound();
					message.targetStack.writeToNBT(itemNBT);
					held.getTagCompound().setTag("TargetItem", itemNBT);
				}
			}
			return null;
		}
	}
}
