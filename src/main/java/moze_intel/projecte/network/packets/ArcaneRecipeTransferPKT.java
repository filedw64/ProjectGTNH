package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import moze_intel.projecte.network.PacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 -> 服务端：NEI 一键填装奥数转化平板的合成网格。
 * 9 个格子，每格是候选 ItemStack 列表（可为空），候选的 stackSize 就是该格需要的数量。
 * 服务端按候选顺序结算，因此服务端从不信任客户端给出的具体结果。
 */
public class ArcaneRecipeTransferPKT implements IMessage, IMessageHandler<ArcaneRecipeTransferPKT, IMessage> {
	private static final int SLOTS = 9;
	private static final int MAX_CANDIDATES = 16;

	public List<List<ItemStack>> recipe = emptyRecipe();

	public ArcaneRecipeTransferPKT() {}

	public ArcaneRecipeTransferPKT(List<List<ItemStack>> recipe) {
		this.recipe = recipe;
	}

	private static List<List<ItemStack>> emptyRecipe() {
		List<List<ItemStack>> empty = new ArrayList<>();
		for (int i = 0; i < SLOTS; i++)
			empty.add(new ArrayList<ItemStack>());
		return empty;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		try {
			readPayload(buf);
		} catch (Exception e) {
			// 载荷格式在版本间变过：读坏就当这次摆料无效，避免异常冒到网络线程
			recipe = emptyRecipe();
		}
	}

	private void readPayload(ByteBuf buf) {
		List<List<ItemStack>> read = new ArrayList<>();
		int slots = buf.readInt();
		if (slots < 0) slots = 0;
		if (slots > SLOTS) slots = SLOTS;

		for (int i = 0; i < slots; i++) {
			int count = buf.readShort();
			if (count < 0) count = 0;
			int keep = Math.min(count, MAX_CANDIDATES);
			List<ItemStack> candidates = new ArrayList<>();
			for (int j = 0; j < count; j++) {
				// 超限的多余候选也必须读完，否则字节流会错位
				ItemStack stack = ByteBufUtils.readItemStack(buf);
				if (j < keep && stack != null)
					candidates.add(stack);
			}
			read.add(candidates);
		}

		while (read.size() < SLOTS)
			read.add(new ArrayList<ItemStack>());

		recipe = read;
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(SLOTS);
		for (int i = 0; i < SLOTS; i++) {
			List<ItemStack> candidates = i < recipe.size() ? recipe.get(i) : null;
			int count = candidates == null ? 0 : Math.min(candidates.size(), MAX_CANDIDATES);
			buf.writeShort(count);
			for (int j = 0; j < count; j++)
				ByteBufUtils.writeItemStack(buf, candidates.get(j));
		}
	}

	@Override
	public IMessage onMessage(ArcaneRecipeTransferPKT message, MessageContext ctx) {
		EntityPlayerMP player = ctx.getServerHandler().playerEntity;
		if (player.openContainer instanceof ArcaneTransmutationContainer) {
			ArcaneTransmutationContainer container = (ArcaneTransmutationContainer) player.openContainer;
			container.fillRecipe(player, message.recipe);
			PacketHandler.sendTo(new ArcaneTransmutationEmcPKT(container.transmutationInventory.emc), player);
		}
		return null;
	}
}
