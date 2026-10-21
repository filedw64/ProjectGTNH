package moze_intel.projecte.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterPipe;
import moze_intel.projecte.transport.api.EndpointMode;
import moze_intel.projecte.transport.api.IFluidEndpoint;
import moze_intel.projecte.transport.api.IItemEndpoint;
import moze_intel.projecte.transport.api.INetworkEndpoint;
import moze_intel.projecte.transport.api.IPortOwner;
import moze_intel.projecte.transport.api.IPortProvider;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

/**
 * 一张同类型 + 同颜色的连通管道网。
 * 管道本身不抽不送，网络只负责把 INPUT 端点已经抽进网络的货物瞬时交给 OUTPUT 端点。
 *
 * 有限档（暗物质 / 红物质）：吞吐上限 = 成员管道每秒速率之和（long 求和后夹到网络上限），
 * 每 interval tick 投递一次，本 tick 总量 = 每秒速率 × interval / 20，超过 int 安全上限时平均拆成多份，
 * 于是实际每秒速率与 interval 无关。interval 由 {@link #getInterval()} 给出：
 * 动态模式读 {@link LogisticsScheduler}（1 / 5 / 20，按 MSPT 浮动），固定模式读配置 pipeTickInterval。
 * 错峰判定一律用 {@link LogisticsScheduler#getTickCounter()}，不用 world time —— 否则会被时间加速带快。
 * 无限档（Gem）：网里只要有任意一根 unlimited 管道，整张网就不做每秒预算、也不拆份，
 * 而是在一次操作内循环「先 simulate 求容量 → 抽取 → 投递」，来源抽空或没有接收空间立刻早停。
 * EMC：语义上永不限速（TransportType.isUnlimited 对 EMC 恒真），根本不走上面的预算 / 拆份两条路径，
 * 由 tickEmc 直接调 EmcMatterTransport，按"组网即导通"的方式收集 + 推送，只受设备频道数与缓冲软上限约束。
 */
public class MatterNetwork
{
	/** 稳定排序：同优先级保持收集顺序 */
	private static final Comparator<IItemEndpoint> ITEM_OUTPUT_ORDER = (a, b) -> Integer.compare(b.getPriority(), a.getPriority());
	private static final Comparator<IFluidEndpoint> FLUID_OUTPUT_ORDER = (a, b) -> Integer.compare(b.getPriority(), a.getPriority());

	private final World world;
	private final TransportType type;
	private final int color;
	private final int tickOffset;
	/** 与 MatterNetworkManager.WorldData 共享的按颜色 EMC 缓冲，重建时由新网络继续接管 */
	private final double[] emcBuffer;
	/**
	 * EMC 专用：成员邻居的提供方 / 接受方缓存。
	 * 只有 EMC 网络才有实例；它随网络对象一起重建，所以 WorldData 变脏 = 缓存必然重扫一次。
	 */
	private final EmcMatterTransport.DeviceCache emcDevices;
	/** EMC 专用：本网可接入的设备频道数（成员最高档位 → 16 / 256 / 无上限） */
	private int emcChannels;

	private final Set<TileMatterPipe> members = new HashSet<>();
	private final List<INetworkEndpoint> endpoints = new ArrayList<>();
	/** 全网每秒可搬运的数量（成员每秒速率 long 求和后夹到网络上限） */
	private int perSecond;
	/** 网里有 unlimited（Gem）成员：整张网一次操作搬空来源，不走每秒预算与拆份 */
	private boolean unlimited;
	/** 有限档专用：本 tick 要执行几份投递（单份超过 int 安全上限时才会 > 1） */
	private int operationRounds = 1;
	/** 有限档专用：每份可搬运的数量 */
	private int roundBudget;
	/** 有限档专用：最后一份的数量（吸收 每秒速率 × interval / 20 的取整余数） */
	private int lastRoundAmount;
	private boolean endpointsDirty = true;

