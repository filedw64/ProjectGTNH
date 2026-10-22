package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import moze_intel.projecte.utils.BuildShape;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MercurialShapePKT implements IMessage {

	private static final int SHAPE_COUNT = BuildShape.values().length;
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
			// 服务端校验：形状序号越界直接丢弃，否则后续 BuildShape.values()[i] 会越界
			if (message.shapeIndex < 0 || message.shapeIndex >= SHAPE_COUNT) {
				PELogger.logDebug("MercurialShapePKT: rejected shapeIndex " + message.shapeIndex);
				return null;
			}

			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			if (player == null)
				return null;

			ItemStack held = player.getHeldItem();

			if (held != null && held.getItem() instanceof MercurialEye) {
				if (!held.hasTagCompound()) held.setTagCompound(new NBTTagCompound());

				ItemStack target = message.targetStack;
				if (target != null && (!(target.getItem() instanceof ItemBlock)
					|| !moze_intel.projecte.utils.EMCHelper.doesItemHaveEmc(target))) {
					PELogger.logDebug("MercurialShapePKT: rejected invalid target block");
					return null;
				}
				if (held.getTagCompound().getByte("BuildShape") != message.shapeIndex) {
					held.getTagCompound().removeTag("MidX");
					held.getTagCompound().removeTag("MidY");
					held.getTagCompound().removeTag("MidZ");
				}
				held.getTagCompound().setByte("BuildShape", message.shapeIndex);
				if (target != null && target.getItem() instanceof ItemBlock) {
					target = target.copy();
					target.stackSize = 1;
					NBTTagCompound itemNBT = new NBTTagCompound();
					target.writeToNBT(itemNBT);
					held.getTagCompound().setTag("TargetItem", itemNBT);
				} else {
					PELogger.logDebug("MercurialShapePKT: ignored non-block target item");
				}
			}
			return null;
		}
	}
}
