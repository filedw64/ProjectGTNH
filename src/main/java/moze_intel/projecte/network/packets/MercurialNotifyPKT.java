package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.rendering.MercurialHUD;
import net.minecraft.util.StatCollector;

public class MercurialNotifyPKT implements IMessage, IMessageHandler<MercurialNotifyPKT, IMessage> {
	public static final int LEVEL_INFO = 0;
	public static final int LEVEL_WARN = 1;
	public static final int LEVEL_ERROR = 2;

	public String langKey = "";
	public String[] args = new String[0];
	public int level = LEVEL_INFO;

	public MercurialNotifyPKT() {}

	public MercurialNotifyPKT(String langKey, int level, String... args) {
		this.langKey = langKey == null ? "" : langKey;
		this.level = level;
		this.args = args == null ? new String[0] : args;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		langKey = ByteBufUtils.readUTF8String(buf);
		level = buf.readByte();
		int argCount = buf.readByte();
		args = new String[argCount];
		for (int i = 0; i < argCount; i++) args[i] = ByteBufUtils.readUTF8String(buf);
	}

	@Override
	public void toBytes(ByteBuf buf) {
		ByteBufUtils.writeUTF8String(buf, langKey);
		buf.writeByte(level);
		buf.writeByte(args.length);
		for (String arg : args) ByteBufUtils.writeUTF8String(buf, arg);
	}

	@Override
	public IMessage onMessage(MercurialNotifyPKT msg, MessageContext ctx) {
		// 客户端接收后按自己的语言翻译，再交给 HUD 渲染器处理
		MercurialHUD.showMessage(StatCollector.translateToLocalFormatted(msg.langKey, (Object[]) msg.args), msg.level, 60);
		return null;
	}
}
