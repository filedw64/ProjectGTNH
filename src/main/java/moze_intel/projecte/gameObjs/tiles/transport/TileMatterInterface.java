package moze_intel.projecte.gameObjs.tiles.transport;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import moze_intel.projecte.gameObjs.blocks.transport.BlockMatterInterface;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.transport.MatterInterfaceSyncPKT;
import moze_intel.projecte.transport.MatterNetwork;
import moze_intel.projecte.transport.MatterNetworkManager;
import moze_intel.projecte.transport.TransportType;
import moze_intel.projecte.transport.api.INetworkEndpoint;
import moze_intel.projecte.transport.api.IPortProvider;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

/**
 * 物质接口方块实体的公共部分：六面抽出 / 推入方向、优先级、9 格过滤槽与六面目标容器缓存。
 * 具体搬运逻辑由 {@link TileMatterInterfaceItem} / {@link TileMatterInterfaceFluid} 实现。
 *
 * 数据模型（取代旧的三态 sideModes + AUTO accessSides）：
 * {@link #extractSides} 每面一个「抽出方向」，{@link #insertSides} 每面一个「推入方向」，
 * 取值 0..5 = ForgeDirection ordinal（绝对方向，不再是「目标朝向我们的那一面」），-1 = 未设置。
 * 面索引 = ForgeDirection ordinal（下 0 / 上 1 / 北 2 / 南 3 / 西 4 / 东 5）。
 * 模式由方向推导：只设抽出 = 输入面，只设推入 = 输出面，两个都设 = 同面双向，都没设 = 关闭。
 *
 * 端口：每面最多两个端口对象（抽出端口 mode=INPUT、推入端口 mode=OUTPUT），
 * 惰性创建后长期复用（一个方块实体最多 12 个），网络去重靠的就是对象身份，不能每次 new。
 * 两个端口各自持有自己的操作方向，因此同一面的抽出与推入可以用不同方向访问相邻容器。
 * 注意：客户端不持有任何网络数据，GUI 需要的状态全部由 {@link MatterInterfaceSyncPKT} 同步。
 */
public abstract class TileMatterInterface extends TileEntity implements IInventory, IPortProvider
{
	/** 每套过滤表的格数（3x3）：抽入表与推出表各一套 */
	public static final int FILTER_SET_SIZE = 9;
	/** 过滤槽总数 = 抽入 9 + 推出 9，也就是本 IInventory 的槽数（0..8 = 抽入表，9..17 = 推出表） */
	public static final int FILTER_SIZE = 18;
	/** 面数量（六面端口） */
	public static final int SIDE_COUNT = 6;
	/** 该面该角色没设方向：extractSides / insertSides 里的空值，NBT 与同步包里同样是 -1 */
	public static final byte SIDE_UNSET = -1;

	/** 未连到容器时，每隔多少 tick 重新查一次目标容器 */
	private static final int TARGET_RETRY_INTERVAL = 20;
	/** GUI 状态同步间隔（tick） */
	private static final int SYNC_INTERVAL = 5;
	/** 目标 TPS：/tick 单位换算成每秒量时乘 20，与任何动态调度档位无关 */
	private static final long TICKS_PER_SECOND = 20L;
	/** 没有频率限制时令牌桶的容量窗口（秒）：否则 cap 恒为 1，单轮最多只搬 1 个 */
	private static final double DEFAULT_BUCKET_SECONDS = 1.0D;
	/** 令牌桶容量上限，量级即 Long.MAX_VALUE（double 表示足够，不会溢出成 Infinity） */
	private static final double MAX_BUCKET_TOKENS = 9.223372036854776E18D;

	/** 抽入表：抽取时「该不该抽」的 9 格模板，INPUT 端口与网络抽取路径只看这一张 */
	protected final ItemStack[] filter = new ItemStack[FILTER_SET_SIZE];
	/** 推出表：投递时「该不该收」的 9 格模板，OUTPUT 端口与 matchesFilter 只看这一张；空表 = 全通 */
	protected final ItemStack[] outputFilter = new ItemStack[FILTER_SET_SIZE];

	/**
	 * 六面「抽出方向」：从该面相邻容器把东西抽进网络时，用容器的哪一面访问。
	 * 值 0..5 = ForgeDirection ordinal，{@link #SIDE_UNSET} = 未设置。索引本身就是面（ordinal）。
	 */
	protected final byte[] extractSides = unsetSides();
	/** 六面「推入方向」：把网内货物推到该面相邻容器时，用容器的哪一面访问 */
	protected final byte[] insertSides = unsetSides();

	protected int priority;

	/** 输出速率上限：0 = 不限速。tile 级，六面共享同一个令牌桶 */
	private long rateLimit;
	/** 速率单位：0 = /s，1 = /tick */
	private int rateUnit;
	/** 输出频率间隔：0 = 不限制。同样是 tile 级 */
	private long intervalValue;
	/** 频率单位：0 = /s，1 = /tick */
	private int intervalUnit;

