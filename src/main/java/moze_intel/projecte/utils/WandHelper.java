package moze_intel.projecte.utils;

import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Direction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 建筑之杖的几何与计费引擎：服务端放置与客户端预览共用同一套形状与可负担性计算。
 */
public class WandHelper {

	// 兼容别名：BuildersWand 的 NBT "Mode" 合法值个数，跟随枚举长度，新增模式不必再改
	public static final byte MODE_COUNT = (byte) WandMode.values().length;

	public static final int PAY_FAIL = 0;
	public static final int PAY_EMC = 1;
	public static final int PAY_ITEM = 2;

	// 柱形（竖直/水平）固定上限，防止一次右键挖穿世界
	private static final int PILLAR_MAX_LENGTH = 32;

	private static final int[][] PLANES_XZ = {{1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {1,0,1}, {1,0,-1}, {-1,0,1}, {-1,0,-1}};
	private static final int[][] PLANES_XY = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {1,1,0}, {1,-1,0}, {-1,1,0}, {-1,-1,0}};
	private static final int[][] PLANES_YZ = {{0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}, {0,1,1}, {0,1,-1}, {0,-1,1}, {0,-1,-1}};

	/**
	 * 一次计算的产物：已经过单格合法性校验的坐标，以及循环外算好的计费参数。
	 */
	public static class PlacementPlan {
		public final List<ChunkCoordinates> positions;
		public final boolean hasKnowledge;
		public final double emcPerBlock;

		public PlacementPlan(List<ChunkCoordinates> positions, boolean hasKnowledge, double emcPerBlock) {
			this.positions = positions;
			this.hasKnowledge = hasKnowledge;
			this.emcPerBlock = emcPerBlock;
		}

		public int size() {
			return this.positions.size();
		}
	}

	/**
	 * 生成待放置坐标。player 为 null 表示客户端预览，此时不做知识判定。
	 */
	public static PlacementPlan plan(World world, int x, int y, int z, int side, byte mode, int maxBlocks, EntityPlayer player) {
		return plan(world, x, y, z, side, mode, maxBlocks, player, null);
	}

	/**
	 * costStack 只决定 hasKnowledge 与 emcPerBlock，为 null 时回退到被点击方块。
	 * 几何遍历、支撑面校验、planned 集合与 maxBlocks 预算一律按被点击方块，与聚焦无关。
	 */
	public static PlacementPlan plan(World world, int x, int y, int z, int side, byte mode, int maxBlocks, EntityPlayer player, ItemStack costStack) {
		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ItemStack targetStack = targetBlock == null ? null : new ItemStack(targetBlock, 1, targetMeta);

		if (targetStack == null || targetStack.getItem() == null) {
			return new PlacementPlan(new ArrayList<>(), false, 0.0);
		}

		// 计费栈：有聚焦按聚焦，无聚焦按被点击方块；循环外算一次，逐格复用
		ItemStack payStack = costStack == null || costStack.getItem() == null ? targetStack : costStack;
		boolean hasKnowledge = player != null && Transmutation.hasKnowledgeForStack(payStack, player);
		double emcPerBlock = EMCHelper.getEmcValue(payStack);

		WandMode wandMode = WandMode.fromId(mode);
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		ForgeDirection opp = dir.getOpposite();
		List<ChunkCoordinates> positions;

		switch (wandMode) {
			case MODE_PLANE:
				positions = plane(world, x, y, z, side, maxBlocks, targetBlock, targetMeta, dir, opp);
				break;
			case MODE_PILLAR:
				positions = pillar(world, x, y, z, maxBlocks, targetBlock, targetMeta, dir, opp);
				break;
			case MODE_H_PILLAR:
				positions = hPillar(world, x, y, z, maxBlocks, targetBlock, targetMeta, dir, opp, player);
				break;
			case MODE_CUBE:
				positions = cube(world, x, y, z, targetBlock, targetMeta, dir, opp);
				break;
			default:
				positions = square(world, x, y, z, side, wandMode.getRadius(), targetBlock, targetMeta, dir, opp);
				break;
		}

		return new PlacementPlan(positions, hasKnowledge, emcPerBlock);
	}

