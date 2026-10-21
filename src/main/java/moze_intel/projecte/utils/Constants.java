package moze_intel.projecte.utils;

import com.google.common.collect.ImmutableMap;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.util.ResourceLocation;
import moze_intel.projecte.PECore;

import java.text.DecimalFormat;

public final class Constants
{
	public static final DecimalFormat EMC_FORMATTER = new DecimalFormat("##.##");
	public static final float PLAYER_WALK_SPEED = 0.1F;

	public static final double[] MAX_KLEIN_EMC = new double[] {5e4, 2e5, 8e5, 3.2e6, 1.28e7, 5.12e7};
	public static final int[] RELAY_KLEIN_CHARGE_RATE = new int[] {16, 48, 160};
	public static final float[] COLLECTOR_LIGHT_VALS = new float[] {0.4375F, 0.6875F, 1.0F};

	public static final float[] EXPLOSIVE_LENS_RADIUS = new float[] {4.0F, 8.0F, 12.0F, 16.0F, 16.0F, 16.0F, 16.0F, 16.0F};
	public static final int[] EXPLOSIVE_LENS_COST = new int[] {384, 768, 1536, 2304, 2304, 2304, 2304, 2304};

	public static final double TILE_MAX_EMC = Double.MAX_VALUE;

	public static final int COLLECTOR_MK1_MAX = 10000;
	public static final int COLLECTOR_MK2_MAX = 30000;
	public static final int COLLECTOR_MK3_MAX = 60000;
	public static final int COLLECTOR_MK1_GEN = 4;
	public static final int COLLECTOR_MK2_GEN = 12;
	public static final int COLLECTOR_MK3_GEN = 40;

	public static final int RELAY_MK1_OUTPUT = 64;
	public static final int RELAY_MK2_OUTPUT = 192;
	public static final int RELAY_MK3_OUTPUT = 640;

	public static final int RELAY_MK1_MAX = 100000;
	public static final int RELAY_MK2_MAX = 1000000;
	public static final int RELAY_MK3_MAX = 10000000;

	public static final int COAL_BURN_TIME = 1600;
	public static final int ALCH_BURN_TIME = COAL_BURN_TIME * 4;
	public static final int MOBIUS_BURN_TIME = ALCH_BURN_TIME * 4;
	public static final int AETERNALIS_BUR_TIME = MOBIUS_BURN_TIME * 4;

	public static final int ALCH_CHEST_GUI = 0;
	public static final int ALCH_BAG_GUI = 1;
	@Deprecated
	public static final int TRANSMUTE_STONE_GUI = 2;
	public static final int CONDENSER_GUI = 3;
	public static final int RM_FURNACE_GUI = 4;
	public static final int DM_FURNACE_GUI = 5;
	public static final int COLLECTOR1_GUI = 6;
	public static final int COLLECTOR2_GUI = 7;
	public static final int COLLECTOR3_GUI = 8;
	public static final int RELAY1_GUI = 9;
	public static final int RELAY2_GUI = 10;
	public static final int RELAY3_GUI = 11;
	public static final int MERCURIAL_GUI = 12;
	public static final int PHILOS_STONE_GUI = 13;
	public static final int TRANSMUTATION_GUI = 14;
	public static final int ETERNAL_DENSITY_GUI = 15;
	public static final int CONDENSER_MK2_GUI = 16;
	public static final int PEDESTAL_GUI = 17;
	public static final int TRANSMUTATION_PORTABLE_GUI = 18;
	public static final int ARCANE_TABLET_GUI = 19;
	public static final int MATTER_INTERFACE_ITEM_GUI = 20;
	public static final int MATTER_INTERFACE_FLUID_GUI = 21;

	public static final int MAX_CONDENSER_PROGRESS = 102;

