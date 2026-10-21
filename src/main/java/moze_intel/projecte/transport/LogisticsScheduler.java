package moze_intel.projecte.transport;

import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.PELogger;

/**
 * 动态物流调度器（服务端单例，纯静态）。
 *
 * 目的：轻载（mspt < 20）时物流每 tick 都跑；20 ~ 35 是迟滞带，保持当前档位不动；
 * 重载区（35 ~ 50）降到每 5 tick 一次；TPS 已经掉到 20 以下（mspt >= 50）时降到每 20 tick 一次（每秒一次），
 * 把服务端的算力余量留给别的系统。
 *
 * 「无视任何加速手段」的关键：本类只认 {@link cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent}
 * 的 START / END 成对调用 —— 一个真实的服务端 tick 恰好让 {@link #tickCounter} 加一。
 * 时间加速类设备 / 物品（加速火把、时间洪流怀表等）改的是 world.getTotalWorldTime()，
 * 与这里的计数器毫无关系，所以物流永远不会因为加速而算得更快。
 * 因此所有物流错峰判定都必须读 {@link #getTickCounter()}，不许再读 world time。
 *
 * 调用约定（由 TransportTickHandler 保证，二者成对且同线程）：
 * START → {@link #onTickStart()}；END → {@link #onTickEnd()}，之后才是 MatterNetworkManager.tickAll()。
 * 于是 END 里算出的档位正好就是同一次 END 中物流实际使用的间隔（{@link #getInterval()} 可查）。
 */
public final class LogisticsScheduler
{
	/** 滚动环形缓冲：最近 LOGISTICS_SAMPLE_SIZE 个 tick 的耗时（ms）；长度固定，永不分配新数组 */
	private static final double[] SAMPLES = new double[Constants.LOGISTICS_SAMPLE_SIZE];
	/** 下一次写入的下标（环形） */
	private static int sampleIndex;
	/** 缓冲里已有的有效样本数（0..SAMPLES.length） */
	private static int sampleCount;
	/** 缓冲内样本总和：环形覆盖时先减旧值，平均值因此是 O(1) */
	private static double sampleTotal;
	/** 本 tick 的起始纳秒；<= 0 表示这次 END 没有配对的 START，跳过采样 */
	private static long tickStartNanos;
	/** 最近一次算出的平均 mspt（调试用） */
	private static double lastMspt;
	/** 迟滞状态机的当前档位（1 / 5 / 20）：物流实际使用的间隔，暖机期间保持默认值 */
	private static int lastInterval = Constants.LOGISTICS_INTERVAL_NORMAL;
	/** 全局单调 tick 计数器：只在 END 递增，与 world time 无关，加速手段改不动它 */
	private static long tickCounter;

	private LogisticsScheduler()
	{
	}

	/** ServerTickEvent.Phase.START：只记录起点，不做任何计算（END 之前不允许动计数器） */
	public static void onTickStart()
	{
		tickStartNanos = System.nanoTime();
	}

	/**
	 * ServerTickEvent.Phase.END：算出本 tick 耗时、进环形缓冲、推进计数器，并在间隔变化时打一条调试日志。
	 * 必须先于 MatterNetworkManager.tickAll() 调用，物流才能用上本 tick 的最新间隔。
	 */
	public static void onTickEnd()
	{
		long start = tickStartNanos;
		tickStartNanos = 0L;
		tickCounter++;

		// 没有配对到的 START（世界加载 / 异常的 tick 顺序）：本 tick 不采样，沿用上一轮的平均值
		if (start <= 0L)
			return;

		double mspt = (System.nanoTime() - start) / 1.0E6D;

		// 测量异常（NaN / 无穷 / 负数）直接丢弃：平均值自然回落到已有样本，样本不足时按默认间隔走
		if (Double.isNaN(mspt) || Double.isInfinite(mspt) || mspt < 0.0D)
			return;

		pushSample(mspt);
		updateInterval();
	}