	/**
	 * 纯函数：给定 EMC 余额与背包数量，返回本次最多能负担的格数（EMC 优先）。
	 */
	public static int affordableCount(PlacementPlan plan, double emcBalance, int inventoryCount) {
		if (plan == null) return 0;

		int size = plan.size();
		if (size <= 0) return 0;

		int fromInv = Math.max(inventoryCount, 0);

		if (!plan.hasKnowledge || plan.emcPerBlock <= 0) {
			return Math.min(size, fromInv);
		}

		double afford = Math.floor(emcBalance / plan.emcPerBlock);
		int fromEmc = afford >= size ? size : (int) (afford > 0 ? afford : 0);

		return fromEmc + Math.min(size - fromEmc, fromInv);
	}

	/**
	 * 背包内与目标方块同种物品的总数量。
	 */
	public static int countInventory(EntityPlayer player, ItemStack targetStack) {
		if (player == null || targetStack == null || targetStack.getItem() == null) return 0;

		int total = 0;
		int capacity = player.inventory.getSizeInventory();

		for (int i = 0; i < capacity; i++) {
			ItemStack stack = player.inventory.getStackInSlot(i);
			if (stack == null || !ItemHelper.basicAreStacksEqual(stack, targetStack)) continue;

			total += stack.stackSize;
		}

		return total;
	}

	// 3x3 / 5x5 / 7x7：点击面外侧那一格为中心，在垂直于点击面的平面内展开完整正方形
	private static List<ChunkCoordinates> square(World world, int x, int y, int z, int side, int radius, Block targetBlock, int targetMeta, ForgeDirection dir, ForgeDirection opp) {
		int centerX = x + dir.offsetX;
		int centerY = y + dir.offsetY;
		int centerZ = z + dir.offsetZ;

		int[] axisA = new int[3];
		int[] axisB = new int[3];
		fillPlaneAxes(side, axisA, axisB);

		List<ChunkCoordinates> toPlace = new ArrayList<>();

		for (int i = -radius; i <= radius; i++) {
			for (int j = -radius; j <= radius; j++) {
				int posX = centerX + axisA[0] * i + axisB[0] * j;
				int posY = centerY + axisA[1] * i + axisB[1] * j;
				int posZ = centerZ + axisA[2] * i + axisB[2] * j;

				if (!canPlaceAt(world, posX, posY, posZ, targetBlock, targetMeta, opp, null)) continue;

				toPlace.add(new ChunkCoordinates(posX, posY, posZ));
			}
		}

		return toPlace;
	}

