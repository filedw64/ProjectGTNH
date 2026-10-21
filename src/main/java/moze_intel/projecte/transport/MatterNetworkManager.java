package moze_intel.projecte.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import moze_intel.projecte.gameObjs.blocks.transport.BlockMatterPipe;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterPipe;
import moze_intel.projecte.utils.Constants;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 按维度管理所有管道网络。
 * 只在"脏"（管道增减 / 染色 / 端点变化）时做一次全量 BFS 重建，正常 tick 不重建也不全维度扫描。
 */
public class MatterNetworkManager
{
	public static final MatterNetworkManager INSTANCE = new MatterNetworkManager();

	private final Map<Integer, WorldData> worlds = new HashMap<>();

	private MatterNetworkManager()
	{
	}

	/** 一个维度的管道登记表与已分组的网络 */
	private static final class WorldData
	{
		private final World world;
		private final Set<TileMatterPipe> pipes = new HashSet<>();
		private final List<MatterNetwork> networks = new ArrayList<>();
		private final Map<Long, MatterNetwork> lookup = new HashMap<>();
		/**
		 * 按颜色索引（0..15）的 EMC 管道网络缓冲，是真实拥有的 EMC。
		 * 缓冲挂在 WorldData 上：网络重建只换 MatterNetwork 对象，缓冲原地保留；
		 * 维度卸载 / clearAll 丢弃 WorldData 时缓冲一并消失，不会跨存档泄漏。
		 * EMC 不限速，所以缓冲不再有"单次操作量 × 倍数"这种上限，
		 * 只有 Constants.PIPE_EMC_BUFFER_SOFT_CAP 让它停止继续收集（已收下的不丢）。
		 * 设备缓存挂在 MatterNetwork 上：这个 dirty 标志一被置上，重建出的新网络就带一份新缓存，
		 * 于是"WorldData 变脏 = 设备列表必然重扫一次"，不必再维护维度级的刷新状态。
		 */
		private final double[] emcBuffer = new double[16];
		private boolean dirty = true;

		private WorldData(World world)
		{
			this.world = world;
		}
	}

	public boolean isPipe(World world, int x, int y, int z)
	{
		return world != null && world.blockExists(x, y, z) && world.getBlock(x, y, z) instanceof BlockMatterPipe;
	}

	/** 管道所在的网络；坐标没有管道或尚未重建时返回 null */
	public MatterNetwork getNetworkAt(World world, int x, int y, int z)
	{
		if (world == null || world.isRemote)
			return null;

		WorldData data = worlds.get(world.provider.dimensionId);

		if (data == null)
			return null;

		if (data.dirty)
			rebuild(data);

		return data.lookup.get(coordKey(x, y, z));
	}

	/**
	 * 把 amount 注入该坐标所在的 EMC 网络缓冲（服务端主线程专用：AEGU 的无线注入走这里）。
	 * 只增加缓冲、绝不扣减 —— 缓冲的减少只有一条路径，即 EMC 网络推送时按 acceptEMC 的返回值扣减，
	 * 所以这里不存在"注入 + 推送"重复计数的可能。
	 * 世界未注册时不顺手创建 WorldData；找不到网络 / 不是 EMC 类型 / 参数非法 / 已到软上限都返回 false。
	 * 缓冲到软上限时只填到软上限（余量丢弃，调用方不关心），返回本次是否真的注入了 EMC。
	 * 注入量先向下取整：不足 1 EMC 直接返回 false（绝不「抹成 0 还报成功」），于是缓冲里只会留下整数 EMC。
	 */
	public boolean injectEmc(World world, int x, int y, int z, double amount)
	{
		// 参数门槛：null / 客户端 / 非正数 / NaN / ±Inf 全部拒绝，杜绝把缓冲写成 NaN
		if (world == null || world.isRemote || !(amount > 0.0D) || Double.isInfinite(amount))
			return false;

		// 世界未注册就直接放弃：注入不该顺手创建一张空表，那样每台 AEGU 换维度都会留下垃圾
		WorldData data = worlds.get(world.provider.dimensionId);

		if (data == null || data.world != world)
			return false;

		// 该坐标没有管道 / 尚未重建时 getNetworkAt 返回 null；它不会创建 WorldData
		MatterNetwork network = getNetworkAt(world, x, y, z);

		if (network == null || network.getType() != TransportType.EMC)
			return false;

		int color = network.getColor();

		if (color < 0 || color >= data.emcBuffer.length)
			return false;

		// 整数量化：不足 1 EMC 的注入直接失败（不谎报成功），留到下次凑够 1 再注入；
		// 与推送阶段口径一致，缓冲里只放整数，接受方界面不会出现 43.9500000012585 这种浮点尾巴
		double toInject = Math.floor(amount);

		if (toInject < 1.0D)
			return false;

		double buffered = sanitize(data.emcBuffer[color]);
		double room = Math.floor(Constants.PIPE_EMC_BUFFER_SOFT_CAP - buffered);

		if (room < 1.0D)
			return false; // 已到软上限：已收下的 EMC 不动，本次不再注入

		// 只填到软上限：accepted 必然有限且 >= 1，总和不会越界、也不会溢出成 Infinity
		double accepted = toInject < room ? toInject : room;
		data.emcBuffer[color] = buffered + accepted;
		return true;
	}