	/** 全局单调 tick 计数器：所有物流（ITEM / FLUID / EMC）的错峰判定只读它 */
	public static long getTickCounter()
	{
		return tickCounter;
	}

	/**
	 * 当前间隔（tick）：1 / 5 / 20，由 {@link #decideInterval(double)} 在每次采样后更新。
	 * 带迟滞：20..35 这段不切换档位，所以这里读的是一个状态，而不是把 mspt 直接映射成间隔。
	 */
	public static int getInterval()
	{
		// 首 tick / 样本不足：按 NORMAL 起步，避免开局抖动
		if (sampleCount < Constants.LOGISTICS_MIN_SAMPLES)
			return Constants.LOGISTICS_INTERVAL_NORMAL;

		return lastInterval;
	}

	/**
	 * 迟滞状态机：按当前采样窗口的平均 mspt 决定下一档（判定顺序不可调换）。
	 * 1. mspt >= 50（TPS < 20）→ 20
	 * 2. 否则 mspt > 35 → 5（从 1 掉到 5；从 20 恢复到 5 都走这条）
	 * 3. 否则 mspt < 20 → 1（只有低于 20 才能回到每 tick）
	 * 4. 否则（20..35 迟滞带）→ 保持当前档位；特例：当前是 20 而 mspt 已 < 50 → 回到 5
	 */
	private static int decideInterval(double mspt)
	{
		if (mspt >= Constants.LOGISTICS_MSPT_HEAVY)
			return Constants.LOGISTICS_INTERVAL_HEAVY;

		if (mspt > Constants.LOGISTICS_MSPT_SLOW_DOWN)
			return Constants.LOGISTICS_INTERVAL_NORMAL;

		if (mspt < Constants.LOGISTICS_MSPT_RESUME_FAST)
			return Constants.LOGISTICS_INTERVAL_FAST;

		return lastInterval == Constants.LOGISTICS_INTERVAL_HEAVY ? Constants.LOGISTICS_INTERVAL_NORMAL : lastInterval;
	}

	/** 采样窗口内的平均 tick 耗时（ms）；还没有样本时返回 0 */
	public static double getMspt()
	{
		return sampleCount <= 0 ? 0.0D : sampleTotal / sampleCount;
	}

	/** 当前有效样本数（调试用）：少于 LOGISTICS_MIN_SAMPLES 时档位固定在默认值 */
	public static int getSampleCount()
	{
		return sampleCount;
	}

	/** 写一个样本进环形缓冲：满窗时先减去被覆盖的旧值，总和便始终等于窗口内样本之和 */
	private static void pushSample(double mspt)
	{
		if (sampleCount == SAMPLES.length)
			sampleTotal -= SAMPLES[sampleIndex];
		else
			sampleCount++;

		SAMPLES[sampleIndex] = mspt;
		sampleTotal += mspt;
		sampleIndex = (sampleIndex + 1) % SAMPLES.length;
	}

	/** 每次采样后推进迟滞状态机；只在档位真的变化时打一条调试日志，绝不每 tick 刷屏 */
	private static void updateInterval()
	{
		lastMspt = getMspt();

		// 暖机阶段不启动状态机：样本不足时档位保持默认，避免开局抖动
		if (sampleCount < Constants.LOGISTICS_MIN_SAMPLES)
			return;

		// 兜底：账算不出来的异常值不动档位（pushSample 已经滤掉 NaN / Inf / 负数，这里纯粹防呆）
		if (Double.isNaN(lastMspt) || Double.isInfinite(lastMspt) || lastMspt < 0.0D)
			return;

		int interval = decideInterval(lastMspt);

		if (interval == lastInterval)
			return;

		lastInterval = interval;
		PELogger.logDebug("Logistics interval -> every " + interval + " tick(s) (avg mspt " + String.format("%.2f", lastMspt) + ")");
	}
}