	// 平面无限：保留原有 8 邻域 BFS 语义，上限由 maxBlocks 决定
	private static List<ChunkCoordinates> plane(World world, int x, int y, int z, int side, int maxBlocks, Block targetBlock, int targetMeta, ForgeDirection dir, ForgeDirection opp) {
		List<ChunkCoordinates> toPlace = new ArrayList<>();
		if (maxBlocks <= 0) return toPlace;

		Queue<ChunkCoordinates> candidates = new LinkedList<>();
		Set<ChunkCoordinates> visited = new HashSet<>();

		ChunkCoordinates start = new ChunkCoordinates(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
		candidates.add(start);
		visited.add(start);

		int[][] offsets = (side == 0 || side == 1) ? PLANES_XZ : (side == 2 || side == 3) ? PLANES_XY : PLANES_YZ;

		while (!candidates.isEmpty() && toPlace.size() < maxBlocks) {
			ChunkCoordinates curr = candidates.poll();

			if (!canPlaceAt(world, curr.posX, curr.posY, curr.posZ, targetBlock, targetMeta, opp, null)) continue;

			toPlace.add(curr);

			for (int[] offset : offsets) {
				ChunkCoordinates next = new ChunkCoordinates(curr.posX + offset[0], curr.posY + offset[1], curr.posZ + offset[2]);
				if (visited.add(next)) {
					candidates.add(next);
				}
			}
		}

		return toPlace;
	}

	/**
	 * 竖直柱：从点击面外侧那一格起沿 Y 轴延伸。方向取点击面法线的 Y 分量（上表面向上、下表面向下），
	 * 法线水平时固定向上，与玩家朝向无关。长度 min(max(maxBlocks,1), 32)，遇到不合法立刻停。
	 */
	private static List<ChunkCoordinates> pillar(World world, int x, int y, int z, int maxBlocks, Block targetBlock, int targetMeta, ForgeDirection dir, ForgeDirection opp) {
		int step = dir.offsetY < 0 ? -1 : 1;
		int length = Math.min(Math.max(maxBlocks, 1), PILLAR_MAX_LENGTH);

		int posX = x + dir.offsetX;
		int posY = y + dir.offsetY;
		int posZ = z + dir.offsetZ;

		List<ChunkCoordinates> toPlace = new ArrayList<>();
		Set<ChunkCoordinates> planned = new HashSet<>();

		for (int i = 0; i < length; i++) {
			int currY = posY + step * i;

			if (!canPlaceAt(world, posX, currY, posZ, targetBlock, targetMeta, opp, planned)) break;

			ChunkCoordinates coord = new ChunkCoordinates(posX, currY, posZ);
			toPlace.add(coord);
			planned.add(coord);
		}

		return toPlace;
	}

	/**
	 * 水平柱：点击侧面时沿该面法线水平延伸；点击顶/底面时法线竖直，改取玩家水平朝向。
	 * 起点是点击面外侧那一格，长度 min(max(maxBlocks,1), 32)，逐格校验，遇到不合法立刻停。
	 * 支撑方向：第 1 格取 opp（被点击方块本身），之后取 -step（行内前一格）。
	 */
	private static List<ChunkCoordinates> hPillar(World world, int x, int y, int z, int maxBlocks, Block targetBlock, int targetMeta, ForgeDirection dir, ForgeDirection opp, EntityPlayer player) {
		ForgeDirection step = dir.offsetY != 0 ? stepForVerticalFace(player) : dir;
		ForgeDirection rowSupport = step.getOpposite();
		int length = Math.min(Math.max(maxBlocks, 1), PILLAR_MAX_LENGTH);

		int startX = x + dir.offsetX;
		int startY = y + dir.offsetY;
		int startZ = z + dir.offsetZ;

		List<ChunkCoordinates> toPlace = new ArrayList<>();
		Set<ChunkCoordinates> planned = new HashSet<>();

		for (int i = 0; i < length; i++) {
			int posX = startX + step.offsetX * i;
			int posZ = startZ + step.offsetZ * i;
			// 第 1 格靠 opp 贴住被点击方块，第 2 格起靠 -step 贴住行内前一格
			ForgeDirection support = i == 0 ? opp : rowSupport;

			if (!canPlaceAt(world, posX, startY, posZ, targetBlock, targetMeta, support, planned)) break;

			ChunkCoordinates coord = new ChunkCoordinates(posX, startY, posZ);
			toPlace.add(coord);
			planned.add(coord);
		}

		return toPlace;
	}

	// 点击顶/底面时法线竖直、无水平分量：取视线的主水平轴，正上/正下看则退回偏航角
	private static ForgeDirection stepForVerticalFace(EntityPlayer player) {
		if (player == null) return ForgeDirection.NORTH;

		Vec3 look = player.getLookVec();
		double axisX = Math.abs(look.xCoord);
		double axisZ = Math.abs(look.zCoord);

		if (axisX < 1.0E-4D && axisZ < 1.0E-4D) return horizontalFacing(player);
		if (axisX > axisZ) return look.xCoord > 0.0D ? ForgeDirection.EAST : ForgeDirection.WEST;

		return look.zCoord > 0.0D ? ForgeDirection.SOUTH : ForgeDirection.NORTH;
	}

	// 玩家水平朝向：Direction.directionToFacing 即 0=南 1=西 2=北 3=东 到 ForgeDirection 的原版映射
	private static ForgeDirection horizontalFacing(EntityPlayer player) {
		if (player == null) return ForgeDirection.NORTH;

		int index = MathHelper.floor_double((double) (player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
		return ForgeDirection.getOrientation(Direction.directionToFacing[index]);
	}

	// 立方体：以点击面外侧那一格为中心、半径 1 的 3x3x3 实心立方，已合法的才收
	private static List<ChunkCoordinates> cube(World world, int x, int y, int z, Block targetBlock, int targetMeta, ForgeDirection dir, ForgeDirection opp) {
		int centerX = x + dir.offsetX;
		int centerY = y + dir.offsetY;
		int centerZ = z + dir.offsetZ;

		List<ChunkCoordinates> toPlace = new ArrayList<>();
		Set<ChunkCoordinates> planned = new HashSet<>();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int posX = centerX + dx;
					int posY = centerY + dy;
					int posZ = centerZ + dz;

					if (!canPlaceAt(world, posX, posY, posZ, targetBlock, targetMeta, opp, planned)) continue;

					ChunkCoordinates coord = new ChunkCoordinates(posX, posY, posZ);
					toPlace.add(coord);
					planned.add(coord);
				}
			}
		}

		return toPlace;
	}

