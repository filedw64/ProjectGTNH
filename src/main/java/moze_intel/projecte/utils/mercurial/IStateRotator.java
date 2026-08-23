package moze_intel.projecte.utils.mercurial;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 状态旋转器：抹平 1.7.10 混乱的元数据和 NBT 朝向定义。
 */
public interface IStateRotator {

	/**
	 * 旋转方块的 Metadata
	 * @param block 目标方块
	 * @param meta 原 Metadata
	 * @param rotations 顺时针旋转次数 (1 = 90°, 2 = 180°, 3 = 270°)
	 * @return 旋转后的 Metadata
	 */
	int rotateMeta(Block block, int meta, int rotations);

	/**
	 * 旋转 TileEntity 的 NBT 数据 (如机器朝向)
	 * @param block 目标方块
	 * @param meta 旋转后的 Metadata
	 * @param nbt 原 NBTTagCompound
	 * @param rotations 顺时针旋转次数
	 */
	void rotateNBT(Block block, int meta, NBTTagCompound nbt, int rotations);
}