	/** 令牌桶余额：物品按件、流体按 mB，同一个接口不会混用 */
	private double tokens;
	/** 上次补令牌的 nanoTime；0 = 尚未初始化，第一次补满 */
	private long lastRefillNanos;
	/** 上次真正投递成功的 nanoTime；0 = 尚未投递。只认真实时间，不读世界时间 / tick */
	private long lastPushNanos;

	/** 仅供客户端 GUI 显示的同步字段 */
	public boolean displayConnected;
	public int displayPipes;

	/**
	 * 玩家右键点开 GUI 的那一面（ForgeDirection ordinal），-1 = 未知。
	 * 瞬态字段：不写 NBT，只随 {@link MatterInterfaceSyncPKT} 下发，GUI 用它决定 3x3 主网格的朝向；
	 * 拿不到时 GUI 回退「前 = 南」。
	 */
	private byte clickSide = SIDE_UNSET;

	/** 每一面缓存的相邻容器：投递热路径只读这里，失效后才重新查一次方块实体 */
	private final TileEntity[] sideTargets = new TileEntity[SIDE_COUNT];
	/** 抽出端口：惰性创建、长期复用（每个面最多一个） */
	private final IMatterPort[] extractPorts = new IMatterPort[SIDE_COUNT];
	/** 推入端口：同上，双向面因此有两个端口对象 */
	private final IMatterPort[] insertPorts = new IMatterPort[SIDE_COUNT];
	/** {@link #getNetworkPorts()} 的复用缓冲，容量按「每面两个端口」给足，避免每次组网都扩容 */
	private final List<INetworkEndpoint> portBuffer = new ArrayList<>(SIDE_COUNT * 2);

	private int targetRetry;
	private int ticksExisted;
	private int numUsing;

	/** 默认全未设置：字段初始化不能直接 Arrays.fill，交给静态工厂 */
	private static byte[] unsetSides()
	{
		byte[] sides = new byte[SIDE_COUNT];
		Arrays.fill(sides, SIDE_UNSET);
		return sides;
	}

	/**
	 * 端口对象：除了网络契约之外，自带一个可更新的「操作方向」。
	 * 惰性创建时直接取数组当前值，玩家改方向时由 {@link #setSideDirection} 就地刷新，
	 * 因此端口对象永远不用重建 —— 网络的身份去重不会被方向修改打破。
	 */
	protected interface IMatterPort extends INetworkEndpoint
	{
		/** 0..5 = ForgeDirection ordinal；调用方保证只在 0..5 时调用 */
		void setAccessSide(byte accessSide);
	}

	/** 面索引 → ForgeDirection；越界返回 null */
	public static ForgeDirection directionOf(int index)
	{
		return index >= 0 && index < SIDE_COUNT ? ForgeDirection.getOrientation(index) : null;
	}

	public abstract TransportType getTransportType();

	/** 该面的抽出方向：0..5 = ForgeDirection ordinal，-1 = 未设置（客户端读的是同步过来的副本） */
	public byte getExtractSide(int index)
	{
		return index >= 0 && index < SIDE_COUNT ? extractSides[index] : SIDE_UNSET;
	}

	/** 该面的推入方向：0..5 = ForgeDirection ordinal，-1 = 未设置 */
	public byte getInsertSide(int index)
	{
		return index >= 0 && index < SIDE_COUNT ? insertSides[index] : SIDE_UNSET;
	}

	/** 该面是否参与搬运：抽出或推入任一有方向即为开 */
	public boolean isFaceConfigured(int index)
	{
		return getExtractSide(index) >= 0 || getInsertSide(index) >= 0;
	}

	/** 越界 / 脏值一律当成「未设置」；-1..-1（含 -1）之外的负数也归到未设置 */
	private static byte normalizeSideDirection(byte value)
	{
		return value >= 0 && value < SIDE_COUNT ? value : SIDE_UNSET;
	}

	/** 服务端写入某面的抽出 / 推入方向：{@link #SIDE_UNSET} = 清除 */
	public void setSideDirection(int index, boolean extract, byte value)
	{
		if (index < 0 || index >= SIDE_COUNT)
			return;

		byte normalized = normalizeSideDirection(value);
		byte[] sides = extract ? extractSides : insertSides;

		if (sides[index] == normalized)
			return;

		sides[index] = normalized;

		// 端口已存在时只刷新它的操作方向：绝不重建，否则网络会把它当成新端点（身份去重失效）
		IMatterPort port = extract ? extractPorts[index] : insertPorts[index];

		if (port != null && normalized >= 0)
			port.setAccessSide(normalized);

		markDirty();
		markNetworkDirty();
	}