	private final List<IItemEndpoint> itemInputs = new ArrayList<>();
	private final List<IItemEndpoint> itemOutputs = new ArrayList<>();
	private final List<IFluidEndpoint> fluidInputs = new ArrayList<>();
	private final List<IFluidEndpoint> fluidOutputs = new ArrayList<>();

	/**
	 * 探测用的临时 stack，热路径复用：一次操作里连着跑几十个 OUTPUT，
	 * 每端点一份 ItemStack / FluidStack 会白白分配（NBT 还要深拷贝）。
	 * 端点契约禁止实现方保留或修改传入的 stack，所以复用不会串味。
	 */
	private ItemStack probeItem;
	private FluidStack probeFluid;

	private int endpointCursor;
	private int inputRotation;
	private int outputRotation;

	private int windowStart;
	private int windowSize;

	public MatterNetwork(World world, TransportType type, int color, int tickOffset, double[] emcBuffer)
	{
		this.world = world;
		this.type = type;
		this.color = color;
		this.tickOffset = tickOffset;
		this.emcBuffer = emcBuffer;
		this.emcDevices = type == TransportType.EMC ? new EmcMatterTransport.DeviceCache() : null;
	}

	public TransportType getType()
	{
		return type;
	}

	public int getColor()
	{
		return color;
	}

	public int getTickOffset()
	{
		return tickOffset;
	}

	/** 该网每秒可搬运的数量；unlimited 网报的是网络上限（真实搬运不受它限制） */
	public int getThroughputPerSecond()
	{
		return perSecond;
	}

	/** 该网是否 unlimited：网里只要有任意一根 Gem 档管道即成立 */
	public boolean isUnlimited()
	{
		return unlimited;
	}

	/** 本 tick 第 round 份操作可搬运的数量 */
	private int roundAmount(int round)
	{
		return round == operationRounds - 1 ? lastRoundAmount : roundBudget;
	}

	public Set<TileMatterPipe> getMembers()
	{
		return members;
	}

	/** EMC 设备缓存；非 EMC 网络为 null */
	public EmcMatterTransport.DeviceCache getEmcDevices()
	{
		return emcDevices;
	}

	/** EMC 网络可接入的设备频道数（非 EMC 网络为 0） */
	public int getEmcChannels()
	{
		return emcChannels;
	}

	public List<INetworkEndpoint> getEndpoints()
	{
		return Collections.unmodifiableList(endpoints);
	}

	public void addMember(TileMatterPipe pipe)
	{
		if (members.add(pipe))
			markDirty();
	}

	public void removeMember(TileMatterPipe pipe)
	{
		if (members.remove(pipe))
			markDirty();
	}

	/**
	 * 成员变动后重算吞吐：成员每秒速率用 long 累加（2^30 × N 会溢出 int），再统一夹到网络上限。
	 * 只要有一根 unlimited 管道，整张网就是 unlimited —— 但这条只对物品 / 流体生效：
	 * EMC 不用每秒预算与 unlimited 字段（tickEmc 直接调 EmcMatterTransport），它只取成员的最高档位换算频道上限。
	 */
	public void recalculateThroughput()
	{
		boolean hasUnlimitedMember = false;
		int maxTier = 0;
		long sum = 0L;

		for (TileMatterPipe member : members)
		{
			if (!hasUnlimitedMember && type.isUnlimited(member.getTier()))
				hasUnlimitedMember = true;

			if (member.getTier() > maxTier)
				maxTier = member.getTier();

			if (sum < Constants.PIPE_NETWORK_THROUGHPUT_CAP)
				sum += member.getThroughputPerSecond();
		}

		this.unlimited = hasUnlimitedMember && (type == TransportType.ITEM || type == TransportType.FLUID);
		this.perSecond = clampToInt(sum, Constants.PIPE_NETWORK_THROUGHPUT_CAP);
		this.emcChannels = type == TransportType.EMC ? type.getEmcChannels(maxTier) : 0;
		updateOperationThroughput(getInterval());
	}

