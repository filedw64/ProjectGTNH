package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.gui.GUIMercurialShape;
import moze_intel.projecte.utils.MercurialHistory;
import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.List;

public class HistorySyncPKT implements IMessage, IMessageHandler<HistorySyncPKT, IMessage> {

	public static class ClientNode {
		public String id;
		public String parentId;
		public int blockId, meta, count;
	}

	public List<ClientNode> nodes = new ArrayList<>();

	public HistorySyncPKT() {}

	public HistorySyncPKT(net.minecraft.entity.player.EntityPlayerMP player) {
		for (MercurialHistory.HistoryRecord r : MercurialHistory.getRecords(player)) {
			ClientNode node = new ClientNode();
			node.id = r.id.toString();
			node.parentId = r.parentId == null ? "" : r.parentId.toString();
			node.count = r.changes.size();
			if (node.count > 0) {
				node.blockId = Block.getIdFromBlock(r.changes.get(0).newBlock);
				node.meta = r.changes.get(0).newMeta;
			}
			nodes.add(node);
		}
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		int size = buf.readInt();
		for (int i = 0; i < size; i++) {
			ClientNode n = new ClientNode();
			n.id = ByteBufUtils.readUTF8String(buf);
			n.parentId = ByteBufUtils.readUTF8String(buf);
			n.blockId = buf.readInt();
			n.meta = buf.readInt();
			n.count = buf.readInt();
			nodes.add(n);
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(nodes.size());
		for (ClientNode n : nodes) {
			ByteBufUtils.writeUTF8String(buf, n.id);
			ByteBufUtils.writeUTF8String(buf, n.parentId);
			buf.writeInt(n.blockId);
			buf.writeInt(n.meta);
			buf.writeInt(n.count);
		}
	}

	@Override
	public IMessage onMessage(HistorySyncPKT message, MessageContext ctx) {
		// 接收到服务端发来的数据，交给 GUI 渲染
		GUIMercurialShape.clientHistoryCache = message.nodes;
		return null;
	}
}