	/**
	 * 单格校验：Y 越界 / 区块未加载 / 不可替换 / 支撑面不匹配 / 实体碰撞 一律拒绝。
	 * planned 中的格子视同已放置的目标方块（竖直柱与立方体靠它沿法线连续铺开）。
	 */
	private static boolean canPlaceAt(World world, int x, int y, int z, Block targetBlock, int targetMeta, ForgeDirection opp, Set<ChunkCoordinates> planned) {
		// 防护：绝不强制加载或生成区块
		if (y < 0 || y >= world.getHeight()) return false;
		if (!world.blockExists(x, y, z)) return false;

		Block blockAt = world.getBlock(x, y, z);
		if (!world.isAirBlock(x, y, z) && !blockAt.isReplaceable(world, x, y, z)) return false;

		int suppX = x + opp.offsetX;
		int suppY = y + opp.offsetY;
		int suppZ = z + opp.offsetZ;

		if (planned == null || !planned.contains(new ChunkCoordinates(suppX, suppY, suppZ))) {
			if (suppY < 0 || suppY >= world.getHeight()) return false;
			if (!world.blockExists(suppX, suppY, suppZ)) return false;
			if (world.getBlock(suppX, suppY, suppZ) != targetBlock) return false;
			if (world.getBlockMetadata(suppX, suppY, suppZ) != targetMeta) return false;
		}

		AxisAlignedBB aabb = targetBlock.getCollisionBoundingBoxFromPool(world, x, y, z);
		return aabb == null || world.checkNoEntityCollision(aabb);
	}

	// 取垂直于点击面的两个轴向单位向量
	private static void fillPlaneAxes(int side, int[] axisA, int[] axisB) {
		if (side == 0 || side == 1) {
			axisA[0] = 1;
			axisB[2] = 1;
		} else if (side == 2 || side == 3) {
			axisA[0] = 1;
			axisB[1] = 1;
		} else {
			axisA[1] = 1;
			axisB[2] = 1;
		}
	}

	/**
	 * 一次放置操作的记账钱包：EMC 余额缓存在本地字段，只有 flush() 写回玩家数据。
	 */
	public static class Wallet {
		private final EntityPlayer player;
		private final ItemStack template;
		private final boolean creative;
		private final double emcPerBlock;

		private double emcBalance;
		private boolean emcDirty;
		private int paidEmc;
		private int paidItem;

		private boolean knowledgeKnown;
		private boolean hasKnowledge;

		private int[] itemSlots;
		private int[] itemCounts;
		private int itemIndexSize;
		private int itemCursor;
		private boolean itemIndexValid;

		public Wallet(EntityPlayer player, ItemStack targetStack) {
			this.player = player;
			this.template = targetStack == null ? null : targetStack.copy();
			if (this.template != null) {
				this.template.stackSize = 1;
			}

			this.creative = player != null && player.capabilities.isCreativeMode;
			this.emcPerBlock = this.template == null || this.template.getItem() == null ? 0.0 : EMCHelper.getEmcValue(this.template);
			// 创造模式不参与 EMC 记账，省掉一次玩家数据查询
			this.emcBalance = player == null || this.creative ? 0.0 : Transmutation.getEmc(player);
		}

		// 只判断，无副作用；知识判定延迟到真能用 EMC 支付时才做，减少一次玩家数据查询
		public boolean canPayOne() {
			if (this.player == null) return false;
			if (this.creative) return true;
			if (canPayEmc()) return true;

			return hasItem();
		}

		public int payOne() {
			if (this.player == null) return PAY_FAIL;
			// 创造模式免费，不记账，撤销时也就不会退还
			if (this.creative) return PAY_EMC;

			if (canPayEmc()) {
				this.emcBalance -= this.emcPerBlock;
				this.emcDirty = true;
				this.paidEmc++;
				return PAY_EMC;
			}

			if (takeOneFromInventory()) {
				this.paidItem++;
				return PAY_ITEM;
			}

			return PAY_FAIL;
		}