	public void registerPipe(TileMatterPipe pipe)
	{
		if (pipe == null || pipe.getWorldObj() == null || pipe.getWorldObj().isRemote)
			return;

		WorldData data = getData(pipe.getWorldObj());

		if (data.pipes.add(pipe))
			data.dirty = true;
	}

	public void unregisterPipe(TileMatterPipe pipe)
	{
		if (pipe == null || pipe.getWorldObj() == null || pipe.getWorldObj().isRemote)
			return;

		WorldData data = worlds.get(pipe.getWorldObj().provider.dimensionId);

		if (data == null || data.world != pipe.getWorldObj())
			return;

		if (data.pipes.remove(pipe))
			data.dirty = true;
	}

	public void requestRebuild(World world)
	{
		if (world == null || world.isRemote)
			return;

		getData(world).dirty = true;
	}

	public void tickAll()
	{
		if (worlds.isEmpty())
			return;

		for (WorldData data : worlds.values())
		{
			if (data.world.isRemote)
				continue;

			if (data.dirty)
				rebuild(data);

			for (int i = 0; i < data.networks.size(); i++)
				data.networks.get(i).tick();
		}
	}

	public void unloadWorld(World world)
	{
		if (world == null)
			return;

		int dim = world.provider.dimensionId;
		WorldData data = worlds.get(dim);

		if (data == null || data.world != world)
			return;

		data.networks.clear();
		data.lookup.clear();
		data.pipes.clear();
		worlds.remove(dim);
	}

	public void clearAll()
	{
		for (WorldData data : worlds.values())
		{
			data.networks.clear();
			data.lookup.clear();
			data.pipes.clear();
		}

		worlds.clear();
	}

	private WorldData getData(World world)
	{
		int dim = world.provider.dimensionId;
		WorldData data = worlds.get(dim);

		if (data == null || data.world != world)
		{
			data = new WorldData(world);
			worlds.put(dim, data);
		}

		return data;
	}

	/** 全量重建：同类型 + 同颜色 BFS 分组，然后为每张网收集端点 */
	private void rebuild(WorldData data)
	{
		World world = data.world;
		data.dirty = false;
		data.networks.clear();
		data.lookup.clear();

		Iterator<TileMatterPipe> it = data.pipes.iterator();

		while (it.hasNext())
		{
			TileMatterPipe pipe = it.next();

			if (pipe.isInvalid() || pipe.getWorldObj() != world)
				it.remove();
		}

		Set<TileMatterPipe> visited = new HashSet<>();
		ArrayDeque<TileMatterPipe> queue = new ArrayDeque<>();

		for (TileMatterPipe seed : data.pipes)
		{
			if (!visited.add(seed))
				continue;

			MatterNetwork network = new MatterNetwork(world, seed.getTransportType(), seed.getColor(), data.networks.size(), data.emcBuffer);
			queue.add(seed);

			while (!queue.isEmpty())
			{
				TileMatterPipe current = queue.poll();
				network.addMember(current);
				data.lookup.put(coordKey(current.xCoord, current.yCoord, current.zCoord), network);

				for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
				{
					ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
					TileMatterPipe neighbor = getPipe(world, current.xCoord + dir.offsetX, current.yCoord + dir.offsetY, current.zCoord + dir.offsetZ);

					if (neighbor != null && current.isSameChannel(neighbor) && visited.add(neighbor))
						queue.add(neighbor);
				}
			}

			// recalculateThroughput 顺带算出 EMC 频道上限；设备缓存是新建的空缓存，第一次操作时才重扫邻居
			network.recalculateThroughput();
			network.collectEndpoints();
			data.networks.add(network);
		}
	}

	private TileMatterPipe getPipe(World world, int x, int y, int z)
	{
		if (!world.blockExists(x, y, z))
			return null;

		TileEntity tile = world.getTileEntity(x, y, z);

		return tile instanceof TileMatterPipe ? (TileMatterPipe) tile : null;
	}

	/** 负数 / NaN / 无穷一律按 0 处理，防止污染网络缓冲（与 EmcMatterTransport 的清洗口径一致） */
	private static double sanitize(double value)
	{
		return value > 0.0D && !Double.isInfinite(value) ? value : 0.0D;
	}

	/** 与客户端约定无关的坐标打包：x 26 位 | z 26 位 | y 12 位 */
	private static long coordKey(int x, int y, int z)
	{
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}
}
