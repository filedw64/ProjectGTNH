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

	private static final int[][] PLANES_XZ = {{1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {1,0,1}, {1,0,-1}, {-1,0,1}, {-1,0,-1}};
	private static final int[][] PLANES_XY = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {1,1,0}, {1,-1,0}, {-1,1,0}, {-1,-1,0}};
	private static final int[][] PLANES_YZ = {{0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}, {0,1,1}, {0,1,-1}, {0,-1,1}, {0,-1,-1}};

	public static List<ChunkCoordinates> getBlocksToPlace(World world, int x, int y, int z, int side, int maxBlocks) {
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

			if (curr.posY < 0 || curr.posY >= 256) continue;

			// 【优化】防止扫描导致未加载的区块被强制加载/生成
			if (!world.blockExists(curr.posX, curr.posY, curr.posZ)) continue;

			Block blockAt = world.getBlock(curr.posX, curr.posY, curr.posZ);
			if (!world.isAirBlock(curr.posX, curr.posY, curr.posZ) && !blockAt.isReplaceable(world, curr.posX, curr.posY, curr.posZ)) {
				continue;
			}

			int suppX = curr.posX + opp.offsetX;
			int suppY = curr.posY + opp.offsetY;
			int suppZ = curr.posZ + opp.offsetZ;

			// 【优化】同样拦截支撑方块跨区块加载
			if (!world.blockExists(suppX, suppY, suppZ)) continue;

			if (world.getBlock(suppX, suppY, suppZ) != targetBlock || world.getBlockMetadata(suppX, suppY, suppZ) != targetMeta) {
				continue;
			}

			AxisAlignedBB aabb = targetBlock.getCollisionBoundingBoxFromPool(world, curr.posX, curr.posY, curr.posZ);
			if (aabb != null && !world.checkNoEntityCollision(aabb)) {
				continue;
			}

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

	// 【优化】接收预计算好的 EMC 价格和知识，不再重复计算
	public static boolean consumeCost(EntityPlayer player, ItemStack targetStack, boolean hasKnowledge, double emcCost) {
		if (player.capabilities.isCreativeMode) return true;

		if (hasKnowledge && emcCost > 0) {
			double currentEmc = Transmutation.getEmc(player);
			if (currentEmc >= emcCost) {
				Transmutation.setEmc(player, currentEmc - emcCost);
				return true;
			}
		}

		for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
			ItemStack invStack = player.inventory.getStackInSlot(i);
			if (invStack != null && ItemHelper.basicAreStacksEqual(invStack, targetStack)) {
				player.inventory.decrStackSize(i, 1);
				return true; // 注意：直接 return true，不需要每次 detectAndSendChanges 降低开销
			}
		}
		return false;
	}
}
