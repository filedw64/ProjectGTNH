package moze_intel.projecte.network.packets.transport;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterfaceFluid;
import moze_intel.projecte.transport.TransportType;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

/**
 * 服务端 → 客户端：物质接口的优先级 / 点击面 / 容器连接 / 相邻管道数 / 六面方向 / 两套过滤模板 / 速率与频率设置。
 *
 * 载荷顺序：x y z / priority / clickSide / connected / pipes / rateLimit / rateUnit / intervalValue / intervalUnit /
 * 6 字节抽出方向 / 6 字节推入方向 / 18 格过滤模板（抽入 9 + 推出 9）/ 传输类型字节 / 流体标记 NBT（只有流体接口才写）。
 * 两套模板各自 9 格：前 9 个 ItemStack 是抽入表的展示物品，后 9 个是推出表的展示物品；
 * 流体接口再多一条 18 条目的 NBTTagList（Slot 0..8 = 抽入、9..17 = 推出），条目数翻倍、Slot 索引扩到 0..17，
 * 仍是「Slot 字节 + FluidStack 自身字段」的原格式，物品接口只多一个类型字节，不写这段载荷。
 * 方向字节取值 0..5 = ForgeDirection ordinal（绝对方向），-1 = 该面该角色未设置；
 * 面索引同样是 ForgeDirection ordinal，索引顺序：下 上 北 南 西 东。
 * clickSide 是玩家点开 GUI 的那一面（-1 = 未知，GUI 回退「前 = 南」），GUI 靠它把 3x3 主网格转到玩家看到的那一面。
 * 速率上限与频率间隔是 long，用 writeLong / readLong 传输，2^31 以上的值不会被截断。
 * 注意：客户端「正在编辑哪一套过滤表」是纯 GUI 本地状态，不上线，包里也没有对应字段。
 */
public class MatterInterfaceSyncPKT implements IMessage
{
	/** 面方向字节数，与 {@link TileMatterInterface#SIDE_COUNT} 一致 */
	private static final int SIDE_BYTES = TileMatterInterface.SIDE_COUNT;
	/** 单套过滤表的格数，与 {@link TileMatterInterface#FILTER_SET_SIZE} 一致 */
	private static final int FILTER_SET = TileMatterInterface.FILTER_SET_SIZE;

	private int x;
	private int y;
	private int z;
	private int priority;
	/** 玩家点开 GUI 的那一面：0..5，-1 = 未知 */
	private byte clickSide = TileMatterInterface.SIDE_UNSET;
	private boolean connected;
	private int pipes;
	private long rateLimit;
	private int rateUnit;
	private long intervalValue;
	private int intervalUnit;
	/** 六面抽出方向 */
	private final byte[] extractSides = unsetSides();
	/** 六面推入方向 */
	private final byte[] insertSides = unsetSides();
	/** 抽入表 9 格过滤模板（展示用） */
	private final ItemStack[] filter = new ItemStack[FILTER_SET];
	/** 推出表 9 格过滤模板（展示用） */
	private final ItemStack[] outputFilter = new ItemStack[FILTER_SET];
	/** 传输类型：决定后面那段流体标记载荷写不写 */
	private TransportType transportType = TransportType.ITEM;
	/** 抽入表 9 格流体标记（判定 / 色块用）：只有流体接口才非空 */
	private final FluidStack[] fluidFilter = new FluidStack[FILTER_SET];
	/** 推出表 9 格流体标记：同上 */
	private final FluidStack[] fluidOutputFilter = new FluidStack[FILTER_SET];
	/** 流体标记在 NBT 里的列表键，与 tile 存盘用的是同一份格式 */
	private static final String FLUID_FILTER_TAG = "FluidFilter";
	/** 类型字节 → 枚举：values() 每次调用都会克隆数组，客户端每 5 tick 收一包，不值得 */
	private static final TransportType[] TRANSPORT_TYPES = TransportType.values();

	private static byte[] unsetSides()
	{
		byte[] sides = new byte[SIDE_BYTES];

		for (int i = 0; i < SIDE_BYTES; i++)
			sides[i] = TileMatterInterface.SIDE_UNSET;

		return sides;
	}

	public MatterInterfaceSyncPKT() {}

	public MatterInterfaceSyncPKT(int x, int y, int z, int priority, byte clickSide, boolean connected, int pipes,
		byte[] extractSides, byte[] insertSides, ItemStack[] filter, ItemStack[] outputFilter, long rateLimit,
		int rateUnit, long intervalValue, int intervalUnit, TransportType transportType, FluidStack[] fluidFilter,
		FluidStack[] fluidOutputFilter)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.priority = priority;
		this.clickSide = clickSide;
		this.connected = connected;
		this.pipes = pipes;
		this.rateLimit = rateLimit;
		this.rateUnit = rateUnit;
		this.intervalValue = intervalValue;
		this.intervalUnit = intervalUnit;
		this.transportType = transportType == null ? TransportType.ITEM : transportType;

		if (extractSides != null)
			System.arraycopy(extractSides, 0, this.extractSides, 0, Math.min(extractSides.length, SIDE_BYTES));

		if (insertSides != null)
			System.arraycopy(insertSides, 0, this.insertSides, 0, Math.min(insertSides.length, SIDE_BYTES));