	/**
	 * 物流执行间隔（tick）。
	 * 动态模式（默认）读 {@link LogisticsScheduler#getInterval()}：按服务端 MSPT 在 1 / 5 / 20 之间浮动，
	 * 且只由 ServerTickEvent 驱动，无视一切时间加速；
	 * 固定模式沿用配置 pipeTickInterval（配置范围 1..100，这里只兜底非法值），保证旧行为可复现。
	 */
	private static int getInterval()
	{
		if (ProjectEConfig.dynamicLogisticsInterval)
			return LogisticsScheduler.getInterval();

		int interval = ProjectEConfig.pipeTickInterval;

		return interval < 1 ? 1 : interval;
	}

	/**
	 * 每次都按当前配置重算本 tick 的操作预算，改配置后无需重建网络即可生效。
	 * 本 tick 总量 = perSecond × interval / 20（long 运算，向下取整但至少 1）；
	 * 超过 PIPE_MAX_OPERATION_AMOUNT 时平均拆成多份，于是平均速率与 interval 无关，单份又不溢出 int。
	 */
	private void updateOperationThroughput(int interval)
	{
		if (perSecond <= 0)
		{
			this.operationRounds = 1;
			this.roundBudget = 0;
			this.lastRoundAmount = 0;
			return;
		}

		long required = (long) perSecond * interval / 20L;

		if (required < 1L)
			required = 1L;

		long max = Constants.PIPE_MAX_OPERATION_AMOUNT;
		int rounds = (int) ((required + max - 1L) / max);

		this.operationRounds = rounds;
		this.roundBudget = clampToInt((required + rounds - 1L) / rounds, Constants.PIPE_MAX_OPERATION_AMOUNT);
		this.lastRoundAmount = clampToInt(required - (long) roundBudget * (rounds - 1), Constants.PIPE_MAX_OPERATION_AMOUNT);
	}

	/** long → int 的唯一夹取入口：0..max，避免各处散落强转与魔法数 */
	private static int clampToInt(long value, int max)
	{
		if (value <= 0L)
			return 0;

		return (int) Math.min(value, (long) max);
	}

	public void addEndpoint(INetworkEndpoint endpoint)
	{
		if (!endpoints.contains(endpoint))
		{
			endpoints.add(endpoint);
			markDirty();
		}
	}

	public void removeEndpoint(INetworkEndpoint endpoint)
	{
		if (endpoints.remove(endpoint))
			markDirty();
	}

	/** 端点布局 / 过滤 / 优先级变动时调用，下一 tick 重扫邻居端点（EMC 设备缓存同时标脏） */
	public void markDirty()
	{
		this.endpointsDirty = true;

		if (emcDevices != null)
			emcDevices.invalidate();
	}

	/** 扫描所有成员的 6 个邻居，收集贴在本网上的端点；只有 endpointsDirty 时才被调用 */
	public void collectEndpoints()
	{
		endpoints.clear();

		for (TileMatterPipe member : members)
		{
			for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
			{
				ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
				int x = member.xCoord + dir.offsetX;
				int y = member.yCoord + dir.offsetY;
				int z = member.zCoord + dir.offsetZ;

				if (!world.blockExists(x, y, z))
					continue;

				TileEntity tile = world.getTileEntity(x, y, z);

				// 多端口方块实体（物质接口六面）：逐个端口筛选。端口对象长期复用，
				// 所以 contains 的身份去重照样成立，同一面被多根管道看到也只会收进来一次
				if (tile instanceof IPortProvider)
				{
					List<INetworkEndpoint> ports = ((IPortProvider) tile).getNetworkPorts();

					for (int p = 0; p < ports.size(); p++)
						acceptEndpoint(ports.get(p));

					continue;
				}

				if (tile instanceof INetworkEndpoint)
					acceptEndpoint((INetworkEndpoint) tile);
			}
		}

		endpointsDirty = false;
	}

