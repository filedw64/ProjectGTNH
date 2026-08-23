package moze_intel.projecte.utils.mercurial;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import java.util.ArrayList;
import java.util.List;

/**
 * 暂存在服务端内存中的选区剪贴板数据
 */
public class ClipboardData {
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	public final List<BlockInfo> blocks = new ArrayList<>();

	public ClipboardData(int sizeX, int sizeY, int sizeZ) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
	}

	public void addBlock(Block block, int meta, NBTTagCompound nbt, int localX, int localY, int localZ, double emcCost) {
		blocks.add(new BlockInfo(block, meta, nbt, localX, localY, localZ, emcCost));
	}

	/**
	 * 计算粘贴整个剪贴板需要的基础 EMC 消耗
	 */
	public double getTotalEmcCost() {
		double total = 0;
		for (BlockInfo info : blocks) {
			total += info.emcCost;
		}
		return total;
	}

	public static class BlockInfo {
		public final Block block;
		public final int meta;
		public final NBTTagCompound nbt;
		public final int localX, localY, localZ;
		public final double emcCost;

		public BlockInfo(Block block, int meta, NBTTagCompound nbt, int localX, int localY, int localZ, double emcCost) {
			this.block = block;
			this.meta = meta;
			this.nbt = nbt;
			this.localX = localX;
			this.localY = localY;
			this.localZ = localZ;
			this.emcCost = emcCost;
		}
	}
}