		copyItems(this.filter, filter);
		copyItems(this.outputFilter, outputFilter);
		copyFluids(this.fluidFilter, fluidFilter);
		copyFluids(this.fluidOutputFilter, fluidOutputFilter);
	}

	private static void copyItems(ItemStack[] target, ItemStack[] source)
	{
		if (source == null)
			return;

		for (int i = 0; i < target.length && i < source.length; i++)
			target[i] = source[i];
	}

	private static void copyFluids(FluidStack[] target, FluidStack[] source)
	{
		if (source == null)
			return;

		for (int i = 0; i < target.length && i < source.length; i++)
			target[i] = source[i] == null ? null : source[i].copy();
	}

	/**
	 * 两套流体标记拼成一条 18 条目的列表（0..8 抽入 / 9..17 推出），沿用原有的「Slot 字节 + FluidStack」格式。
	 * 同步包只带这一条列表：客户端按前 9 / 后 9 拆回去，不用解析两个键。
	 */
	private FluidStack[] combinedFluidFilter()
	{
		FluidStack[] combined = new FluidStack[FILTER_SET * 2];

		System.arraycopy(fluidFilter, 0, combined, 0, FILTER_SET);
		System.arraycopy(fluidOutputFilter, 0, combined, FILTER_SET, FILTER_SET);
		return combined;
	}

	/** 18 条目列表 → 两套 9 格表：越界或解析不出的槽一律留空 */
	private void splitFluidFilter(FluidStack[] combined)
	{
		for (int i = 0; i < FILTER_SET; i++)
		{
			fluidFilter[i] = combined[i];
			fluidOutputFilter[i] = combined[FILTER_SET + i];
		}
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		priority = buf.readByte();
		clickSide = buf.readByte();
		connected = buf.readBoolean();
		pipes = buf.readInt();
		rateLimit = buf.readLong();
		rateUnit = buf.readByte();
		intervalValue = buf.readLong();
		intervalUnit = buf.readByte();

		for (int i = 0; i < SIDE_BYTES; i++)
			extractSides[i] = buf.readByte();

		for (int i = 0; i < SIDE_BYTES; i++)
			insertSides[i] = buf.readByte();

		// 抽入表 9 个 ItemStack + 推出表 9 个 ItemStack，顺序与 toBytes 严格一致
		for (int i = 0; i < FILTER_SET; i++)
			filter[i] = ByteBufUtils.readItemStack(buf);

		for (int i = 0; i < FILTER_SET; i++)
			outputFilter[i] = ByteBufUtils.readItemStack(buf);

		int typeOrdinal = buf.readByte();
		transportType = typeOrdinal >= 0 && typeOrdinal < TRANSPORT_TYPES.length ? TRANSPORT_TYPES[typeOrdinal] : TransportType.ITEM;

		// 只有流体接口在后面带这段载荷；物品接口读到的就是空
		if (transportType == TransportType.FLUID)
		{
			NBTTagCompound root = ByteBufUtils.readTag(buf);
			FluidStack[] combined = new FluidStack[FILTER_SET * 2];
			TileMatterInterfaceFluid.readFilterNBT(root == null ? null : root.getTagList(FLUID_FILTER_TAG, 10), combined);
			splitFluidFilter(combined);
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		buf.writeByte(priority);
		buf.writeByte(clickSide);
		buf.writeBoolean(connected);
		buf.writeInt(pipes);
		buf.writeLong(rateLimit);
		buf.writeByte(rateUnit);
		buf.writeLong(intervalValue);
		buf.writeByte(intervalUnit);

		for (int i = 0; i < SIDE_BYTES; i++)
			buf.writeByte(extractSides[i]);

		for (int i = 0; i < SIDE_BYTES; i++)
			buf.writeByte(insertSides[i]);

		for (int i = 0; i < FILTER_SET; i++)
			ByteBufUtils.writeItemStack(buf, filter[i]);

		for (int i = 0; i < FILTER_SET; i++)
			ByteBufUtils.writeItemStack(buf, outputFilter[i]);

		buf.writeByte(transportType.ordinal());

		// 1.7.10 的 ByteBufUtils 没有 writeFluidStack：FluidStack.writeToNBT 装进 NBTTagList，整个 tag 走 writeTag
		if (transportType == TransportType.FLUID)
		{
			NBTTagCompound root = new NBTTagCompound();
			root.setTag(FLUID_FILTER_TAG, TileMatterInterfaceFluid.writeFilterNBT(combinedFluidFilter()));
			ByteBufUtils.writeTag(buf, root);
		}
	}

	public static class Handler implements IMessageHandler<MatterInterfaceSyncPKT, IMessage>
	{
		@Override
		public IMessage onMessage(MatterInterfaceSyncPKT pkt, MessageContext ctx)
		{
			TileEntity tile = Minecraft.getMinecraft().theWorld.getTileEntity(pkt.x, pkt.y, pkt.z);

			if (tile instanceof TileMatterInterface)
				((TileMatterInterface) tile).applySync(pkt.priority, pkt.clickSide, pkt.connected, pkt.pipes,
					pkt.extractSides, pkt.insertSides, pkt.filter, pkt.outputFilter, pkt.fluidFilter,
					pkt.fluidOutputFilter, pkt.rateLimit, pkt.rateUnit, pkt.intervalValue, pkt.intervalUnit);

			return null;
		}
	}
}