	/** 单个端点的类型 / 颜色筛选与身份去重：端口与方块实体两条收集路径共用 */
	private void acceptEndpoint(INetworkEndpoint endpoint)
	{
		if (endpoint == null || endpoint.getTransportType() != type)
			return;

		int endpointColor = endpoint.getNetworkColor();

		if (endpointColor >= 0 && endpointColor != color)
			return;

		if (!endpoints.contains(endpoint))
			endpoints.add(endpoint);
	}

	public void tick()
	{
		if (!type.isImplemented() || world == null || world.isRemote)
			return;

		// EMC 管道不做端点，网络直接操作成员的邻居方块，走独立分支
		if (type == TransportType.EMC)
		{
			tickEmc();
			return;
		}

		if (endpointsDirty)
			collectEndpoints();

		if (endpoints.isEmpty())
			return;

		// 错峰：每张网按自己的偏移执行，避免同一 tick 全部开火。
		// 计数器来自 LogisticsScheduler（每个真实服务端 tick 恰好 +1），加速火把 / 时间怀表都改不动它
		if (Math.floorMod(LogisticsScheduler.getTickCounter() + (long) tickOffset, (long) getInterval()) != 0)
			return;

		if (unlimited)
			runUnlimitedOperation();
		else
			runBudgetedOperation();
	}

	/**
	 * EMC 分支：组网即导通 —— 没有每秒预算，也没有份数拆分，一次操作就是把本网搬得动的搬干净。
	 * 仍按 interval 错峰，只是为了避免所有网在同一 tick 一起开火，与速率无关；
	 * 错峰读 LogisticsScheduler 的 tick 计数器，所以重载时 EMC 网络也会跟着降到每秒一次。
	 */
	private void tickEmc()
	{
		if (emcBuffer == null || emcDevices == null)
			return;

		int interval = getInterval();

		if (Math.floorMod(LogisticsScheduler.getTickCounter() + (long) tickOffset, (long) interval) != 0)
			return;

		try
		{
			EmcMatterTransport.transfer(this, world, emcBuffer);
		}
		catch (Exception e)
		{
			PELogger.logError("EMC pipe network transfer failed (color " + color + "): " + e);
		}
	}

	/** 有限档：本 tick 总量按 operationRounds 拆份执行，每份都落在 int 安全范围 */
	private void runBudgetedOperation()
	{
		updateOperationThroughput(getInterval());

		try
		{
			for (int round = 0; round < operationRounds; round++)
			{
				int budget = roundAmount(round);

				if (budget <= 0)
					continue;

				if (type == TransportType.ITEM)
					transferItems(budget);
				else if (type == TransportType.FLUID)
					transferFluid(budget);
			}
		}
		catch (Exception e)
		{
			PELogger.logError("Matter network transfer failed (" + type + " color " + color + "): " + e);
		}
	}

	/** unlimited 档：一次操作搬空来源，没有每秒预算，也没有份数拆分 */
	private void runUnlimitedOperation()
	{
		try
		{
			if (type == TransportType.ITEM)
				transferItemsUnlimited();
			else if (type == TransportType.FLUID)
				transferFluidUnlimited();
		}
		catch (Exception e)
		{
			PELogger.logError("Matter network unlimited transfer failed (" + type + " color " + color + "): " + e);
		}
	}

	/** 单次操作最多遍历 Constants.PIPE_ENDPOINTS_PER_OPERATION 个端点，超出时按游标轮转窗口 */
	private void prepareWindow()
	{
		int total = endpoints.size();
		windowSize = Math.min(total, Constants.PIPE_ENDPOINTS_PER_OPERATION);
		windowStart = total > windowSize ? Math.floorMod(endpointCursor, total) : 0;

		if (total > windowSize)
			endpointCursor = Math.floorMod(windowStart + windowSize, total);
	}

	private INetworkEndpoint windowEndpoint(int index)
	{
		return endpoints.get(Math.floorMod(windowStart + index, endpoints.size()));
	}

