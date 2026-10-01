package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.integration.ae2.TileMEEMCLink;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

public class MEEMCLinkPKT implements IMessage, IMessageHandler<MEEMCLinkPKT, IMessage> {
	private int x, y, z, action, value;

	public MEEMCLinkPKT() {}
	public MEEMCLinkPKT(int x, int y, int z, int action, int value) {
		this.x = x; this.y = y; this.z = z; this.action = action; this.value = value;
	}

	@Override public void fromBytes(ByteBuf buf) {
		x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
		action = buf.readByte(); value = buf.readInt();
	}
	@Override public void toBytes(ByteBuf buf) {
		buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
		buf.writeByte(action); buf.writeInt(value);
	}

	@Override public IMessage onMessage(MEEMCLinkPKT pkt, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player == null || player.worldObj == null) return null;

		TileEntity te = player.worldObj.getTileEntity(pkt.x, pkt.y, pkt.z);
		if (te instanceof TileMEEMCLink) {
			TileMEEMCLink link = (TileMEEMCLink) te;
			if (pkt.action == 0) link.setPriority(pkt.value);
			else if (pkt.action == 1) link.setAccessMode(pkt.value);
			else if (pkt.action == 2) link.setFilterMode(pkt.value);
			else if (pkt.action == 3) link.setOwner(player);
			else if (pkt.action == 4) link.setFilterPrecision(pkt.value);
		}
		return null;
	}
}