	/**
	 * 客户端就地写入（服务端回包随后覆盖成权威值）：只改数组。
	 * 客户端不建端口、不跑限速，所以这里不碰端口对象也不标脏网络。
	 */
	public void localSetSideDirection(int index, boolean extract, byte value)
	{
		if (index < 0 || index >= SIDE_COUNT)
			return;

		byte[] sides = extract ? extractSides : insertSides;
		sides[index] = normalizeSideDirection(value);
	}

	/** 玩家点开 GUI 的那一面：ForgeDirection ordinal，-1 = 未知 */
	public byte getClickSide()
	{
		return clickSide;
	}

	/** 服务端 / 客户端记录点击面；越界一律记为未知 */
	public void setClickSide(int side)
	{
		this.clickSide = side >= 0 && side < SIDE_COUNT ? (byte) side : SIDE_UNSET;
	}

	/** 六面是否全部关闭：GUI 用来区分「真有容器但一个面都没开」与「根本没容器」 */
	public boolean isAllSidesOff()
	{
		for (int index = 0; index < SIDE_COUNT; index++)
		{
			if (isFaceConfigured(index))
				return false;
		}

		return true;
	}

	public int getPriority()
	{
		return priority;
	}

	/** 服务端改优先级，范围 0..9 */
	public void setPriority(int priority)
	{
		int clamped = MathHelper.clamp_int(priority, 0, 9);

		if (this.priority == clamped)
			return;

		this.priority = clamped;
		markNetworkDirty();
		syncToClients();
	}

	public void localAddPriority(int delta)
	{
		this.priority = MathHelper.clamp_int(priority + delta, 0, 9);
	}

	/**
	 * 客户端应用服务端同步的 GUI 状态
	 * （含点击面、六面抽出 / 推入方向、过滤模板、流体标记、速率 / 频率设置）。
	 */
	public void applySync(int newPriority, byte newClickSide, boolean connected, int pipes, byte[] newExtractSides,
		byte[] newInsertSides, ItemStack[] newFilter, ItemStack[] newOutputFilter, FluidStack[] newFluidFilter,
		FluidStack[] newFluidOutputFilter, long newRateLimit, int newRateUnit, long newIntervalValue, int newIntervalUnit)
	{
		this.priority = MathHelper.clamp_int(newPriority, 0, 9);
		this.clickSide = normalizeSideDirection(newClickSide);
		this.displayConnected = connected;
		this.displayPipes = pipes;
		// 客户端不跑限速逻辑，只把值摆上去给 GUI 读，不动令牌桶
		this.rateLimit = Math.max(0L, newRateLimit);
		this.rateUnit = MathHelper.clamp_int(newRateUnit, 0, 1);
		this.intervalValue = Math.max(0L, newIntervalValue);
		this.intervalUnit = MathHelper.clamp_int(newIntervalUnit, 0, 1);

		if (newExtractSides != null)
		{
			for (int i = 0; i < SIDE_COUNT && i < newExtractSides.length; i++)
				extractSides[i] = normalizeSideDirection(newExtractSides[i]);
		}

		if (newInsertSides != null)
		{
			for (int i = 0; i < SIDE_COUNT && i < newInsertSides.length; i++)
				insertSides[i] = normalizeSideDirection(newInsertSides[i]);
		}

		// 两套过滤模板也走同一个同步包：客户端要靠它把 ghost 标记画进槽位
		applyItemFilter(filter, newFilter);
		applyItemFilter(outputFilter, newOutputFilter);

		// 流体标记由子类自己接（判定与色块都读它），物品接口没有这一份数据
		applyFluidFilter(newFluidFilter, newFluidOutputFilter);
	}

	/** 同步包 → 某一张物品过滤表：越界槽位清空，其余一律存 size=1 的副本 */
	private static void applyItemFilter(ItemStack[] table, ItemStack[] source)
	{
		if (source == null)
			return;

		for (int i = 0; i < table.length; i++)
			table[i] = i < source.length ? copyTemplate(source[i]) : null;
	}

	/**
	 * 基类钩子：子类接收自己的流体过滤数据。默认空实现 —— 物品接口没有这一份数据。
	 * 流体接口在这里把同步过来的两套流体标记分别写回 fluidFilter / fluidOutputFilter。
	 */
	protected void applyFluidFilter(FluidStack[] newFluidFilter, FluidStack[] newFluidOutputFilter)
	{
	}

	/** 基类钩子：同步包要下发的「抽入」流体标记；物品接口没有这份数据，返回 null（同步包因此不写这段载荷） */
	protected FluidStack[] getFluidFilterForSync()
	{
		return null;
	}

	/** 基类钩子：同步包要下发的「推出」流体标记；物品接口返回 null */
	protected FluidStack[] getFluidOutputFilterForSync()
	{
		return null;
	}

	/**
	 * 基类钩子：该槽的流体标记，物品接口恒 null（GUI 据此决定要不要画色块 / 显示流体名）。
	 * slot 与物品槽同号：0..8 = 抽入表，9..17 = 推出表。
	 */
	public FluidStack getFluidFilter(int slot)
	{
		return null;
	}

