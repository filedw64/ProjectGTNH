package moze_intel.projecte.utils.mercurial;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TransformMatrix {
	public int rotations = 0; // 0=0°, 1=90°, 2=180°, 3=270° (顺时针)
	public ForgeDirection stackAxis = ForgeDirection.UNKNOWN;
	public int stackCount = 1;

	public TransformMatrix() {}

	/**
	 * 将局部坐标绕原点 (锚点) 进行旋转变换
	 * 90° 顺时针矩阵：x' = -z, z' = x
	 */
	public int[] applyRotation(int localX, int localY, int localZ) {
		int r = rotations % 4;
		if (r < 0) r += 4;

		switch (r) {
			case 1:  return new int[] { -localZ, localY, localX }; // 90°
			case 2:  return new int[] { -localX, localY, -localZ }; // 180°
			case 3:  return new int[] { localZ, localY, -localX }; // 270°
			default: return new int[] { localX, localY, localZ }; // 0°
		}
	}

	/**
	 * 根据原选区的局部边界，计算旋转后的新局部边界
	 * @return [minX, minY, minZ, maxX, maxY, maxZ]
	 */
	public int[] getRotatedBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int r = rotations % 4;
		if (r < 0) r += 4;
		switch (r) {
			case 1:  return new int[] { -maxZ, minY, minX, -minZ, maxY, maxX }; // 90°
			case 2:  return new int[] { -maxX, minY, -maxZ, -minX, maxY, -minZ }; // 180°
			case 3:  return new int[] { minZ, minY, -maxX, maxZ, maxY, -minX }; // 270°
			default: return new int[] { minX, minY, minZ, maxX, maxY, maxZ }; // 0°
		}
	}

	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setByte("Rotations", (byte) this.rotations);
		nbt.setByte("StackAxis", (byte) this.stackAxis.ordinal());
		nbt.setInteger("StackCount", this.stackCount);
	}

	public void readFromNBT(NBTTagCompound nbt) {
		this.rotations = nbt.getByte("Rotations");
		byte axisOrd = nbt.getByte("StackAxis");
		if (axisOrd >= 0 && axisOrd < ForgeDirection.VALID_DIRECTIONS.length) {
			this.stackAxis = ForgeDirection.VALID_DIRECTIONS[axisOrd];
		} else {
			this.stackAxis = ForgeDirection.UNKNOWN;
		}
		this.stackCount = nbt.getInteger("StackCount");
		if (this.stackCount < 1) this.stackCount = 1;
	}
}
