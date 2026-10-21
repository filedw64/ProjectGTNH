package moze_intel.projecte.transport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.api.tile.IEmcProvider;
import moze_intel.projecte.api.tile.IEmcStorage;
import moze_intel.projecte.gameObjs.tiles.CollectorMK1Tile;
import moze_intel.projecte.gameObjs.tiles.RelayMK1Tile;
import moze_intel.projecte.gameObjs.tiles.RelayMK2Tile;
import moze_intel.projecte.gameObjs.tiles.RelayMK3Tile;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterPipe;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * EMC 管道（TransportType.EMC）的网络逻辑，等价于一台分布式反物质继电器。
 * 管道本身不做端点：网络直接把成员管道的相邻方块当作 EMC 来源 / 接受者，
 * 于是远处的收集器、继电器与接受方（凝聚器、熔炉、机器）可以用管道接起来。
 * 抽到的 EMC 先进网络缓冲（按颜色由 MatterNetworkManager 的 WorldData 持有），再推给接受者。
 *
 * 谁是可以抽的来源、谁只是缓存：
 * A1. **网络只从收集器（CollectorMK1Tile 及其子类）抽 EMC，继电器一概不动**。
 *     继电器是缓存 / 分发端：它的 EMC 本来就是相邻收集器直接灌进去的（CollectorMK1Tile#sendToAllAcceptors
 *     与 #sendRelayBonus），再由继电器自己 active-push 给邻居 —— 网络再抽它，只会让继电器界面在 0 与满值之间
 *     反复跳动（每轮抽干 → 收集器下一 tick 又灌回来）。代价：一张只有继电器、没有收集器的网不再有 EMC 输入，
 *     这可以接受 —— 继电器从来不是 EMC 的源头。
 * A2. 获取乘数：贴在成员管道旁边的继电器让本网的**收集器产出**变多 —— MK1 +10% / MK2 +25% / MK3 +40%，
 *     多个继电器**加算**叠加（两枚 MK2 → ×1.50，不是 ×1.5625），只算进设备缓存里的继电器，同一台只算一次。
 *     **乘数只乘「从收集器抽出来的量」**。这条规则与 A1 是同一件事的两面：既然只有收集器是来源，
 *     也就不存在「继电器互相抽送 → 指数增殖」的自激回路（收集器只实现 IEmcProvider，永远不会被推送回填）。
 *     **请注意：继电器乘数会凭空产生 EMC，这是设计**（docs/logistics-matter-system.md 有说明）。
 * A3. 发配顺序：推送先走非继电器接受方（凝聚器 / 熔炉 / 机器等使用端），**最后才轮到继电器**，
 *     否则继电器会把缓冲全吸走、使用端永远拿不到；使用端内部再按「缓冲容量小的先喂」排序（见 {@link #FEED_ORDER}）。
 *
 * 组网语义（EMC 语义上永不限速，除 A2 的继电乘数外不做倍增）：
 * 1. 没有每秒预算、也不拆份：一次操作就是把本网搬得动的搬干净，速率常量只服务物品 / 流体。
 * 2. 一次操作 = 先收集（只抽收集器，请求量给到 {@link Constants#PIPE_EMC_UNLIMITED_REQUEST}）
 *    再推送（所有接受方，送多少按 acceptEMC 的返回值扣减）。
 * 3. 整数量化：缓冲里只放整数 EMC —— 收集的定额 floor、推送给出的量 floor；不足 1 EMC 的余量要么留在来源里，
 *    要么留在缓冲里，绝不会变成接受方界面上 43.9500000012585 这种浮点尾巴（见 {@link #collect} / {@link #pushPass}）。
 * 4. 本网没有任何接受者就不收集，避免把收集器的 EMC 吸进管道却送不出去。
 * 5. 缓冲仍按颜色挂在 WorldData 上（跨网络重建存活）；软上限只用来「停手」，不会丢 EMC。
 */
public final class EmcMatterTransport
{
	/** 超频道截断顺序：坐标字典序（x、y、z 依次比较），保证重建结果可复现 */
	private static final Comparator<Device> COORD_ORDER = (a, b) ->
	{
		int cmp = Integer.compare(a.tile.xCoord, b.tile.xCoord);

		if (cmp != 0)
			return cmp;

		cmp = Integer.compare(a.tile.yCoord, b.tile.yCoord);

		if (cmp != 0)
			return cmp;

		return Integer.compare(a.tile.zCoord, b.tile.zCoord);
	};

	/**
	 * 推送顺序：**缓冲容量小的使用端先喂，无界容器最后**（容量相同再按坐标，保证结果可复现）。
	 * 物质熔炉这类机器 maximumEMC 只有 64，而凝聚器的 maximumEMC 是 TileEmc 默认的 Double.MAX_VALUE：
	 * acceptEMC 会「能吃的全吃掉」，于是「先遍历到谁」直接决定「谁永远拿不到」。
	 * 不排序时顺序来自 HashSet<TileMatterPipe> 的迭代顺序（每次载入都可能不同、同一次载入内固定），
	 * 凝聚器只要排在熔炉前面，熔炉就一辈子分不到 1 EMC —— 这是「物质熔炉拿不到网络 EMC」的根因。
	 */
	private static final Comparator<Device> FEED_ORDER = (a, b) ->
	{
		int cmp = Double.compare(a.maxEmc, b.maxEmc);

		return cmp != 0 ? cmp : COORD_ORDER.compare(a, b);
	};

	private EmcMatterTransport()
	{
	}

	/** 一次操作：先收集进缓冲，再推送；本网没有接受者时既不收集也不动缓冲 */
	public static void transfer(MatterNetwork network, World world, double[] buffer)
	{
		int color = network.getColor();

		if (world == null || world.isRemote || buffer == null || color < 0 || color >= buffer.length)
			return;

		DeviceCache cache = network.getEmcDevices();

		if (cache == null)
			return;

		cache.ensureFresh(network, world);

		// 没有任何接受者：宁可不动，也不把 EMC 吸进管道却送不出去
		if (!cache.hasAcceptor)
			return;

		double buffered = sanitize(buffer[color]);

		// 达到软上限就不再收集：先把已拥有的送出去，剩下的真实 EMC 留在缓冲里
		if (buffered < Constants.PIPE_EMC_BUFFER_SOFT_CAP)
			buffered += collect(cache, buffered);

		if (buffered > 0.0D)
			buffered -= push(cache, buffered);

		buffer[color] = buffered > 0.0D ? buffered : 0.0D;
	}

	/**
	 * 收集：**只从收集器抽 EMC**（继电器是缓存 / 分发端，见类注释 A1），且请求量与入账定额都**向下取整**：
	 * 请求量先夹到「源自己上报的整数存量」，源就会把不足 1 EMC 的残渣留在自己身上、攒够 1 再给；
	 * 于是网络缓冲只会收到整数，EMC 也一个都没被抹掉。
	 * 收集器的产出再乘一次本网的继电器乘数（乘数在设备缓存重建时算好，这里只读一个 double），
	 * 乘出来的定额同样 floor：乘数会凭空产生 EMC，这是设计（见类注释 A2）。
	 *
	 * 软上限仍只管「停手」：乘数可能让 collected 略微越过软上限，但已经抽出来的真实 EMC 绝不丢弃。
	 */
	private static double collect(DeviceCache cache, double buffered)
	{
		double cap = Constants.PIPE_EMC_BUFFER_SOFT_CAP;
		double multiplier = cache.getEmcMultiplier();
		double collected = 0.0D;
		List<Device> devices = cache.devices;

		for (int i = 0; i < devices.size(); i++)
		{
			Device device = devices.get(i);

			if (!device.collector)
				continue;    // 只抽收集器：继电器 / 机器等一切非收集器提供方都不动

			TileEntity tile = device.tile;

			if (tile.isInvalid())
			{
				// 设备已经失效：本次先用剩下的，下一 tick 重扫（重扫是唯一做世界查询的地方）
				cache.invalidate();
				continue;
			}

			// 整数请求：先夹住软上限空间，再往下取整
			double request = Math.floor(cap - buffered - collected);

			if (request > Constants.PIPE_EMC_UNLIMITED_REQUEST)
				request = Constants.PIPE_EMC_UNLIMITED_REQUEST;

			if (request < 1.0D)
				break;    // 缓冲到软上限：本轮停止收集

			IEmcProvider provider = (IEmcProvider) tile;
			double stored = provider.getStoredEmc();

			// 只在「可信的有限存量」上做整数夹取；NaN / 无穷 / 负数一律不动请求量，交给 provideEMC 自己兜底
			if (stored >= 0.0D && stored < Double.POSITIVE_INFINITY)
				request = Math.min(request, Math.floor(stored));

			if (request < 1.0D)
				continue;    // 这台的存量不足 1 EMC：留在它自己身上，攒够了下一轮再抽

			double got = sanitize(provider.provideEMC(device.dir, request));

			if (got <= 0.0D)
				continue;

			// 收集器产出吃乘数；乘出来的差额是设计上凭空产生的 EMC（见类注释 A2），floor 掉小数部分
			double credit = Math.floor(sanitize(got * multiplier));

			if (credit <= 0.0D)
				credit = got;    // 乘数算出 0 / 负数 / Inf（理论到不了）：退回实际抽到的量，绝不吞掉已经抽出来的 EMC

			collected += credit;
		}

		return collected;
	}

	/**
	 * 推送：先给使用端（凝聚器 / 熔炉 / 机器等一切非继电器接受方），**最后才是继电器**。
	 * 分两趟遍历同一个缓存列表：不新建列表、也不排序，组内顺序就是 FEED_ORDER（小缓冲先喂）。
	 */
	private static double push(DeviceCache cache, double buffered)
	{
		double remaining = pushPass(cache, buffered, false);

		if (remaining > 0.0D)
			remaining = pushPass(cache, remaining, true);

		return buffered - remaining;
	}

	/**
	 * 一趟推送：只处理 relay 标记匹配的设备，按 acceptEMC 的返回值扣减。
	 * 交给接受方的量**向下取整**：不足 1 EMC 的余量留在缓冲里，下一轮凑够整数再送 ——
	 * 缓冲因此只含整数，接受方界面不会出现 43.9500000012585 这种浮点尾巴。
	 * 扣减用接受方自己上报的值（可能带小数），而不是我们请求的那个整数：否则会把「它拿走了却没记账」
	 * 的那部分凭空复制出来。
	 * @param relays true = 这一趟只喂继电器（放最后，避免它们把缓冲全吸走）
	 */
	private static double pushPass(DeviceCache cache, double buffered, boolean relays)
	{
		double remaining = buffered;
		List<Device> devices = cache.devices;

		for (int i = 0; i < devices.size(); i++)
		{
			Device device = devices.get(i);

			if (!device.acceptor || device.relay != relays)
				continue;

			// 整数出账：不足 1 EMC 就整趟停手，余量留在缓冲里
			double offer = Math.floor(remaining);

			if (offer < 1.0D)
				break;

			TileEntity tile = device.tile;

			if (tile.isInvalid())
			{
				cache.invalidate();
				continue;
			}

			double accepted = sanitize(((IEmcAcceptor) tile).acceptEMC(device.dir, offer));

			// 防御：接受方多报时不许凭空造 EMC
			if (accepted > offer)
				accepted = offer;

			if (accepted <= 0.0D)
				continue;

			remaining -= accepted;
		}

		return remaining;
	}

	/** 一台设备 = 一个贴着本网管道、实现 IEmcProvider 和 / 或 IEmcAcceptor 的方块 */
	public static final class Device
	{
		private final TileEntity tile;
		private final ForgeDirection dir;
		private final boolean acceptor;
		/** 继电器加成档位：0/1/2 = MK1/MK2/MK3，-1 = 不是继电器 */
		private final int relayTier;
		/** 继电器（既收也发）：乘数来源，且推送时必须排到使用端之后 */
		private final boolean relay;
		/**
		 * 收集器（CollectorMK1Tile 及其子类）：**网络里唯一的 EMC 来源**，也是唯一吃继电器乘数的设备。
		 * 收集器只实现 IEmcProvider、不是 IEmcAcceptor，永远不会被推送回填 —— 所以「抽它 → 再送它」
		 * 的自激回路在结构上就不存在。
		 */
		private final boolean collector;
		/** 最大储量（重建时读一次，运行中不变化）：只用来决定推送顺序，见 {@link #FEED_ORDER} */
		private final double maxEmc;

		private Device(TileEntity tile, ForgeDirection dir, boolean acceptor, int relayTier, boolean collector, double maxEmc)
		{
			this.tile = tile;
			this.dir = dir;
			this.acceptor = acceptor;
			this.relayTier = relayTier;
			this.relay = relayTier >= 0;
			this.collector = collector;
			this.maxEmc = maxEmc;
		}

		/**
		 * 继电器档位判定：**必须子类优先**（MK3 → MK2 → MK1）。
		 * RelayMK2Tile / RelayMK3Tile 都继承 RelayMK1Tile，顺序写反会让 MK3 只算成 MK1 的 +10%。
		 * @return 0/1/2 对应 MK1/MK2/MK3；-1 表示不是继电器
		 */
		private static int relayTier(TileEntity tile)
		{
			if (tile instanceof RelayMK3Tile)
				return 2;

			if (tile instanceof RelayMK2Tile)
				return 1;

			if (tile instanceof RelayMK1Tile)
				return 0;

			return -1;
		}

		/**
		 * 最大储量：只服务推送排序，不参与任何数值计算。
		 * NaN / 负数按 0（容量未知 → 排最前，收不收由 acceptEMC 自己决定）；无穷原样保留 → 排最后，避免它抢在机器前面。
		 */
		private static double maxEmc(TileEntity tile)
		{
			double max = ((IEmcStorage) tile).getMaximumEmc();

			return Double.isNaN(max) || max < 0.0D ? 0.0D : max;
		}
	}

	/**
	 * EMC 设备缓存：网络重建时扫一次成员邻居，之后每次操作只遍历这张表，操作里不做世界查询。
	 * 缓存对象由 MatterNetwork 持有，所以：
	 * · 网络成员变动 / WorldData 变脏 → 全量重建 → 新网络对象 → 新缓存，必然重扫一次；
	 * · 剩下三条刷新触发点由本类自己管：tile.isInvalid()、到期（PIPE_EMC_DEVICE_REFRESH_TICKS）、
	 *   world.getTileEntity 结果与缓存不一致（重扫本身就是那次校验）。
	 */
	public static final class DeviceCache
	{
		/** 复用同一个列表：重建时 clear + add，操作里不再分配 */
		private final List<Device> devices = new ArrayList<>();
		/** 重建去重用的临时容器：同一方块贴着多根管道也只算 1 个频道 */
		private final Set<TileEntity> seen = new HashSet<>();
		/** 有效设备里是否存在接受者；没有就不收集 */
		private boolean hasAcceptor;
		/**
		 * 本网的 EMC 获取乘数 = 1 + Σ(有效设备里各继电器各自的加成)，加算叠加。
		 * 重建时算好并缓存，收集热路径里只读这一个 double，不再遍历 / 不再逐台判断类型。
		 */
		private double emcMultiplier = 1.0D;
		private boolean valid;
		private long builtAt;
		/**
		 * 超频道日志：每个缓存对象（= 一次网络全量重建）最多一条。
		 * 每 PIPE_EMC_DEVICE_REFRESH_TICKS 的周期重扫沿用同一个缓存对象，所以不会按周期刷屏；
		 * 操作更不会写日志。
		 */
		private boolean overflowLogged;

		/** 主动标脏：下一 tick 重扫。网络 markDirty / 世界重建都会走到这里 */
		public void invalidate()
		{
			this.valid = false;
		}

		/** 本网的 EMC 获取乘数（1.0 = 没有继电器）；由 rebuild 算好并缓存 */
		public double getEmcMultiplier()
		{
			return emcMultiplier;
		}

		/**
		 * 未构建 / 被标脏 / 到期时重扫一次成员邻居；绝不每 tick 全维度重建网络。
		 * 计时用 LogisticsScheduler 的 tick 计数器（真实服务端 tick），不用 world time：
		 * 时间加速不该让设备重扫也跟着变快。
		 */
		private void ensureFresh(MatterNetwork network, World world)
		{
			long now = LogisticsScheduler.getTickCounter();

			if (valid && now >= builtAt && now - builtAt < Constants.PIPE_EMC_DEVICE_REFRESH_TICKS)
				return;

			rebuild(network, world, now);
		}

		/** 全量重扫成员邻居：本类唯一做 world.getTileEntity 的地方 */
		private void rebuild(MatterNetwork network, World world, long now)
		{
			devices.clear();
			seen.clear();
			this.valid = true;
			this.builtAt = now;

			for (TileMatterPipe member : network.getMembers())
			{
				for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
				{
					ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
					TileEntity tile = neighbor(world, member, dir);

					if (tile == null || tile.isInvalid())
						continue;

					// 网络只与两种角色打交道：EMC 来源（收集器）与接受方；继电器同时是接受方，照旧保留
					boolean collector = tile instanceof CollectorMK1Tile;
					boolean acceptor = tile instanceof IEmcAcceptor;

					if (!collector && !acceptor)
						continue;

					// 同一方块即使贴着多根管道（或同时是来源与接受方）也只算 1 个频道；
					// 因此同一个继电器也只贡献一次乘数
					if (!seen.add(tile))
						continue;

					// 继电器档位子类优先（MK3 → MK2 → MK1）；收集器只认 CollectorMK1Tile（MK2/MK3 都是它的子类）
					devices.add(new Device(tile, dir, acceptor, Device.relayTier(tile), collector, Device.maxEmc(tile)));
				}
			}

			int limit = network.getEmcChannels();
			int total = devices.size();

			// 频道上限为 0 只可能是非 EMC 网络（本缓存不该存在）：兜底成不截断，绝不静默丢掉整网设备
			if (limit <= 0)
				limit = Integer.MAX_VALUE;

			// 截断仍按坐标字典序：超频道时丢掉哪几台与修复前完全一致、且可复现
			if (total > limit)
			{
				devices.sort(COORD_ORDER);
				logOverflow(network.getColor(), limit, total);
				devices.subList(limit, total).clear();
			}

			// 截断之后再排成推送顺序：小缓冲使用端（物质熔炉等）先拿，无界容器（凝聚器）最后吃剩下的
			devices.sort(FEED_ORDER);

			// 乘数在重建时算好并缓存：只统计截断后仍有效的设备（被频道上限丢掉的继电器不算数）
			double bonus = 0.0D;

			for (int i = 0; i < devices.size(); i++)
			{
				Device device = devices.get(i);

				if (device.relay)
					bonus += Constants.PIPE_EMC_RELAY_BONUS[device.relayTier];
			}

			this.emcMultiplier = 1.0D + bonus;
			this.hasAcceptor = false;

			for (int i = 0; i < devices.size(); i++)
			{
				if (devices.get(i).acceptor)
				{
					this.hasAcceptor = true;
					break;
				}
			}
		}

		/** 超频道：每次重建最多一条日志，写清网络颜色、上限、被忽略数量与前 3 个被忽略坐标 */
		private void logOverflow(int color, int limit, int total)
		{
			if (overflowLogged)
				return;

			this.overflowLogged = true;

			int ignored = total - limit;
			int show = Math.min(3, ignored);
			StringBuilder message = new StringBuilder(128);

			message.append("EMC pipe network (color ").append(color).append(") exceeds channel limit ").append(limit)
				.append(": ").append(ignored).append(" device(s) ignored");

			for (int i = 0; i < show; i++)
			{
				Device device = devices.get(limit + i);
				message.append(i == 0 ? ", first ignored at " : ", ").append('[')
					.append(device.tile.xCoord).append(", ").append(device.tile.yCoord).append(", ").append(device.tile.zCoord).append(']');
			}

			PELogger.logError(message.toString());
		}

		/** 唯一的邻居查询入口：只在重建（ensureFresh）里被调用 */
		private static TileEntity neighbor(World world, TileMatterPipe member, ForgeDirection dir)
		{
			int x = member.xCoord + dir.offsetX;
			int y = member.yCoord + dir.offsetY;
			int z = member.zCoord + dir.offsetZ;

			if (!world.blockExists(x, y, z))
				return null;

			return world.getTileEntity(x, y, z);
		}
	}

	/** 负数 / NaN / 无穷一律按 0 处理，防止污染网络缓冲 */
	private static double sanitize(double value)
	{
		return value > 0.0D && !Double.isInfinite(value) ? value : 0.0D;
	}
}