	/** 槽位索引 → 表内格索引 0..{@link #FILTER_SET_SIZE}-1（调用方保证 slot 已在 0..17 内） */
	public static int filterIndex(int slot)
	{
		return slot % FILTER_SET_SIZE;
	}

	/** 槽位索引 → 该套物品过滤表：0..8 抽入表，9..17 推出表 */
	protected ItemStack[] itemTable(int slot)
	{
		return slot < FILTER_SET_SIZE ? filter : outputFilter;
	}

	/**
	 * 清除某槽标记：物品接口清该槽所属的那张物品表，流体接口覆写后连自己的流体表一起清。
	 * 清完立刻标脏（落盘 + 组网），下发同步仍由调用方负责。
	 */
	protected void clearFilter(int slot)
	{
		if (slot < 0 || slot >= FILTER_SIZE)
			return;

		itemTable(slot)[filterIndex(slot)] = null;
		onFilterChanged();
	}

	/** 服务端处理 GUI 按钮：1 = 优先级 -1，2 = 优先级 +1 */
	public void handleButton(EntityPlayer player, int action)
	{
		if (!canInteract(player))
			return;

		switch (action)
		{
			case 1:
				setPriority(priority - 1);
				break;
			case 2:
				setPriority(priority + 1);
				break;
			default:
				break;
		}

		syncToClients();
		syncToPlayer(player);
	}

	/** 服务端处理方向选择器：extract = true 写抽出方向，false 写推入方向；-1 = 清除该角色 */
	public void handleSideDirection(EntityPlayer player, boolean extract, int sideIndex, int direction)
	{
		if (!canInteract(player) || sideIndex < 0 || sideIndex >= SIDE_COUNT)
			return;

		if (direction < SIDE_UNSET || direction >= SIDE_COUNT)
			return;

		setSideDirection(sideIndex, extract, (byte) direction);
		syncToClients();
		syncToPlayer(player);
	}

	/**
	 * 服务端处理过滤槽标记（FILTER 包）：只写「模板」，不碰任何玩家的手持物品。
	 * slot = 0..8 写抽入表，9..17 写推出表；stack == null 表示清除该槽。
	 */
	public void handleFilter(EntityPlayer player, int slot, ItemStack stack)
	{
		if (!canInteract(player) || slot < 0 || slot >= FILTER_SIZE)
			return;

		setFilterTemplate(slot, stack);
		syncToClients();
		syncToPlayer(player);
	}

	/**
	 * 写入 / 清除过滤槽标记：永远只存一份 size=1 的副本（NBT 保留），
	 * 因此标记既不会被消耗，也拿不出来 —— 它只是一张模板。
	 * 槽位索引自己带表：0..8 = 抽入表，9..17 = 推出表（客户端与服务端用同一套索引，不必同步「在看哪一套」）。
	 */
	public void setFilterTemplate(int slot, ItemStack stack)
	{
		if (slot < 0 || slot >= FILTER_SIZE)
			return;

		itemTable(slot)[filterIndex(slot)] = copyTemplate(stack);
		onFilterChanged();
	}

	/** 模板副本：size 固定 1，其余字段（含 NBT）原样保留；null 透传 */
	private static ItemStack copyTemplate(ItemStack stack)
	{
		if (stack == null)
			return null;

		ItemStack template = stack.copy();
		template.stackSize = 1;
		return template;
	}

	public long getRateLimit()
	{
		return rateLimit;
	}

	public int getRateUnit()
	{
		return rateUnit;
	}

	public long getIntervalValue()
	{
		return intervalValue;
	}

	public int getIntervalUnit()
	{
		return intervalUnit;
	}

	/** 每秒速率：/tick 单位乘 20，乘法溢出时夹到 Long.MAX_VALUE */
	public long ratePerSecond()
	{
		return rateUnit == 1 ? clampMul(rateLimit, TICKS_PER_SECOND) : rateLimit;
	}

	/** 频率间隔秒数：0 = 不限制；/tick 按目标 20 TPS 折算，与真实 TPS 无关 */
	public double intervalSeconds()
	{
		if (intervalValue <= 0L)
			return 0.0D;

		return intervalUnit == 0 ? (double) intervalValue : (double) intervalValue / (double) TICKS_PER_SECOND;
	}

	/** 服务端写入速率上限：负数按 0（不限速）处理，单位越界夹到 0..1 */
	public void setRateLimit(long limit, int unit)
	{
		long clamped = Math.max(0L, limit);
		int clampedUnit = MathHelper.clamp_int(unit, 0, 1);

		if (rateLimit == clamped && rateUnit == clampedUnit)
			return;

		this.rateLimit = clamped;
		this.rateUnit = clampedUnit;
		markDirty();
		resetLimitState();
	}

