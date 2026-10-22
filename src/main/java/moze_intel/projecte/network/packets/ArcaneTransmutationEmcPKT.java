package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import net.minecraft.client.Minecraft;

/**
 * 服务端 -> 客户端：把奥数转化平板容器里的 EMC 同步给客户端。
 * 打开界面期间 EMC 的权威值在 ArcaneTransmutationContainer.transmutationInventory 上，
 * 服务端扣/加完必须把它推回客户端，否则界面数字不变。
 */
public class ArcaneTransmutationEmcPKT implements IMessage, IMessageHandler<ArcaneTransmutationEmcPKT, IMessage> {
	public double emc;

	public ArcaneTransmutationEmcPKT() {}

	public ArcaneTransmutationEmcPKT(double emc) {
		this.emc = emc;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		emc = buf.readDouble();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeDouble(emc);
	}

	@Override
	public IMessage onMessage(ArcaneTransmutationEmcPKT message, MessageContext ctx) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null && mc.thePlayer.openContainer instanceof ArcaneTransmutationContainer) {
			ArcaneTransmutationContainer container = (ArcaneTransmutationContainer) mc.thePlayer.openContainer;
			container.transmutationInventory.emc = message.emc;
			container.transmutationInventory.updateOutputs();
		}
		return null;
	}
}
