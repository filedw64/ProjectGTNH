package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import moze_intel.projecte.utils.mercurial.TransformMatrix;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MercurialTransformPKT implements IMessage, IMessageHandler<MercurialTransformPKT, IMessage> {
	private int rotations;
	private int stackAxisOrd;
	private int stackCount;

	public MercurialTransformPKT() {}

	public MercurialTransformPKT(int rotations, int stackAxisOrd, int stackCount) {
		this.rotations = rotations;
		this.stackAxisOrd = stackAxisOrd;
		this.stackCount = stackCount;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.rotations = buf.readByte();
		this.stackAxisOrd = buf.readByte();
		this.stackCount = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeByte(rotations);
		buf.writeByte(stackAxisOrd);
		buf.writeInt(stackCount);
	}

	@Override
	public IMessage onMessage(MercurialTransformPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player != null) {
			ItemStack held = player.getHeldItem();
			if (held != null && held.getItem() instanceof MercurialEye) {
				NBTTagCompound nbt = held.getTagCompound();
				if (nbt != null && nbt.getBoolean("HasClipboard")) {
					nbt.setByte("Rotations", (byte) message.rotations);
					nbt.setByte("StackAxis", (byte) message.stackAxisOrd);
					nbt.setInteger("StackCount", message.stackCount);
				}
			}
		}
		return null;
	}
}
