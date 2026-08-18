package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.rendering.MercurialHUD;

public class MercurialNotifyPKT implements IMessage, IMessageHandler<MercurialNotifyPKT, IMessage> {
	public String text = "";

	public MercurialNotifyPKT() {}
	public MercurialNotifyPKT(String text) { this.text = text; }

	@Override
	public void fromBytes(ByteBuf buf) { text = ByteBufUtils.readUTF8String(buf); }

	@Override
	public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, text); }

	@Override
	public IMessage onMessage(MercurialNotifyPKT message, MessageContext ctx) {
		// 客户端接收后，交给 HUD 渲染器处理
		MercurialHUD.showMessage(message.text, 60); // 停留 60 tick (3秒)
		return null;
	}
}
