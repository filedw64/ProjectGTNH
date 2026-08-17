package moze_intel.projecte.utils;

import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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

	// 获取需要延伸放置的方块坐标列表 (返回的是原方块位置，放置时需要向 side 偏移一格)
	public static List<ChunkCoordinates> getBlocksToPlace(World world, int x, int y, int z, int side, int maxBlocks) {
		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ForgeDirection dir = ForgeDirection.getOrientation(side);

		List<ChunkCoordinates> result = new ArrayList<>();
		Queue<ChunkCoordinates> queue = new LinkedList<>();
		Set<ChunkCoordinates> visited = new HashSet<>();

		ChunkCoordinates start = new ChunkCoordinates(x, y, z);
		queue.add(start);
		visited.add(start);

		// 八个方向（包括对角线）
		int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

		while (!queue.isEmpty() && result.size() < maxBlocks) {
			ChunkCoordinates curr = queue.poll();

			// 检查对应面是否为空气或可替换方块
			int placeX = curr.posX + dir.offsetX;
			int placeY = curr.posY + dir.offsetY;
			int placeZ = curr.posZ + dir.offsetZ;

			if (world.isAirBlock(placeX, placeY, placeZ) || world.getBlock(placeX, placeY, placeZ).isReplaceable(world, placeX, placeY, placeZ)) {
				result.add(curr);
			}

			for (int[] d : dirs) {
				int nx = curr.posX, ny = curr.posY, nz = curr.posZ;

				// 根据点击的面决定在哪个平面上扩散
				if (side == 0 || side == 1) { nx += d[0]; nz += d[1]; } // Y轴面 -> 扩散 X, Z
				else if (side == 2 || side == 3) { nx += d[0]; ny += d[1]; } // Z轴面 -> 扩散 X, Y
				else { ny += d[0]; nz += d[1]; } // X轴面 -> 扩散 Y, Z

				ChunkCoordinates next = new ChunkCoordinates(nx, ny, nz);
				if (!visited.contains(next)) {
					visited.add(next);
					if (world.getBlock(nx, ny, nz) == targetBlock && world.getBlockMetadata(nx, ny, nz) == targetMeta) {
						queue.add(next);
					}
				}
			}
		}
		return result;
	}

	// 尝试消耗 EMC 或 背包物品
	public static boolean consumeCost(EntityPlayer player, ItemStack targetStack) {
		if (player.capabilities.isCreativeMode) return true;

		boolean hasKnowledge = Transmutation.hasKnowledgeForStack(targetStack, player);
		double emcCost = EMCHelper.getEmcValue(targetStack);

		// 1. 尝试消耗个人 EMC
		if (hasKnowledge && emcCost > 0) {
			double currentEmc = Transmutation.getEmc(player);
			if (currentEmc >= emcCost) {
				Transmutation.setEmc(player, currentEmc - emcCost);
				// 注意: 服务端修改 EMC 后，可能需要发包给客户端同步 (类似 SyncEmcPKT)
				return true;
			}
		}

		// 2. 尝试消耗背包实体方块
		for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
			ItemStack invStack = player.inventory.getStackInSlot(i);
			if (invStack != null && ItemHelper.basicAreStacksEqual(invStack, targetStack)) {
				player.inventory.decrStackSize(i, 1);
				player.inventoryContainer.detectAndSendChanges();
				return true;
			}
		}

		return false;
	}
}
