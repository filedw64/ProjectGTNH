package moze_intel.projecte.utils;

import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class WandHelper {

	// 模式常量：与 BuildersWand 的 NBT 字节 "Mode" 一一对应
	public static final byte MODE_3X3 = 0;
	public static final byte MODE_5X5 = 1;
	public static final byte MODE_PLANE = 2;
	public static final byte MODE_COUNT = 3;

	private static final int[][] PLANES_XZ = {{1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {1,0,1}, {1,0,-1}, {-1,0,1}, {-1,0,-1}};
	private static final int[][] PLANES_XY = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {1,1,0}, {1,-1,0}, {-1,1,0}, {-1,-1,0}};
	private static final int[][] PLANES_YZ = {{0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}, {0,1,1}, {0,1,-1}, {0,-1,1}, {0,-1,-1}};

	/**
	 * 【性能】背包匹配槽位索引：一次放置操作内只扫描一次背包，之后每格 O(1) 扣减。
	 */
	public static class CostIndex {
		private final int[] slots;
		private final int[] counts;
		private final int size;
		private int cursor;

		private CostIndex(int[] slots, int[] counts, int size) {
			this.slots = slots;
			this.counts = counts;
			this.size = size;
		}

		private boolean take(EntityPlayer player) {
			while (this.cursor < this.size && this.counts[this.cursor] <= 0) {
				this.cursor++;
			}

			if (this.cursor >= this.size) {
				return false;
			}

			player.inventory.decrStackSize(this.slots[this.cursor], 1);
			this.counts[this.cursor]--;
			return true;
		}
	}

	/**
	 * 建索引：一次遍历背包，记录所有匹配槽位与各自数量。
	 */
	public static CostIndex createCostIndex(EntityPlayer player, ItemStack targetStack) {
		int capacity = player.inventory.getSizeInventory();
		int[] slots = new int[capacity];
		int[] counts = new int[capacity];
		int size = 0;

		for (int i = 0; i < capacity; i++) {
			ItemStack invStack = player.inventory.getStackInSlot(i);
			if (invStack == null || !ItemHelper.basicAreStacksEqual(invStack, targetStack)) continue;

			slots[size] = i;
			counts[size] = invStack.stackSize;
			size++;
		}

		return new CostIndex(slots, counts, size);
	}

	/**
	 * 一次操作内的消耗（配合 createCostIndex 使用，每格 O(1)）。
	 */
	public static boolean consumeCost(EntityPlayer player, ItemStack targetStack, boolean hasKnowledge, double emcCost, CostIndex index) {
		if (player.capabilities.isCreativeMode) return true;

		if (hasKnowledge && emcCost > 0) {
			double currentEmc = Transmutation.getEmc(player);
			if (currentEmc >= emcCost) {
				Transmutation.setEmc(player, currentEmc - emcCost);
				return true;
			}
		}

		return index.take(player);
	}

	/**
	 * 单次消耗入口：供非循环场景使用，循环内请改用带 CostIndex 的重载。
	 */
	public static boolean consumeCost(EntityPlayer player, ItemStack targetStack, boolean hasKnowledge, double emcCost) {
		return consumeCost(player, targetStack, hasKnowledge, emcCost, createCostIndex(player, targetStack));
	}

	/**
	 * 旧签名保留：语义等同"平面无限"模式。
	 */
	public static List<ChunkCoordinates> getBlocksToPlace(World world, int x, int y, int z, int side, int maxBlocks) {
		return getPlaneBlocks(world, x, y, z, side, maxBlocks);
	}

	/**
	 * 按模式生成待放置坐标。
	 */
	public static List<ChunkCoordinates> getBlocksToPlace(World world, int x, int y, int z, int side, byte mode, int maxBlocks) {
		if (mode == MODE_3X3) {
			return getSquareBlocks(world, x, y, z, side, 1);
		}

		if (mode == MODE_5X5) {
			return getSquareBlocks(world, x, y, z, side, 2);
		}

		return getPlaneBlocks(world, x, y, z, side, maxBlocks);
	}

	/**
	 * 3x3 / 5x5：点击面所在平面内以被点击方块为圆心的完整正方形，逐格独立校验。
	 */
	private static List<ChunkCoordinates> getSquareBlocks(World world, int x, int y, int z, int side, int radius) {
		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		ForgeDirection opp = dir.getOpposite();

		// 圆心为点击面外侧那一格，正方形在垂直于点击面的平面内展开
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

				if (!canPlaceAt(world, posX, posY, posZ, targetBlock, targetMeta, opp)) continue;

				toPlace.add(new ChunkCoordinates(posX, posY, posZ));
			}
		}

		return toPlace;
	}

	/**
	 * 平面无限：保留原有 8 邻域 BFS 语义，上限由 config buildersWandMaxBlocks 决定。
	 */
	private static List<ChunkCoordinates> getPlaneBlocks(World world, int x, int y, int z, int side, int maxBlocks) {
		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		ForgeDirection opp = dir.getOpposite();

		List<ChunkCoordinates> toPlace = new ArrayList<>();
		Queue<ChunkCoordinates> candidates = new LinkedList<>();
		Set<ChunkCoordinates> visited = new HashSet<>();

		ChunkCoordinates start = new ChunkCoordinates(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
		candidates.add(start);
		visited.add(start);

		int[][] offsets = (side == 0 || side == 1) ? PLANES_XZ : (side == 2 || side == 3) ? PLANES_XY : PLANES_YZ;

		while (!candidates.isEmpty() && toPlace.size() < maxBlocks) {
			ChunkCoordinates curr = candidates.poll();

			if (!canPlaceAt(world, curr.posX, curr.posY, curr.posZ, targetBlock, targetMeta, opp)) continue;

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
	 * 单格校验：Y 越界 / 区块未加载 / 不可替换 / 支撑面不匹配 / 实体碰撞 一律拒绝。
	 */
	private static boolean canPlaceAt(World world, int x, int y, int z, Block targetBlock, int targetMeta, ForgeDirection opp) {
		// 防护：绝不强制加载或生成区块
		if (y < 0 || y >= world.getHeight()) return false;
		if (!world.blockExists(x, y, z)) return false;

		Block blockAt = world.getBlock(x, y, z);
		if (!world.isAirBlock(x, y, z) && !blockAt.isReplaceable(world, x, y, z)) return false;

		int suppX = x + opp.offsetX;
		int suppY = y + opp.offsetY;
		int suppZ = z + opp.offsetZ;

		if (suppY < 0 || suppY >= world.getHeight()) return false;
		if (!world.blockExists(suppX, suppY, suppZ)) return false;
		if (world.getBlock(suppX, suppY, suppZ) != targetBlock) return false;
		if (world.getBlockMetadata(suppX, suppY, suppZ) != targetMeta) return false;

		AxisAlignedBB aabb = targetBlock.getCollisionBoundingBoxFromPool(world, x, y, z);
		if (aabb != null && !world.checkNoEntityCollision(aabb)) return false;

		return true;
	}

	/**
	 * 取垂直于点击面的两个轴向单位向量。
	 */
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
}