	/** 服务端写入输出频率间隔：负数按 0（不限制）处理，单位越界夹到 0..1 */
	public void setInterval(long value, int unit)
	{
		long clamped = Math.max(0L, value);
		int clampedUnit = MathHelper.clamp_int(unit, 0, 1);

		if (intervalValue == clamped && intervalUnit == clampedUnit)
			return;

		this.intervalValue = clamped;
		this.intervalUnit = clampedUnit;
		markDirty();
		resetLimitState();
	}

	/** 服务端处理 GUI「应用」：校验距离后写入速率 / 频率，再回同步包 */
	public void handleLimits(EntityPlayer player, long newRateLimit, int newRateUnit, long newIntervalValue, int newIntervalUnit)
	{
		if (!canInteract(player))
			return;

		setRateLimit(newRateLimit, newRateUnit);
		setInterval(newIntervalValue, newIntervalUnit);

		syncToClients();
		syncToPlayer(player);
	}

	/**
	 * 频率门：间隔未到点时返回 false，端口据此直接报告 0（网络看到没空间自然跳过本轮）。
	 * 判据只有 System.nanoTime()：服务器掉到 10 TPS 时仍然是「每真实秒一次」，
	 * 也不受动态调度档位影响 —— 不读世界时间，也不数 tick。
	 */
	protected final boolean isPushDue()
	{
		if (intervalValue <= 0L || lastPushNanos == 0L)
			return true;

		double intervalNanos = intervalSeconds() * 1.0E9D;

		if (Double.isNaN(intervalNanos) || intervalNanos <= 0.0D)
			return true;

		long elapsed = System.nanoTime() - lastPushNanos;

		// 回绕 / 时间戳跳变时按「到点」处理：宁可少限一轮，也不要把端口永久卡死
		return elapsed < 0L || (double) elapsed >= intervalNanos;
	}

	/** 当前可用整令牌数：rate = 0 时返回 Long.MAX_VALUE，且完全不碰令牌桶（零开销快速路径） */
	protected final long availableTokens()
	{
		if (rateLimit == 0L)
			return Long.MAX_VALUE;

		refillTokens();

		if (Double.isNaN(tokens) || tokens <= 0.0D)
			return 0L;

		if (tokens >= (double) Long.MAX_VALUE)
			return Long.MAX_VALUE;

		return (long) tokens;
	}

	/**
	 * 真正接收了 moved 个（物品按件 / 流体按 mB）之后调用：
	 * 推进频率时间戳并扣掉令牌。simulate 路径绝不调用它。
	 */
	protected final void onPushed(int moved)
	{
		if (intervalValue > 0L)
			lastPushNanos = System.nanoTime();

		if (rateLimit == 0L || moved <= 0)
			return;

		tokens -= moved;

		if (Double.isNaN(tokens) || tokens < 0.0D)
			tokens = 0.0D;
	}

	/** 按真实经过时间补令牌：tokens = min(cap, tokens + 经过秒数 * 每秒速率) */
	private void refillTokens()
	{
		long now = System.nanoTime();
		double cap = bucketCapacity();

		if (lastRefillNanos == 0L)
		{
			// 首次使用（含改设置后重置）：直接把桶装满，不必等一轮
			lastRefillNanos = now;
			tokens = cap;
			return;
		}

		long elapsed = now - lastRefillNanos;

		if (elapsed <= 0L)
		{
			if (elapsed < 0L)
				lastRefillNanos = now;

			return;
		}

		lastRefillNanos = now;
		double refilled = tokens + (elapsed / 1.0E9D) * (double) ratePerSecond();

		// NaN / 超过容量一律压回 cap；cap 自身已防溢出
		tokens = Double.isNaN(refilled) || refilled > cap ? cap : refilled;
	}

	/**
	 * 桶容量 = max(1, 每秒速率 * 容量窗口)。容量窗口 = 频率间隔秒数；
	 * 没有频率限制时取 1 秒 —— 否则 cap 恒为 1，单轮最多搬 1 个，设定速率形同虚设。
	 */
	private double bucketCapacity()
	{
		double seconds = intervalValue > 0L ? intervalSeconds() : DEFAULT_BUCKET_SECONDS;
		double cap = (double) ratePerSecond() * seconds;

		if (Double.isNaN(cap) || cap < 1.0D)
			cap = 1.0D;

		return cap > MAX_BUCKET_TOKENS ? MAX_BUCKET_TOKENS : cap;
	}

	/** 改设置后重置令牌桶与频率时间戳：新设置立刻生效 */
	private void resetLimitState()
	{
		this.tokens = 0.0D;
		this.lastRefillNanos = 0L;
		this.lastPushNanos = 0L;
	}

	/** 乘法防溢出：超过 Long.MAX_VALUE 一律夹到 Long.MAX_VALUE */
	private static long clampMul(long value, long factor)
	{
		if (value <= 0L || factor <= 0L)
			return 0L;

		return value > Long.MAX_VALUE / factor ? Long.MAX_VALUE : value * factor;
	}

