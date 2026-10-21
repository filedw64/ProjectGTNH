package moze_intel.projecte.transport;

import java.util.Locale;
import moze_intel.projecte.utils.Constants;

/**
 * 物质物流网络的传输类型。
 * ITEM / FLUID 走端点投递，EMC 走向管道邻居直接收发（分布式继电器）。
 */
public enum TransportType
{
	ITEM,
	FLUID,
	EMC;

	/** 该类型是否已有可用的网络实现与管道方块 */
	public boolean isImplemented()
	{
		return this == ITEM || this == FLUID || this == EMC;
	}

	public String getName()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * 单根管道每秒可搬运的数量（tier 0 = 暗物质 2^15，1 = 红物质 2^30，2 = Gem 不限速）。
	 * tier 2 返回的只是占位值，物品 / 流体走 unlimited 路径后不再用它换算预算。
	 * EMC 恒返回 0：EMC 不用每秒预算（连上就通、数值守恒），速率表对它没有意义。
	 */
	public int getPipeThroughputPerSecond(int tier)
	{
		if (this == EMC)
			return 0;

		int[] table = Constants.PIPE_THROUGHPUT_PER_SECOND;

		return table[tier < 0 ? 0 : Math.min(tier, table.length - 1)];
	}

	/**
	 * EMC 在语义上永远不限速：任何档位的 EMC 网络都是一次操作搬空能搬的部分。
	 * 物品 / 流体只有 tier 2（Gem）不限速：一次操作搬空来源，既不做每秒预算也不拆份。
	 */
	public boolean isUnlimited(int tier)
	{
		return this == EMC || tier >= Constants.PIPE_UNLIMITED_TIER;
	}

	/**
	 * EMC 网络可接入的设备频道数，按档位 16 / 256 / 无上限（见 Constants.PIPE_EMC_CHANNELS）。
	 * 一张网的上限取成员中的最高档位；非 EMC 类型没有频道概念，调用方不该用它。
	 */
	public int getEmcChannels(int tier)
	{
		int[] table = Constants.PIPE_EMC_CHANNELS;

		return table[tier < 0 ? 0 : Math.min(tier, table.length - 1)];
	}

	/** 一张网每秒可搬运的数量上限 */
	public int getMaxThroughputPerSecond()
	{
		return isImplemented() ? Constants.PIPE_NETWORK_THROUGHPUT_CAP : 0;
	}
}