	/** 有限档物品搬运：每个 INPUT 抽一份、交给 OUTPUT，单份最多 budget 件 */
	private void transferItems(int budget)
	{
		if (!gatherItemEndpoints())
			return;

		int inputCount = itemInputs.size();
		int outputCount = itemOutputs.size();

		for (int i = 0; i < inputCount; i++)
		{
			IItemEndpoint input = itemInputs.get(Math.floorMod(inputRotation + i, inputCount));
			ItemStack peek = input.extractItems(budget, true);

			if (peek == null || peek.stackSize <= 0)
				continue;

			// 先 simulate 出所有 OUTPUT 的剩余可接收量，再决定抽取量
			int capacity = itemCapacity(peek, budget);

			if (capacity <= 0)
				continue;

			ItemStack taken = input.extractItems(capacity, false);

			if (taken == null || taken.stackSize <= 0)
				continue;

			int remaining = deliverItems(taken, budget);

			if (remaining > 0)
				returnItems(input, taken, remaining);

			inputRotation = Math.floorMod(inputRotation + 1, inputCount);
			outputRotation = Math.floorMod(outputRotation + 1, outputCount);
		}
	}

	/**
	 * unlimited 档物品搬运：对每个 INPUT 反复「先 simulate 求容量 → 抽取 → 投递」，直到来源抽空或没有接收空间。
	 * 每轮请求量都夹在 PIPE_MAX_OPERATION_AMOUNT 内，来源总量超过 int 时自然拆成多轮发送；
	 * 抽取 / 投递各有一个 4096 次的安全上限，异常端点不会让服务端原地打转。
	 */
	private void transferItemsUnlimited()
	{
		if (!gatherItemEndpoints())
			return;

		int inputCount = itemInputs.size();
		int deliverRounds = 0;

		for (int i = 0; i < inputCount && deliverRounds < Constants.PIPE_UNLIMITED_DELIVER_ROUNDS; i++)
		{
			IItemEndpoint input = itemInputs.get(Math.floorMod(inputRotation + i, inputCount));
			int extractRounds = 0;
			int moved = 0;

			while (extractRounds < Constants.PIPE_UNLIMITED_EXTRACT_ROUNDS && deliverRounds < Constants.PIPE_UNLIMITED_DELIVER_ROUNDS)
			{
				extractRounds++;

				ItemStack peek = input.extractItems(Constants.PIPE_MAX_OPERATION_AMOUNT, true);

				if (peek == null || peek.stackSize <= 0)
					break;    // 来源抽空：早停，连容量模拟都不做

				int capacity = itemCapacity(peek, Constants.PIPE_MAX_OPERATION_AMOUNT);

				if (capacity <= 0)
					break;    // 没有任何接收空间：先模拟后早停，绝不先抽再退

				ItemStack taken = input.extractItems(capacity, false);

				if (taken == null || taken.stackSize <= 0)
					break;

				deliverRounds++;
				int remaining = deliverItems(taken, taken.stackSize);
				int roundMoved = taken.stackSize - remaining;

				moved += roundMoved;

				if (remaining > 0)
					returnItems(input, taken, remaining);

				if (roundMoved <= 0)
					break;    // 这一轮一件都没搬动：再循环只会自旋
			}

			// 与有限档一致：真的搬动过才推进轮转，空转不推进
			if (moved > 0)
			{
				inputRotation = Math.floorMod(inputRotation + 1, inputCount);
				outputRotation = Math.floorMod(outputRotation + 1, itemOutputs.size());
			}
		}
	}