	public static final int CHEST_RENDER_ID = RenderingRegistry.getNextAvailableRenderId();
	public static final int CONDENSER_RENDER_ID = RenderingRegistry.getNextAvailableRenderId();
	public static final int CONDENSER_MK2_RENDER_ID = RenderingRegistry.getNextAvailableRenderId();
	public static final int PEDESTAL_RENDER_ID = RenderingRegistry.getNextAvailableRenderId();
	public static final ResourceLocation PEDESTAL_MODELTEX_LOCATION = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/models/pedestaltexmap.png");

	public static final int MAX_VEIN_SIZE = 256;

	public static final int ENCH_EMC_BONUS = 5000;

	// 物质物流：单根管道每秒可搬运的数量，只服务物品（件/s）与流体（mB/s）
	// tier 0 = 暗物质 2^15，tier 1 = 红物质 2^30，tier 2 = Gem 不限速
	// tier 2 的 2^30 只是占位值：unlimited 网络根本不用每秒预算；EMC 也不读这张表（EMC 语义上永不限速）
	public static final int[] PIPE_THROUGHPUT_PER_SECOND = new int[] {1 << 15, 1 << 30, 1 << 30};
	// 不限速档的 tier：网里只要有任意一根这种管道，整张网一次操作搬空来源
	public static final int PIPE_UNLIMITED_TIER = 2;
	// 一张管道网每秒可搬运的数量上限；同时充当 int 安全的数量天花板（2^30 < Integer.MAX_VALUE）
	public static final int PIPE_NETWORK_THROUGHPUT_CAP = 1 << 30;
	// 单份操作量的 int 安全上限：每秒速率 × interval / 20 若超过它，就拆成多份执行
	public static final int PIPE_MAX_OPERATION_AMOUNT = Integer.MAX_VALUE - 1;
	// unlimited 档单次操作最多的抽取循环次数：一次抽取只从"一个槽 / 一个罐"取货，
	// 4096 次足以搬空上千槽的巨型容器；异常端点（抽了不给、给了不记）也不会把服务端 tick 拖死
	public static final int PIPE_UNLIMITED_EXTRACT_ROUNDS = 4096;
	// unlimited 档单次操作最多的投递循环次数：与抽取分开计数，
	// 防止"能抽到但投不出去"的端点组合在单次操作里原地打转（抽了又退回去）
	public static final int PIPE_UNLIMITED_DELIVER_ROUNDS = 4096;
	// EMC 无上限请求量：向 IEmcProvider 要 EMC 时给的数量，等于"理论无限"。
	// 不用 Double.MAX_VALUE：提供方常做 min / 减法 / 比例运算，1.0E15 已经远超任何真实储量，
	// 又不会在乘法或转 int 时溢出成 Infinity / 负数。
	public static final double PIPE_EMC_UNLIMITED_REQUEST = 1.0E15D;
	// 网络 EMC 缓冲软上限：EMC 不再受"单次操作量 × 倍数"限制，只保留一个"到顶就停手"的软上限。
	// 理由：EMC 语义是不限速，但缓冲是真金白银的已拥有 EMC，没有上限时一张只剩收集器的网
	// 会无限往缓冲里灌数；1.0E15 与无上限请求量同量级 —— 正常吞吐永远碰不到，异常时也只是停止继续抽取，
	// 已经收下的 EMC 不丢（送不完就留在缓冲里）。
	public static final double PIPE_EMC_BUFFER_SOFT_CAP = 1.0E15D;
	// EMC 设备缓存的重扫周期（tick）：成员邻居的 EMC 来源 / 接受方只在这个周期或缓存失效时重扫，
	// 操作里不查世界。20 tick = 1 秒：玩家把凝聚器 / 物质熔炉贴到已有管道旁边之后，网络在 1 秒内就能发现它。
	// 曾用 200 tick（10 秒），那是「熔炉贴上去半天拿不到 EMC」的主因之一 —— 管道方块没有 onNeighborBlockChange，
	// 邻居变化没有任何事件可以接，只能靠这个周期重扫兜住。代价是每周期 6 次 getTileEntity / 成员管道，
	// 1 秒一轮对上千根管道的网也只是每 tick 数十次区块内查表，可以忽略。
	public static final int PIPE_EMC_DEVICE_REFRESH_TICKS = 20;
	// EMC 网络可接入的设备频道数，按成员里的最高档位取值：
	// 暗物质 16 / 红物质 256 / Gem 无上限（Integer.MAX_VALUE 表示不做截断）
	public static final int[] PIPE_EMC_CHANNELS = new int[] {16, 256, Integer.MAX_VALUE};
	// 单次投递最多遍历的端点数量，超出则分 tick 轮转
	public static final int PIPE_ENDPOINTS_PER_OPERATION = 64;
	// 继电器三档 EMC 获取乘数（加算叠加）：tier 0/1/2 = MK1/MK2/MK3，单个继电器的加成
	// 网络乘数 = 1 + Σ(网内相邻继电器各自的加成)，两枚 MK2 = 1 + 0.25 + 0.25 = 1.50（不是 1.5625 的连乘）
	// 只作用于「从收集器收集到的 EMC」：乘数会凭空产生 EMC，这是设计（见 docs/logistics-matter-system.md）
	public static final double[] PIPE_EMC_RELAY_BONUS = new double[] {0.10D, 0.25D, 0.40D};

