package moze_intel.projecte.network.packets.transport;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * 客户端 → 服务端：物质接口 GUI 动作。
 * action 1 = 优先级 -1，2 = 优先级 +1，
 * action 3 = 写某一面的「抽出方向」：side 为面索引 0..5（= ForgeDirection ordinal），
 *            accessSide 为目标方向 0..5，或 -1 = 清除该面抽出方向，
 * action 4 = 应用速率上限与输出频率：rateLimit / intervalValue 为 long（不截断高位），单位见 rateUnit / intervalUnit（0 = /s，1 = /tick），
 * action 5 = 写某一面的「推入方向」：字段含义同 action 3，
 * action 6 = 设置 / 清除过滤槽标记：side 为槽位索引 0..17（0..8 抽入表 / 9..17 推出表），stack 为模板（null = 清除）。
 * 两个方向 action 的载荷完全一致，由客户端直接给出目标值（含「再点一次 = 清除」算出的 -1），服务端只做范围校验。
 * 所有 action 在线上都固定带这整串字段（外加一个 ItemStack），省掉按 action 分支解析。
 */
public class MatterInterfaceButtonPKT implements IMessage
{
	/** action 3：写某一面的抽出方向，side 为面索引 0..5，accessSide 为 0..5 或 -1（清除） */
	public static final int EXTRACT_ACTION = 3;
	/** action 4：写入速率上限与输出频率间隔 */
	public static final int LIMITS_ACTION = 4;
	/** action 5：写某一面的推入方向，字段含义与 action 3 完全一致 */
	public static final int INSERT_ACTION = 5;
	/** action 6：设置 / 清除过滤槽标记，side 为槽位索引 0..17（0..8 抽入表 / 9..17 推出表） */
	public static final int FILTER_ACTION = 6;

	private int x;
	private int y;
	private int z;
	private int action;
	private int side;
	private long rateLimit;
	private int rateUnit;
	private long intervalValue;
	private int intervalUnit;
	/** action 3 / 5 的目标方向：0..5 = ForgeDirection ordinal，-1 = 清除该面该角色 */
	private int accessSide = -1;
	/** action 6 的模板：null = 清除该槽标记 */
	private ItemStack stack;

	public MatterInterfaceButtonPKT() {}

	public MatterInterfaceButtonPKT(int x, int y, int z, int action)
	{
		this(x, y, z, action, -1);
	}

	public MatterInterfaceButtonPKT(int x, int y, int z, int action, int side)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.action = action;
		this.side = side;
	}

	/** action 4 的载荷：速率上限 + 单位 + 频率间隔 + 单位 */
	public static MatterInterfaceButtonPKT limits(int x, int y, int z, long rateLimit, int rateUnit, long intervalValue, int intervalUnit)
	{
		MatterInterfaceButtonPKT pkt = new MatterInterfaceButtonPKT(x, y, z, LIMITS_ACTION);
		pkt.rateLimit = rateLimit;
		pkt.rateUnit = rateUnit;
		pkt.intervalValue = intervalValue;
		pkt.intervalUnit = intervalUnit;
		return pkt;
	}

	/**
	 * action 3 / 5 的载荷：面索引 + 目标方向。
	 * extract = true 写抽出方向，false 写推入方向；direction = 0..5，或 -1 = 清除。
	 * 客户端已经算好「再点一次同一方向 = 清除」，服务端只做范围校验。
	 */
	public static MatterInterfaceButtonPKT direction(int x, int y, int z, boolean extract, int side, int direction)
	{
		MatterInterfaceButtonPKT pkt = new MatterInterfaceButtonPKT(x, y, z, extract ? EXTRACT_ACTION : INSERT_ACTION, side);
		pkt.accessSide = direction;
		return pkt;
	}

	/**
	 * action 6 的载荷：槽位索引 + 模板（size=1 的副本由 tile 侧再规范一次，null = 清除）。
	 * 槽位索引自己带表，所以「客户端在看哪一套」根本不需要上线：0..8 落抽入表，9..17 落推出表。
	 */
	public static MatterInterfaceButtonPKT filter(int x, int y, int z, int slot, ItemStack stack)
	{
		MatterInterfaceButtonPKT pkt = new MatterInterfaceButtonPKT(x, y, z, FILTER_ACTION, slot);
		pkt.stack = stack;
		return pkt;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		action = buf.readByte();
		side = buf.readByte();
		accessSide = buf.readByte();
		rateLimit = buf.readLong();
		intervalValue = buf.readLong();
		rateUnit = buf.readByte();
		intervalUnit = buf.readByte();
		stack = ByteBufUtils.readItemStack(buf);
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		buf.writeByte(action);
		buf.writeByte(side);
		buf.writeByte(accessSide);
		buf.writeLong(rateLimit);
		buf.writeLong(intervalValue);
		buf.writeByte(rateUnit);
		buf.writeByte(intervalUnit);
		ByteBufUtils.writeItemStack(buf, stack);
	}

	public static class Handler implements IMessageHandler<MatterInterfaceButtonPKT, IMessage>
	{
		@Override
		public IMessage onMessage(MatterInterfaceButtonPKT pkt, MessageContext ctx)
		{
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;

			if (player == null || player.worldObj == null)
				return null;

			TileEntity tile = player.worldObj.getTileEntity(pkt.x, pkt.y, pkt.z);

			if (!(tile instanceof TileMatterInterface))
				return null;

			TileMatterInterface matterInterface = (TileMatterInterface) tile;

			// 距离校验与回包都在 tile 里做：改方向 / 应用设置之后立刻回一个 MatterInterfaceSyncPKT
			if (pkt.action == EXTRACT_ACTION)
				matterInterface.handleSideDirection(player, true, pkt.side, pkt.accessSide);
			else if (pkt.action == INSERT_ACTION)
				matterInterface.handleSideDirection(player, false, pkt.side, pkt.accessSide);
			else if (pkt.action == LIMITS_ACTION)
				matterInterface.handleLimits(player, pkt.rateLimit, pkt.rateUnit, pkt.intervalValue, pkt.intervalUnit);
			else if (pkt.action == FILTER_ACTION)
				matterInterface.handleFilter(player, pkt.side, pkt.stack);
			else
				matterInterface.handleButton(player, pkt.action);

			return null;
		}
	}
}