	/** 收集窗口内的物品端点并按模式分组；任一组为空就没有搬运的意义，直接返回 false */
	private boolean gatherItemEndpoints()
	{
		itemInputs.clear();
		itemOutputs.clear();
		prepareWindow();

		for (int i = 0; i < windowSize; i++)
		{
			INetworkEndpoint endpoint = windowEndpoint(i);

			if (!(endpoint instanceof IItemEndpoint) || endpoint.getTransportType() != TransportType.ITEM)
				continue;

			IItemEndpoint itemEndpoint = (IItemEndpoint) endpoint;

			if (itemEndpoint.getMode() == EndpointMode.OUTPUT)
				itemOutputs.add(itemEndpoint);
			else
				itemInputs.add(itemEndpoint);
		}

		if (itemInputs.isEmpty() || itemOutputs.isEmpty())
			return false;

		itemOutputs.sort(ITEM_OUTPUT_ORDER);
		return true;
	}

	/** 在 simulate 下求所有 OUTPUT 最多能收多少件（封顶 budget），抽取量以此为准，绝不超抽 */
	private int itemCapacity(ItemStack peek, int budget)
	{
		int capacity = 0;
		int outputCount = itemOutputs.size();

		for (int o = 0; o < outputCount && capacity < budget; o++)
		{
			int offer = Math.min(peek.stackSize, budget - capacity);

			if (offer <= 0)
				break;

			IItemEndpoint output = itemOutputs.get(Math.floorMod(outputRotation + o, outputCount));
			ItemStack probe = probeItemFor(peek, offer);

			if (!output.matchesFilter(probe))
				continue;

			int accepted = output.insertItems(probe, true);

			if (accepted > 0)
				capacity += Math.min(accepted, offer);
		}

		return capacity;
	}

	/** 按优先级 / 轮转把物品投递给 OUTPUT，返回没投出去的件数 */
	private int deliverItems(ItemStack stack, int budget)
	{
		int remaining = stack.stackSize;
		budget = Math.min(budget, remaining);
		int outputCount = itemOutputs.size();

		for (int o = 0; o < outputCount && remaining > 0 && budget > 0; o++)
		{
			IItemEndpoint output = itemOutputs.get(Math.floorMod(outputRotation + o, outputCount));
			int offer = Math.min(remaining, budget);
			ItemStack probe = probeItemFor(stack, offer);

			if (!output.matchesFilter(probe))
				continue;

			int moved = output.insertItems(probe, false);

			if (moved <= 0)
				continue;

			moved = Math.min(moved, offer);
			remaining -= moved;
			budget -= moved;
		}

		return remaining;
	}

	/** 复用同一个探针 stack：内容相同只改数量，热路径不再为每个 OUTPUT 分配 ItemStack */
	private ItemStack probeItemFor(ItemStack source, int amount)
	{
		if (probeItem == null || !sameItemContent(probeItem, source))
			probeItem = source.copy();

		probeItem.stackSize = amount;
		return probeItem;
	}

	private static boolean sameItemContent(ItemStack a, ItemStack b)
	{
		return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
	}

	/** 投不进 OUTPUT 的必须退回原容器；容器也收不下才掉落到世界，保证件数守恒 */
	private void returnItems(IItemEndpoint input, ItemStack source, int amount)
	{
		int left = amount;
		int returned = input.insertItems(copyItem(source, amount), false);

		if (returned > 0)
			left -= Math.min(returned, left);

		if (left <= 0)
			return;

		// 端点可能已经不是方块实体本身，而是端口对象（六面端口），必须先解析出所属方块实体才能定位掉落点
		TileEntity owner = resolveOwner(input);

		if (owner != null)
			world.spawnEntityInWorld(new EntityItem(world, owner.xCoord + 0.5D, owner.yCoord + 0.5D, owner.zCoord + 0.5D, copyItem(source, left)));
		else
			PELogger.logError("Matter pipe network could not return " + left + " item(s) to " + input);
	}

	/** 兜底掉落需要世界坐标：方块实体端点直接用自身，端口对象则回查它所属的方块实体 */
	private static TileEntity resolveOwner(Object endpoint)
	{
		if (endpoint instanceof TileEntity)
			return (TileEntity) endpoint;

		if (endpoint instanceof IPortOwner)
			return ((IPortOwner) endpoint).getPortOwner();

		return null;
	}

