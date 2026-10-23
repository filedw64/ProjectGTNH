package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.utils.PickTransmuteHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * 客户端 -> 服务端：中键选取方块时消耗 EMC 直接获得该物品。
 * 只传坐标与命中面，物品由服务端按世界数据重新推导，不信任客户端 ItemStack。
 * 扣费/容量/转化桌门槛等共用逻辑在 PickTransmuteHelper。
 */
public class PickBlockPKT implements IMessage
{
	private int x;
	private int y;
	private int z;
	private byte side;

	public PickBlockPKT() {}

	public PickBlockPKT(int x, int y, int z, int side)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.side = (byte) side;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		side = buf.readByte();
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		buf.writeByte(side);
	}

	public static class Handler implements IMessageHandler<PickBlockPKT, IMessage>
	{
		@Override
		public IMessage onMessage(PickBlockPKT message, MessageContext ctx)
		{
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			if (player == null) return null;

			if (!ProjectEConfig.enablePickBlockTransmute) return null;

			int side = message.side;
			if (side < 0 || side > 6) return null;

			if (player.worldObj == null) return null;

			World world = player.worldObj;
			int x = message.x;
			int y = message.y;
			int z = message.z;

			if (!world.blockExists(x, y, z) || y < 0 || y > 255) return null;
			if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D) return null;

			Block block = world.getBlock(x, y, z);
			if (block == null || block.isAir(world, x, y, z)) return null;

			ItemStack stack = getPickedStack(block, world, player, x, y, z, side);
			if (stack == null) return null;
			stack.stackSize = 1;

			PickTransmuteHelper.tryTransmute(player, stack);

			return null;
		}

		private static ItemStack getPickedStack(Block block, World world, EntityPlayerMP player, int x, int y, int z, int side)
		{
			MovingObjectPosition mop = new MovingObjectPosition(x, y, z, side, Vec3.createVectorHelper(x + 0.5D, y + 0.5D, z + 0.5D));
			ItemStack picked = block.getPickBlock(mop, world, x, y, z, player);
			if (picked != null) return picked;

			Item item = Item.getItemFromBlock(block);
			if (item == null) return null;
			return new ItemStack(item, 1, world.getBlockMetadata(x, y, z));
		}
	}
}