	private boolean canInteract(EntityPlayer player)
	{
		if (worldObj == null || worldObj.isRemote || player == null)
			return false;

		return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
	}

	/** 把端点状态推给附近客户端（只有在有人打开 GUI 时才推送） */
	public void syncToClients()
	{
		if (worldObj == null || worldObj.isRemote)
			return;

		PacketHandler.sendToAllAround(createSyncPacket(),
			new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 8));
	}

	private void syncToPlayer(EntityPlayer player)
	{
		if (player instanceof EntityPlayerMP)
			PacketHandler.sendTo(createSyncPacket(), (EntityPlayerMP) player);
	}

	private MatterInterfaceSyncPKT createSyncPacket()
	{
		displayConnected = hasConnectedPort();
		displayPipes = countPipes();

		return new MatterInterfaceSyncPKT(xCoord, yCoord, zCoord, priority, clickSide, displayConnected, displayPipes,
			extractSides, insertSides, filter, outputFilter, rateLimit, rateUnit, intervalValue, intervalUnit,
			getTransportType(), getFluidFilterForSync(), getFluidOutputFilterForSync());
	}

	/**
	 * 是否有任何一面（不论开关）贴着本类型的有效容器。
	 * 只给 GUI 做提示用：客户端也能就地算（读客户端世界的方块实体），所以不必新增同步字段；
	 * 刻意不动 {@link #sideTargets} 缓存，也不参与端口模型 —— 没设方向的面照样不进网络。
	 */
	public boolean hasAdjacentContainer()
	{
		if (worldObj == null)
			return false;

		for (int index = 0; index < SIDE_COUNT; index++)
		{
			ForgeDirection dir = directionOf(index);
			int x = xCoord + dir.offsetX;
			int y = yCoord + dir.offsetY;
			int z = zCoord + dir.offsetZ;

			if (!worldObj.blockExists(x, y, z))
				continue;

			if (isPortTarget(worldObj.getTileEntity(x, y, z)))
				return true;
		}

		return false;
	}

	/** 是否至少有一个已设方向的面连到了容器 */
	private boolean hasConnectedPort()
	{
		for (int index = 0; index < SIDE_COUNT; index++)
		{
			if (isFaceConfigured(index) && getSideTarget(index) != null)
				return true;
		}

		return false;
	}

	public int getNetworkColor()
	{
		if (worldObj == null)
			return -1;

		Block block = worldObj.getBlock(xCoord, yCoord, zCoord);

		if (!(block instanceof BlockMatterInterface))
			return -1;

		return worldObj.getBlockMetadata(xCoord, yCoord, zCoord) & 15;
	}

	/**
	 * 该面相邻的容器（缓存优先）。缓存为空或失效时才查一次方块实体，
	 * 之后由 {@link #refreshSideTargets()} 按 {@link #TARGET_RETRY_INTERVAL} 补。
	 */
	protected TileEntity getSideTarget(int index)
	{
		if (index < 0 || index >= SIDE_COUNT || worldObj == null || worldObj.isRemote)
			return null;

		TileEntity cached = sideTargets[index];

		if (cached != null && !cached.isInvalid())
			return cached;

		TileEntity resolved = resolveSideTarget(index);
		sideTargets[index] = resolved;
		return resolved;
	}

	private TileEntity resolveSideTarget(int index)
	{
		ForgeDirection dir = directionOf(index);
		int x = xCoord + dir.offsetX;
		int y = yCoord + dir.offsetY;
		int z = zCoord + dir.offsetZ;

		if (!worldObj.blockExists(x, y, z))
			return null;

		TileEntity tile = worldObj.getTileEntity(x, y, z);

		return isPortTarget(tile) ? tile : null;
	}

	/** 可作为端口目标的容器：类型对口，且不是管道 / 另一个物质接口 */
	private boolean isPortTarget(TileEntity tile)
	{
		if (tile == null || tile instanceof TileMatterPipe || tile instanceof TileMatterInterface)
			return false;

		return isValidTarget(tile);
	}

	/** 该面方块实体是否是本类型接口认可的容器 */
	protected abstract boolean isValidTarget(TileEntity tile);

	/**
	 * 创建一个端口对象：accessSide = 建号时的操作方向（0..5），extract = 该端口是抽出还是推入角色。
	 * 只由 {@link #portAt(int, boolean)} 惰性调用一次，之后长期复用。
	 */
	protected abstract IMatterPort createPort(int index, byte accessSide, boolean extract);

	/**
	 * 只返回能真正干活的端口：该面抽出 / 推入任一有方向 + 该面邻居是有效容器 + 邻居不是管道 / 物质接口。
	 * 双向面产出两个端口对象：抽出端口（mode=INPUT，用 extractSides 当访问方向）
	 * 与推入端口（mode=OUTPUT，用 insertSides）。
	 */
	@Override
	public List<INetworkEndpoint> getNetworkPorts()
	{
		portBuffer.clear();

		if (worldObj == null || worldObj.isRemote)
			return portBuffer;

		for (int index = 0; index < SIDE_COUNT; index++)
		{
			boolean extract = extractSides[index] >= 0;
			boolean insert = insertSides[index] >= 0;

			if (!extract && !insert)
				continue;

			if (getSideTarget(index) == null)
				continue;

			if (extract)
				portBuffer.add(portAt(index, true));

			if (insert)
				portBuffer.add(portAt(index, false));
		}

		return portBuffer;
	}

	private IMatterPort portAt(int index, boolean extract)
	{
		IMatterPort[] ports = extract ? extractPorts : insertPorts;
		IMatterPort port = ports[index];

		if (port == null)
		{
			byte accessSide = extract ? extractSides[index] : insertSides[index];

			if (accessSide < 0)
				accessSide = (byte) directionOf(index).getOpposite().ordinal();

			port = createPort(index, accessSide, extract);
			ports[index] = port;
		}

		return port;
	}

	/** 邻居变化 / 方块实体重新校验时让六面的目标缓存一起失效 */
	public void invalidateTarget()
	{
		Arrays.fill(sideTargets, null);
		this.targetRetry = 0;
	}

	@Override
	public void validate()
	{
		super.validate();
		invalidateTarget();
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		invalidateTarget();
		// 端点消失后要求重扫，避免网络继续持有失效的端口实例
		MatterNetworkManager.INSTANCE.requestRebuild(worldObj);
	}

	@Override
	public void onChunkUnload()
	{
		super.onChunkUnload();
		invalidateTarget();
		MatterNetworkManager.INSTANCE.requestRebuild(worldObj);
	}

	@Override
	public void updateEntity()
	{
		if (worldObj == null || worldObj.isRemote)
			return;

		ticksExisted++;

		if (--targetRetry <= 0)
		{
			targetRetry = TARGET_RETRY_INTERVAL;
			refreshSideTargets();
		}

		if (numUsing > 0 && ticksExisted % SYNC_INTERVAL == 0)
			syncToClients();
	}

	/**
	 * 清掉失效缓存，并为「已设方向但现在没有目标」的面重取一次。
	 * 端口可见性一旦变化就让相邻网络重扫 —— 否则后来才放下的箱子要等别人把网络标脏才会被看到。
	 */
	private void refreshSideTargets()
	{
		boolean portsChanged = false;

		for (int index = 0; index < SIDE_COUNT; index++)
		{
			if (!isFaceConfigured(index))
				continue;

			TileEntity cached = sideTargets[index];

			if (cached != null && !cached.isInvalid())
				continue;

			if (cached != null)
			{
				sideTargets[index] = null;
				portsChanged = true;
			}

			TileEntity resolved = resolveSideTarget(index);

			if (resolved != null)
			{
				sideTargets[index] = resolved;
				portsChanged = true;
			}
		}

		if (portsChanged)
			markNetworkDirty();
	}

	/** 运行期改方向 / 优先级 / 过滤后重扫端点 */
	public void markNetworkDirty()
	{
		if (worldObj == null || worldObj.isRemote)
			return;

		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			TileEntity tile = getAdjacentPipe(ForgeDirection.VALID_DIRECTIONS[i]);

			if (tile == null)
				continue;

			MatterNetwork network = MatterNetworkManager.INSTANCE.getNetworkAt(worldObj, tile.xCoord, tile.yCoord, tile.zCoord);

			if (network != null)
				network.markDirty();
		}
	}

	/** 相邻同类型同色管道；颜色锁定保证相邻管道都属于同一张网 */
	private TileEntity getAdjacentPipe(ForgeDirection dir)
	{
		int x = xCoord + dir.offsetX;
		int y = yCoord + dir.offsetY;
		int z = zCoord + dir.offsetZ;

		if (!MatterNetworkManager.INSTANCE.isPipe(worldObj, x, y, z))
			return null;

		TileEntity tile = worldObj.getTileEntity(x, y, z);

		if (!(tile instanceof TileMatterPipe pipe))
			return null;

		if (pipe.getTransportType() != getTransportType() || pipe.getColor() != getNetworkColor())
			return null;

		return tile;
	}

	/** 相连网络的管道总数；查不到网络时退回相邻管道计数 */
	public int countPipes()
	{
		if (worldObj == null || worldObj.isRemote)
			return 0;

		int adjacent = 0;
		MatterNetwork network = null;

		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			TileEntity tile = getAdjacentPipe(ForgeDirection.VALID_DIRECTIONS[i]);

			if (tile == null)
				continue;

			adjacent++;

			if (network == null)
				network = MatterNetworkManager.INSTANCE.getNetworkAt(worldObj, tile.xCoord, tile.yCoord, tile.zCoord);
		}

		return network != null ? network.getMembers().size() : adjacent;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		this.priority = MathHelper.clamp_int(nbt.getInteger("Priority"), 0, 9);
		// 旧存档没有这几个字段：getLong / getInteger 缺省即 0 = 不限速、不限频
		this.rateLimit = Math.max(0L, nbt.getLong("RateLimit"));
		this.rateUnit = MathHelper.clamp_int(nbt.getInteger("RateUnit"), 0, 1);
		this.intervalValue = Math.max(0L, nbt.getLong("IntervalValue"));
		this.intervalUnit = MathHelper.clamp_int(nbt.getInteger("IntervalUnit"), 0, 1);
		resetLimitState();

		// 旧存档的 Mode / Facing / SideModes / AccessSides 读到即忽略：
		// 三态模式与 AUTO 操作面已被「按面方向」取代，升级后六面全关，需要重新配置
		Arrays.fill(extractSides, SIDE_UNSET);
		Arrays.fill(insertSides, SIDE_UNSET);

		byte[] storedExtract = nbt.getByteArray("ExtractSides");

		for (int i = 0; i < SIDE_COUNT && i < storedExtract.length; i++)
			extractSides[i] = normalizeSideDirection(storedExtract[i]);

		byte[] storedInsert = nbt.getByteArray("InsertSides");

		for (int i = 0; i < SIDE_COUNT && i < storedInsert.length; i++)
			insertSides[i] = normalizeSideDirection(storedInsert[i]);

		// 迁移语义：旧存档只有 Filter 一个键，它读进【抽入表】；OutputFilter 缺失 ⇒ 推出表为空 ⇒ 全通，
		// 即升级后「以前那 9 格过滤继续管抽取，推出方向一开始什么都不拦」，玩家要拦再自己标推出表
		readItemFilter(nbt, "Filter", filter);
		readItemFilter(nbt, "OutputFilter", outputFilter);
	}

	/** NBT → 某一张物品过滤表：只认 0..FILTER_SET_SIZE-1 的槽，读之前先清空 */
	private static void readItemFilter(NBTTagCompound nbt, String key, ItemStack[] table)
	{
		Arrays.fill(table, null);

		NBTTagList list = nbt.getTagList(key, 10);

		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound entry = list.getCompoundTagAt(i);
			int slot = entry.getByte("Slot") & 255;

			if (slot < table.length)
				table[slot] = copyTemplate(ItemStack.loadItemStackFromNBT(entry));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);

		nbt.setInteger("Priority", priority);
		nbt.setByteArray("ExtractSides", Arrays.copyOf(extractSides, SIDE_COUNT));
		nbt.setByteArray("InsertSides", Arrays.copyOf(insertSides, SIDE_COUNT));
		nbt.setLong("RateLimit", rateLimit);
		nbt.setInteger("RateUnit", rateUnit);
		nbt.setLong("IntervalValue", intervalValue);
		nbt.setInteger("IntervalUnit", intervalUnit);

		nbt.setTag("Filter", writeItemFilter(filter));
		nbt.setTag("OutputFilter", writeItemFilter(outputFilter));
	}

	/** 某一张物品过滤表 → NBT 列表：每个非空槽一个 Slot 字节（表内索引 0..8）+ 模板自身字段 */
	private static NBTTagList writeItemFilter(ItemStack[] table)
	{
		NBTTagList list = new NBTTagList();

		for (int i = 0; i < table.length; i++)
		{
			if (table[i] == null)
				continue;

			NBTTagCompound entry = new NBTTagCompound();
			entry.setByte("Slot", (byte) i);
			table[i].writeToNBT(entry);
			list.appendTag(entry);
		}

		return list;
	}

	@Override
	public int getSizeInventory()
	{
		return FILTER_SIZE;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return slot >= 0 && slot < FILTER_SIZE ? itemTable(slot)[filterIndex(slot)] : null;
	}

	/**
	 * 过滤槽里是虚拟标记，不是真货：任何抽取路径都拿不到东西。
	 * 清标记只走 {@link #setFilterTemplate(int, ItemStack)}（GUI 右键 / 空手左键或 FILTER 包）。
	 */
	@Override
	public ItemStack decrStackSize(int slot, int amount)
	{
		return null;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		return null;
	}

	/**
	 * 外部写入（含别的模组的管道）一律降级成模板：size 固定 1、不改动调用方手里的 stack。
	 * 本模组自己的写入走 {@link #setFilterTemplate(int, ItemStack)}。
	 */
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		setFilterTemplate(slot, stack);
	}

	private void onFilterChanged()
	{
		markDirty();
		markNetworkDirty();
	}

	@Override
	public String getInventoryName()
	{
		return "pe.matterinterface";
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
			&& player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory()
	{
		numUsing++;
	}

	@Override
	public void closeInventory()
	{
		if (numUsing > 0)
			numUsing--;
	}

	/** 过滤区只存模板：自动化 / shift-click 都不能往里塞实物 */
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		return false;
	}
}