	/** 有限档流体搬运：每个 INPUT 抽一份、交给 OUTPUT，单份最多 budget mB */
	private void transferFluid(int budget)
	{
		if (!gatherFluidEndpoints())
			return;

		int inputCount = fluidInputs.size();
		int outputCount = fluidOutputs.size();

		for (int i = 0; i < inputCount; i++)
		{
			IFluidEndpoint input = fluidInputs.get(Math.floorMod(inputRotation + i, inputCount));
			FluidStack peek = input.drainFluid(budget, true);

			if (peek == null || peek.amount <= 0)
				continue;

			int capacity = fluidCapacity(peek, budget);

			if (capacity <= 0)
				continue;

			FluidStack taken = input.drainFluid(capacity, false);

			if (taken == null || taken.amount <= 0)
				continue;

			// 端点报回的量不可信（外来容器实现可能回一个超过请求的数字）：
			// 夹回本次请求量，否则预算账目与退回账目都会被撑爆，且退回量会大于实抽量
			if (taken.amount > capacity)
				taken = copyFluid(taken, capacity);

			int remaining = deliverFluid(taken, budget);

			if (remaining > 0)
				returnFluid(input, taken, remaining);

			inputRotation = Math.floorMod(inputRotation + 1, inputCount);
			outputRotation = Math.floorMod(outputRotation + 1, outputCount);
		}
	}

	/**
	 * unlimited 档流体搬运：与物品同构，先 simulate 求容量再抽取，抽空 / 无空间立即早停。
	 * 每轮请求量夹在 PIPE_MAX_OPERATION_AMOUNT mB 内，超过 int 的储量自然拆成多轮发送。
	 */
	private void transferFluidUnlimited()
	{
		if (!gatherFluidEndpoints())
			return;

		int inputCount = fluidInputs.size();
		int deliverRounds = 0;

		for (int i = 0; i < inputCount && deliverRounds < Constants.PIPE_UNLIMITED_DELIVER_ROUNDS; i++)
		{
			IFluidEndpoint input = fluidInputs.get(Math.floorMod(inputRotation + i, inputCount));
			int extractRounds = 0;
			int moved = 0;

			while (extractRounds < Constants.PIPE_UNLIMITED_EXTRACT_ROUNDS && deliverRounds < Constants.PIPE_UNLIMITED_DELIVER_ROUNDS)
			{
				extractRounds++;

				FluidStack peek = input.drainFluid(Constants.PIPE_MAX_OPERATION_AMOUNT, true);

				if (peek == null || peek.amount <= 0)
					break;    // 来源抽空

				int capacity = fluidCapacity(peek, Constants.PIPE_MAX_OPERATION_AMOUNT);

				if (capacity <= 0)
					break;    // 没有可接收空间：先模拟后早停

				FluidStack taken = input.drainFluid(capacity, false);

				if (taken == null || taken.amount <= 0)
					break;

				// 同上：抽到的量不得超过本次请求量
				if (taken.amount > capacity)
					taken = copyFluid(taken, capacity);

				deliverRounds++;
				int remaining = deliverFluid(taken, taken.amount);
				int roundMoved = taken.amount - remaining;

				moved += roundMoved;

				if (remaining > 0)
					returnFluid(input, taken, remaining);

				if (roundMoved <= 0)
					break;
			}

			if (moved > 0)
			{
				inputRotation = Math.floorMod(inputRotation + 1, inputCount);
				outputRotation = Math.floorMod(outputRotation + 1, fluidOutputs.size());
			}
		}
	}

