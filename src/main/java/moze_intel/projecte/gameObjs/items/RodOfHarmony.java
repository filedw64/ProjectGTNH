package moze_intel.projecte.gameObjs.items;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class RodOfHarmony extends ItemPE
{
	// 沿视线推进时的采样步长（格）
	private static final double RAY_STEP = 0.2D;
	// 遁地穿墙沿视线最多推进的格数（固定值，保持改造前行为，不进 config）
	private static final int WALL_SCAN_LIMIT = 64;
	// 视距（单位：区块）换算为格数的系数
	private static final int VIEW_DISTANCE_BLOCK_FACTOR = 16;
	// 判定“脚下有支撑”时向下搜索的最大深度
	private static final int SUPPORT_SEARCH_DEPTH = 8;
	// 就近安全落点搜索半径的硬上限，防止 config 填入过大数值导致长时间循环
	private static final int MAX_SAFE_RADIUS = 8;
	// 落点校验结果
	private static final int LANDING_SAFE = 0;
	private static final int LANDING_NO_ROOM = 1;
	private static final int LANDING_VOID = 2;

	public RodOfHarmony()
	{
		this.setUnlocalizedName("rod_of_harmony");
		this.setTextureName("projecte:rod_of_harmony");
		this.setMaxStackSize(1);
		this.setCreativeTab(ObjHandler.tab);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		player.swingItem();

		if (world.isRemote) return stack; // 传送逻辑仅在服务端处理，防止拉回和数据不同步

		// 获取玩家标准触及范围内的目标，命中方块说明玩家想遁地穿墙
		MovingObjectPosition localMop = this.getMovingObjectPositionFromPlayer(world, player, false);

		if (localMop != null && localMop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
		{
			return wallPenetrate(stack, world, player);
		}

		return flight(stack, world, player);
	}

	/**
	 * 遁地穿墙：沿视线推进，穿出墙壁后取安全落点，按穿过的实心方块数计费
	 */
	private ItemStack wallPenetrate(ItemStack stack, World world, EntityPlayer player)
	{
		Vec3 head = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
		Vec3 look = player.getLookVec();
		double limit = WALL_SCAN_LIMIT;

		boolean insideWall = false;
		int solidCount = 0;
		int lastX = Integer.MAX_VALUE, lastY = Integer.MAX_VALUE, lastZ = Integer.MAX_VALUE;

		for (double d = 0.0D; d <= limit; d += RAY_STEP)
		{
			double tx = head.xCoord + look.xCoord * d;
			double ty = head.yCoord + look.yCoord * d;
			double tz = head.zCoord + look.zCoord * d;

			int ix = MathHelper.floor_double(tx);
			int iy = MathHelper.floor_double(ty);
			int iz = MathHelper.floor_double(tz);

			// 遇到未加载区块（或 Y 越界）立即停止推进，避免 getBlock 触发区块加载
			if (!world.blockExists(ix, iy, iz)) break;

			boolean isSolid = world.getBlock(ix, iy, iz).getMaterial().blocksMovement();

			// 统计沿途穿过了多少个不同的固体方块
			if (ix != lastX || iy != lastY || iz != lastZ)
			{
				if (isSolid) solidCount++;
				lastX = ix; lastY = iy; lastZ = iz;
			}

			if (isSolid)
			{
				insideWall = true;
			}
			else if (insideWall)
			{
				// 脱离了墙壁：先做三重安全校验，失败则在安全半径内就近搜索
				ChunkCoordinates landing = findLanding(world, player, ix, iy, iz);

				if (landing == null)
				{
					reportUnsafeLanding(world, player, ix, iy, iz); // 就近也找不到，提示并取消，不扣费
					return stack;
				}

				double cost = solidCount * (double) ProjectEConfig.rodOfHarmonyCostPerWallBlock;
				chargeAndTeleport(player, cost, landing.posX + 0.5D, landing.posY, landing.posZ + 0.5D);
				return stack;
			}
		}

		player.addChatMessage(new ChatComponentTranslation("pe.harmony.toothick"));
		return stack;
	}

	/**
	 * 指哪飞哪：沿视线飞到最后一个已加载点，命中方块时贴面偏移，按飞行距离计费
	 */
	private ItemStack flight(ItemStack stack, World world, EntityPlayer player)
	{
		Vec3 head = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
		Vec3 look = player.getLookVec();

		// 先算出不会触碰未加载区块的有效距离，再交给 rayTraceBlocks，避免其内部触发区块加载
		double dist = getLoadedDistance(world, head, look, getTravelLimit());

		if (dist <= 0.0D) return stack;

		Vec3 target = Vec3.createVectorHelper(
			head.xCoord + look.xCoord * dist,
			head.yCoord + look.yCoord * dist,
			head.zCoord + look.zCoord * dist);

		MovingObjectPosition mopFar = world.rayTraceBlocks(head, target, false);

		double finalX;
		double finalY;
		double finalZ;

		// 命中方块 = 贴面落点；未命中 = 纯空中落点，两者安全语义不同
		boolean hitBlock = (mopFar != null && mopFar.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK);

		if (hitBlock)
		{
			// 视距内击中了方块，根据击中面偏移，防止卡进方块里
			ForgeDirection dir = ForgeDirection.getOrientation(mopFar.sideHit);
			finalX = mopFar.blockX + 0.5D + dir.offsetX;
			finalY = mopFar.blockY + dir.offsetY;
			finalZ = mopFar.blockZ + 0.5D + dir.offsetZ;

			if (mopFar.sideHit == 0) finalY -= 2.0D; // 如果打中方块底面，往下偏移防止头卡住
		}
		else
		{
			// 平飞，未击中任何东西，直接传送到已加载区块的边缘
			finalX = target.xCoord;
			finalY = target.yCoord;
			finalZ = target.zCoord;
		}

		int maxY = world.getHeight() - 1;

		// 边界检查：朝上传送，插值到世界高度上限
		if (finalY > (double) maxY)
		{
			double diffY = (double) maxY - head.yCoord;
			double ratio = diffY / (finalY - head.yCoord);
			finalX = head.xCoord + (finalX - head.xCoord) * ratio;
			finalZ = head.zCoord + (finalZ - head.zCoord) * ratio;
			finalY = (double) maxY;
		}
		// 边界检查：朝下传送越过 Y=0，直接拦截
		else if (finalY < 0.0D)
		{
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.voidcancel"));
			return stack;
		}

		int cellX = MathHelper.floor_double(finalX);
		int cellY = MathHelper.floor_double(finalY);
		int cellZ = MathHelper.floor_double(finalZ);

		// 计费距离始终取 head 到瞄准点（finalX/finalY/finalZ）的距离，与落点钳制无关
		double cost = head.distanceTo(Vec3.createVectorHelper(finalX, finalY, finalZ)) * (double) ProjectEConfig.rodOfHarmonyCostPerBlock;

		if (hitBlock)
		{
			// 贴面落点：落点三重校验，失败则在安全半径内就近搜索
			ChunkCoordinates landing = findLanding(world, player, cellX, cellY, cellZ);

			if (landing == null)
			{
				reportUnsafeLanding(world, player, cellX, cellY, cellZ); // 提示并取消，不扣费
				return stack;
			}

			chargeAndTeleport(player, cost, landing.posX + 0.5D, landing.posY, landing.posZ + 0.5D);
			return stack;
		}

		// 纯空中落点：只要求 1x2 站立空间在世界内且不被方块占据，不要求脚下有支撑，也不做就近搜索
		ChunkCoordinates air = checkAirLanding(world, player, cellX, cellY, cellZ);

		if (air == null)
		{
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.nosafe")); // 取消，不扣费
			return stack;
		}

		chargeAndTeleport(player, cost, air.posX + 0.5D, air.posY, air.posZ + 0.5D);
		return stack;
	}

	/**
	 * 本次传送允许的最大距离（格）：config 上限优先，否则沿用视距×16
	 */
	private double getTravelLimit()
	{
		if (ProjectEConfig.rodOfHarmonyMaxDistance > 0)
		{
			return (double) ProjectEConfig.rodOfHarmonyMaxDistance;
		}

		int viewDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		return viewDistance * (double) VIEW_DISTANCE_BLOCK_FACTOR;
	}

	/**
	 * 沿视线推进到最后一个已加载区块内的采样点，返回该点的距离作为有效射程
	 */
	private double getLoadedDistance(World world, Vec3 head, Vec3 look, double limit)
	{
		double valid = 0.0D;

		for (double d = 0.0D; d <= limit; d += RAY_STEP)
		{
			int ix = MathHelper.floor_double(head.xCoord + look.xCoord * d);
			int iy = MathHelper.floor_double(head.yCoord + look.yCoord * d);
			int iz = MathHelper.floor_double(head.zCoord + look.zCoord * d);

			if (!world.blockExists(ix, iy, iz)) break; // 未加载区块：停止推进，截断到这里

			valid = d;
		}

		return valid;
	}

	/**
	 * 先在目标格本身找安全落点，失败则在 rodOfHarmonySafeRadius 半径内同一平面上由近到远搜索
	 */
	private ChunkCoordinates findLanding(World world, EntityPlayer player, int x, int y, int z)
	{
		if (checkLanding(world, player, x, y, z) == LANDING_SAFE)
		{
			return new ChunkCoordinates(x, y, z);
		}

		int radius = Math.min(Math.max(ProjectEConfig.rodOfHarmonySafeRadius, 0), MAX_SAFE_RADIUS);

		// size 为曼哈顿距离，从 1 递增即可保证“先近后远”
		for (int size = 1; size <= 2 * radius; size++)
		{
			for (int dx = -radius; dx <= radius; dx++)
			{
				for (int dz = -radius; dz <= radius; dz++)
				{
					if (Math.abs(dx) + Math.abs(dz) != size) continue; // 只处理当前这一圈

					int nx = x + dx;
					int nz = z + dz;

					if (checkLanding(world, player, nx, y, nz) == LANDING_SAFE)
					{
						return new ChunkCoordinates(nx, y, nz);
					}
				}
			}
		}

		return null;
	}

	/**
	 * 落点三重校验：目标格与上方一格可通行、脚下有实体支撑、玩家碰撞箱不卡进方块或实体
	 */
	private int checkLanding(World world, EntityPlayer player, int x, int y, int z)
	{
		if (y < 0 || y > world.getHeight() - 1) return LANDING_NO_ROOM;

		if (!isPassable(world, x, y, z)) return LANDING_NO_ROOM; // 目标格必须可通行
		if (!isPassable(world, x, y + 1, z)) return LANDING_NO_ROOM; // 上一格必须可通行，保证 1x2 站立空间

		if (!hasSupportBelow(world, x, y, z)) return LANDING_VOID; // 脚下必须是实体方块，避免悬空与虚空

		AxisAlignedBB box = getPlayerBox(player, x, y, z);

		if (hasBlockCollision(world, box)) return LANDING_NO_ROOM;
		if (!world.checkNoEntityCollision(box)) return LANDING_NO_ROOM;

		return LANDING_SAFE;
	}

	/**
	 * 纯空中落点校验：只要求 1x2 站立空间在世界内、区块已加载且未被方块占据，不要求脚下有支撑
	 */
	private ChunkCoordinates checkAirLanding(World world, EntityPlayer player, int x, int y, int z)
	{
		// 钳制到 [0, 世界高度 - 2]，保证 (x, y) 与 (x, y + 1) 两格都落在 0 .. getHeight() - 1 内
		int cellY = Math.min(Math.max(y, 0), world.getHeight() - 2);

		if (!isPassable(world, x, cellY, z)) return null; // 目标格必须可通行且区块已加载
		if (!isPassable(world, x, cellY + 1, z)) return null; // 上一格必须可通行，保证 1x2 站立空间

		AxisAlignedBB box = getPlayerBox(player, x, cellY, z);

		// 世界高度以上按空气处理，避免 blockExists 恒为 false 被误判成有碰撞
		if (hasBlockCollisionInWorld(world, box)) return null;

		return new ChunkCoordinates(x, cellY, z);
	}

	/**
	 * 就近搜索也失败时按原因提示：脚下无支撑视为虚空危险，其余情况提示无安全落点
	 */
	private void reportUnsafeLanding(World world, EntityPlayer player, int x, int y, int z)
	{
		String key = checkLanding(world, player, x, y, z) == LANDING_VOID ? "pe.harmony.voiddanger" : "pe.harmony.nosafe";
		player.addChatMessage(new ChatComponentTranslation(key));
	}

	/**
	 * 判断某格是否为可通过空间；未加载区块一律视为不可通过，避免 getBlock 触发区块加载
	 */
	private boolean isPassable(World world, int x, int y, int z)
	{
		if (!world.blockExists(x, y, z)) return false;

		return !world.getBlock(x, y, z).getMaterial().blocksMovement();
	}

	/**
	 * 从目标格向下最多 SUPPORT_SEARCH_DEPTH 格寻找实体方块支撑，超过仍无支撑即视为虚空
	 */
	private boolean hasSupportBelow(World world, int x, int y, int z)
	{
		int minY = Math.max(y - SUPPORT_SEARCH_DEPTH, 0);

		for (int i = y - 1; i >= minY; i--)
		{
			// 区块未加载则无法确认支撑，按危险处理，转去就近搜索
			if (!world.blockExists(x, i, z)) return false;

			if (world.getBlock(x, i, z).getMaterial().blocksMovement()) return true;
		}

		return false;
	}

	/**
	 * 构造玩家站在某格中心时的碰撞箱，尺寸取自玩家自身 boundingBox
	 */
	private AxisAlignedBB getPlayerBox(EntityPlayer player, int x, int y, int z)
	{
		double halfWidth = (player.boundingBox.maxX - player.boundingBox.minX) / 2.0D;
		double height = player.boundingBox.maxY - player.boundingBox.minY;
		double centerX = x + 0.5D;
		double centerZ = z + 0.5D;

		return AxisAlignedBB.getBoundingBox(
			centerX - halfWidth, (double) y, centerZ - halfWidth,
			centerX + halfWidth, (double) y + height, centerZ + halfWidth);
	}

	/**
	 * 检查碰撞箱覆盖到的每一格是否都可通行（避免把玩家塞进方块里）
	 */
	private boolean hasBlockCollision(World world, AxisAlignedBB box)
	{
		int minX = MathHelper.floor_double(box.minX);
		int maxX = MathHelper.floor_double(box.maxX);
		int minY = MathHelper.floor_double(box.minY);
		int maxY = MathHelper.floor_double(box.maxY);
		int minZ = MathHelper.floor_double(box.minZ);
		int maxZ = MathHelper.floor_double(box.maxZ);

		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				for (int z = minZ; z <= maxZ; z++)
				{
					if (!isPassable(world, x, y, z)) return true;
				}
			}
		}

		return false;
	}

	/**
	 * hasBlockCollision 的变体：把世界高度以上的格子按空气处理（该高度 blockExists 恒为 false，
	 * isPassable 会把它误判成有碰撞）
	 */
	private boolean hasBlockCollisionInWorld(World world, AxisAlignedBB box)
	{
		int topY = world.getHeight() - 1;
		int minX = MathHelper.floor_double(box.minX);
		int maxX = MathHelper.floor_double(box.maxX);
		int minY = MathHelper.floor_double(box.minY);
		int maxY = Math.min(MathHelper.floor_double(box.maxY), topY);
		int minZ = MathHelper.floor_double(box.minZ);
		int maxZ = MathHelper.floor_double(box.maxZ);

		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				for (int z = minZ; z <= maxZ; z++)
				{
					if (!isPassable(world, x, y, z)) return true;
				}
			}
		}

		return false;
	}

	/**
	 * 统一处理计费与传送：扣费失败（余额不足）则什么都不做
	 */
	private void chargeAndTeleport(EntityPlayer player, double cost, double x, double y, double z)
	{
		if (!consumePlayerNetworkEMC(player, cost)) return;

		teleportPlayer(player, x, y, z);
	}

	private void teleportPlayer(EntityPlayer player, double x, double y, double z)
	{
		if (player instanceof EntityPlayerMP)
		{
			EntityPlayerMP mp = (EntityPlayerMP) player;
			mp.setPositionAndUpdate(x, y, z);
			mp.fallDistance = 0.0F;
			mp.worldObj.playSoundAtEntity(mp, "mob.endermen.portal", 1.0F, 1.0F); // 传送音效
		}
	}

	private boolean consumePlayerNetworkEMC(EntityPlayer player, double amount)
	{
		if (player.capabilities.isCreativeMode) return true;

		double currentEMC = Transmutation.getEmc(player);

		if (currentEMC < amount)
		{
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.noemc", String.format("%.2f", amount)));
			return false;
		}

		Transmutation.setEmc(player, currentEMC - amount);

		// 扣费后立刻发包同步 EMC，否则客户端数值要等下一次同步才更新（sync 内部强转 EntityPlayerMP）
		if (player instanceof EntityPlayerMP && !player.worldObj.isRemote)
		{
			Transmutation.sync(player);
		}

		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean par4)
	{
		list.add(StatCollector.translateToLocal("pe.harmony.tooltip1"));
		list.add(StatCollector.translateToLocal("pe.harmony.tooltip2"));
		list.add(StatCollector.translateToLocal("pe.harmony.tooltip3"));
	}
}