		// 撤销记录是 NBT 持久化的，可能用新建的钱包退还，因此这里不以"本钱包付过"为前提
		public void refund(int source) {
			if (this.player == null) return;

			if (source == PAY_EMC) {
				// 创造模式从未实际支付 EMC，退还等于凭空造 EMC
				if (this.creative || this.emcPerBlock <= 0) return;

				if (this.paidEmc > 0) this.paidEmc--;

				this.emcBalance += this.emcPerBlock;
				this.emcDirty = true;
			} else if (source == PAY_ITEM) {
				if (this.paidItem > 0) this.paidItem--;

				giveBack(1);
			}
		}

		public void refundAll() {
			if (this.player == null) return;

			if (this.paidEmc > 0) {
				this.emcBalance += this.emcPerBlock * this.paidEmc;
				this.paidEmc = 0;
				this.emcDirty = true;
			}

			int count = this.paidItem;
			this.paidItem = 0;
			int max = this.template == null ? 1 : Math.max(this.template.getMaxStackSize(), 1);

			while (count > 0) {
				int batch = Math.min(count, max);
				giveBack(batch);
				count -= batch;
			}
		}

		public void flush() {
			if (this.player == null || !this.emcDirty) return;

			this.emcDirty = false;
			Transmutation.setEmc(this.player, this.emcBalance);
		}

		public double getEmcPerBlock() {
			return this.emcPerBlock;
		}

		public boolean hasKnowledge() {
			if (!this.knowledgeKnown) {
				this.knowledgeKnown = true;
				this.hasKnowledge = this.template != null && this.player != null
					&& Transmutation.hasKnowledgeForStack(this.template, this.player);
			}

			return this.hasKnowledge;
		}

		private boolean canPayEmc() {
			// 短路顺序有意为之：余额不足时不查知识表
			return this.emcPerBlock > 0 && this.emcBalance >= this.emcPerBlock && hasKnowledge();
		}

		private boolean hasItem() {
			return peekItemSlot() >= 0;
		}

		private int peekItemSlot() {
			buildItemIndex();
			while (this.itemCursor < this.itemIndexSize && this.itemCounts[this.itemCursor] <= 0) {
				this.itemCursor++;
			}

			return this.itemCursor < this.itemIndexSize ? this.itemCursor : -1;
		}

		// 一次操作只扫描一次背包，之后每格 O(1)
		private void buildItemIndex() {
			if (this.itemIndexValid) return;

			this.itemIndexValid = true;
			this.itemCursor = 0;
			this.itemIndexSize = 0;

			int capacity = this.player.inventory.getSizeInventory();
			if (this.itemSlots == null || this.itemSlots.length != capacity) {
				this.itemSlots = new int[capacity];
				this.itemCounts = new int[capacity];
			}

			if (this.template == null || this.template.getItem() == null) return;

			for (int i = 0; i < capacity; i++) {
				ItemStack stack = this.player.inventory.getStackInSlot(i);
				if (stack == null || !ItemHelper.basicAreStacksEqual(stack, this.template)) continue;

				this.itemSlots[this.itemIndexSize] = i;
				this.itemCounts[this.itemIndexSize] = stack.stackSize;
				this.itemIndexSize++;
			}
		}

		private boolean takeOneFromInventory() {
			int index = peekItemSlot();
			if (index < 0) return false;

			this.player.inventory.decrStackSize(this.itemSlots[index], 1);
			this.itemCounts[index]--;
			return true;
		}

		// 背包满时退回掉落物，绝不凭空吞掉玩家的方块
		private void giveBack(int count) {
			if (this.template == null || this.template.getItem() == null) return;

			ItemStack stack = this.template.copy();
			stack.stackSize = count;
			// 该方法会把未塞入的剩余量写回 stackSize
			this.player.inventory.addItemStackToInventory(stack);

			if (stack.stackSize > 0) {
				this.player.dropPlayerItemWithRandomChoice(stack, false);
			}

			// 背包内容变了，槽位索引作废
			this.itemIndexValid = false;
		}
	}
}