	/** 收集窗口内的流体端点并按模式分组；任一组为空就没有搬运的意义 */
	private boolean gatherFluidEndpoints()
	{
		fluidInputs.clear();
		fluidOutputs.clear();
		prepareWindow();

		for (int i = 0; i < windowSize; i++)
		{
			INetworkEndpoint endpoint = windowEndpoint(i);

			if (!(endpoint instanceof IFluidEndpoint) || endpoint.getTransportType() != TransportType.FLUID)
				continue;

			IFluidEndpoint fluidEndpoint = (IFluidEndpoint) endpoint;

			if (fluidEndpoint.getMode() == EndpointMode.OUTPUT)
				fluidOutputs.add(fluidEndpoint);
			else
				fluidInputs.add(fluidEndpoint);
		}

		if (fluidInputs.isEmpty() || fluidOutputs.isEmpty())
			return false;

		fluidOutputs.sort(FLUID_OUTPUT_ORDER);
		return true;
	}

	/** 在 simulate 下求所有 OUTPUT 最多能收多少 mB（封顶 budget） */
	private int fluidCapacity(FluidStack peek, int budget)
	{
		int capacity = 0;
		int outputCount = fluidOutputs.size();

		for (int o = 0; o < outputCount && capacity < budget; o++)
		{
			int offer = Math.min(peek.amount, budget - capacity);

			if (offer <= 0)
				break;

			IFluidEndpoint output = fluidOutputs.get(Math.floorMod(outputRotation + o, outputCount));
			FluidStack probe = probeFluidFor(peek, offer);

			if (!output.matchesFilter(probe))
				continue;

			int accepted = output.insertFluid(probe, true);

			// 端点只能在 0..offer 里报数：负数按 0，超出按 offer，容量累加才不会溢出或失真
			if (accepted <= 0)
				continue;

			capacity += Math.min(accepted, offer);
		}

		return capacity;
	}

	/** 按优先级 / 轮转把流体投递给 OUTPUT，返回没投出去的 mB */
	private int deliverFluid(FluidStack stack, int budget)
	{
		int remaining = stack.amount;
		budget = Math.min(budget, remaining);
		int outputCount = fluidOutputs.size();

		for (int o = 0; o < outputCount && remaining > 0 && budget > 0; o++)
		{
			IFluidEndpoint output = fluidOutputs.get(Math.floorMod(outputRotation + o, outputCount));
			int offer = Math.min(remaining, budget);
			FluidStack probe = probeFluidFor(stack, offer);

			if (!output.matchesFilter(probe))
				continue;

			int moved = output.insertFluid(probe, false);

			if (moved <= 0)
				continue;

			moved = Math.min(moved, offer);
			remaining -= moved;
			budget -= moved;
		}

		return remaining;
	}

	/** 复用同一个探针 FluidStack：流体与 tag 相同（isFluidEqual）只改数量 */
	private FluidStack probeFluidFor(FluidStack source, int amount)
	{
		if (probeFluid == null || !probeFluid.isFluidEqual(source))
			probeFluid = source.copy();

		probeFluid.amount = amount;
		return probeFluid;
	}

	/**
	 * 投不进的流体必须按 mB 精确退回原容器。
	 * 已知取舍：流体没有「掉落到世界」的机制，退回失败只能记日志，
	 * 不像物品那样有第三级兜底（因此这里是守恒红线上仅存的已知缺口，不新增流体掉落）。
	 */
	private void returnFluid(IFluidEndpoint input, FluidStack source, int amount)
	{
		int returned = input.insertFluid(copyFluid(source, amount), false);

		if (returned >= amount)
			return;

		int lost = amount - (returned > 0 ? Math.min(returned, amount) : 0);
		String fluidName = source.getFluid() == null ? "unknown" : source.getFluid().getName();
		PELogger.logError("Matter pipe network could not return " + lost + " mB of " + fluidName + " to " + input);
	}

	private static ItemStack copyItem(ItemStack source, int amount)
	{
		ItemStack copy = source.copy();
		copy.stackSize = amount;
		return copy;
	}

	private static FluidStack copyFluid(FluidStack source, int amount)
	{
		FluidStack copy = source.copy();
		copy.amount = amount;
		return copy;
	}
}
