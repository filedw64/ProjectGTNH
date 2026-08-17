package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import net.minecraft.entity.player.EntityPlayerMP;

public class ArcaneTabletButtonPKT implements IMessage, IMessageHandler<ArcaneTabletButtonPKT, IMessage> {
	public int actionId;

	public ArcaneTabletButtonPKT() {}

	public ArcaneTabletButtonPKT(int actionId) {
		this.actionId = actionId;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		actionId = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(actionId);
	}

	@Override
	public IMessage onMessage(ArcaneTabletButtonPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player.openContainer instanceof ArcaneTransmutationContainer) {
			ArcaneTransmutationContainer container = (ArcaneTransmutationContainer) player.openContainer;
			switch (message.actionId) {
				case 0: container.rotateCrafting(true); break;
				case 1: container.rotateCrafting(false); break;
				case 2: container.balanceCrafting(); break;
				case 3: container.spreadCrafting(); break;
				case 4: container.clearCrafting(player); break;
			}
		}
		return null;
	}
}