	// 动态物流调度（LogisticsScheduler）：按服务端 tick 耗时决定物流执行间隔，无视一切时间加速手段
	// 带迟滞的状态机（当前档位是 1 / 5 / 20 之一），每次采样算完 mspt 后按顺序判定：
	//   mspt >= 50        → 20（每秒一次，TPS < 20）
	//   mspt >  35        → 5
	//   mspt <  20        → 1（**只有低于 20 才能回到每 tick**）
	//   20 <= mspt <= 35  → 保持当前档位（迟滞带，防止在阈值附近来回抖动）；特例：当前是 20 则回到 5
	public static final double LOGISTICS_MSPT_RESUME_FAST = 20.0D;
	public static final double LOGISTICS_MSPT_SLOW_DOWN = 35.0D;
	public static final double LOGISTICS_MSPT_HEAVY = 50.0D;
	public static final int LOGISTICS_INTERVAL_FAST = 1;
	public static final int LOGISTICS_INTERVAL_NORMAL = 5;
	public static final int LOGISTICS_INTERVAL_HEAVY = 20;
	// mspt 采样窗口（tick）：最近这么多个 tick 的耗时平均值决定间隔
	public static final int LOGISTICS_SAMPLE_SIZE = 20;
	// 起步门槛：有效样本少于这个数时按 LOGISTICS_INTERVAL_NORMAL 执行，避免开局抖动
	public static final int LOGISTICS_MIN_SAMPLES = 5;

	public static final ImmutableMap<String, String> SPACE_STRIP_NAME_MAP;

	static {
		ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
		builder.put("Alchemical Chest", "alchemical_chest");
		builder.put("Interdiction Torch", "interdiction_torch");
		builder.put("Transmutation Stone", "transmutation_table");
		builder.put("Condenser", "condenser_mk1");
		builder.put("Condenser MK2", "condenser_mk2");
		builder.put("RM Furnace", "rm_furnace");
		builder.put("RM Furnace Lit", "rm_furnace_lit");
		builder.put("DM Furnace", "dm_furnace");
		builder.put("DM Furnace Lit", "dm_furnace_lit");
		builder.put("DM Pedestal", "dm_pedestal");
		builder.put("Matter Block", "matter_block");
		builder.put("Fuel Block", "fuel_block");
		builder.put("Collector MK1", "collector_mk1");
		builder.put("Collector MK2", "collector_mk2");
		builder.put("Collector MK3", "collector_mk3");
		builder.put("Relay MK1", "relay_mk1");
		builder.put("Realy MK2", "relay_mk2");
		builder.put("Relay MK3", "relay_mk3");
		builder.put("Nova Catalyst", "nova_catalyst");
		builder.put("Nova Cataclysm", "nova_cataclysm");
		SPACE_STRIP_NAME_MAP = builder.build();
	}
}
