package moze_intel.projecte.utils.mercurial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockStairs;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.HashMap;
import java.util.Map;

public class StateRotatorRegistry {
	private static final Map<Class<?>, IStateRotator> CLASS_ROTATORS = new HashMap<>();
	private static final Map<String, IStateRotator> GT_NBT_ROTATOR = new HashMap<>();

	static {
		// 原版原木
		CLASS_ROTATORS.put(BlockLog.class, new IStateRotator() {
			@Override
			public int rotateMeta(Block block, int meta, int rotations) {
				if (rotations % 2 == 0) return meta; // 180度不变
				int type = meta & 3;
				int axis = meta & 12;
				if (axis == 4) return type | 8; // X 轴变 Z 轴
				if (axis == 8) return type | 4; // Z 轴变 X 轴
				return meta; // Y 轴不变
			}
			@Override public void rotateNBT(Block block, int meta, NBTTagCompound nbt, int rotations) {}
		});

		// 原版楼梯
		CLASS_ROTATORS.put(BlockStairs.class, new IStateRotator() {
			@Override
			public int rotateMeta(Block block, int meta, int rotations) {
				int facing = meta & 3;
				int top = meta & 4;
				for (int i = 0; i < (rotations % 4); i++) {
					// 0:E, 1:W, 2:S, 3:N -> 顺时针旋转: E(0)->S(2)->W(1)->N(3)->E(0)
					switch (facing) {
						case 0: facing = 2; break;
						case 2: facing = 1; break;
						case 1: facing = 3; break;
						case 3: facing = 0; break;
					}
				}
				return facing | top;
			}
			@Override public void rotateNBT(Block block, int meta, NBTTagCompound nbt, int rotations) {}
		});

		// 针对 GTNH (GregTech 5U) 机器的通用 NBT 旋转
		// 格雷的大部分机器朝向存储在 NBT 的 "mFacing" 字段 (short 类型)
		IStateRotator gtRotator = new IStateRotator() {
			@Override
			public int rotateMeta(Block block, int meta, int rotations) {
				return meta; // 格雷机器的 Meta 是机器 ID，不能乱动
			}

			@Override
			public void rotateNBT(Block block, int meta, NBTTagCompound nbt, int rotations) {
				if (nbt != null && nbt.hasKey("mFacing")) {
					short facingOrd = nbt.getShort("mFacing");
					if (facingOrd >= 0 && facingOrd < ForgeDirection.VALID_DIRECTIONS.length) {
						ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[facingOrd];
						for (int i = 0; i < (rotations % 4); i++) {
							dir = rotateY(dir);
						}
						nbt.setShort("mFacing", (short) dir.ordinal());
					}
				}
				// 如果需要处理 GT 覆盖板 (Covers)，可以在这里遍历 NBT 里的 6 个面并进行重映射映射
				// NBT key: "mCoverID0" ~ "mCoverID5"
				if (rotations % 4 != 0 && nbt != null) {
					rotateGTCovers(nbt, rotations % 4);
				}
			}
		};
		// 用一个特殊的标识符暂存，稍后在查找时只要方块是 GregTechAPI.sBlockMachines 就调用它
		GT_NBT_ROTATOR.put("GregTech", gtRotator);
	}

	/**
	 * 获取对应方块的旋转器
	 */
	public static IStateRotator getRotator(Block block) {
		if (block == null) return null;

		// 特判 GT 机器
		String blockName = Block.blockRegistry.getNameForObject(block);
		if (blockName != null && blockName.startsWith("gregtech:gt.blockmachines")) {
			return GT_NBT_ROTATOR.get("GregTech");
		}

		// 按类查找
		for (Map.Entry<Class<?>, IStateRotator> entry : CLASS_ROTATORS.entrySet()) {
			if (entry.getKey().isAssignableFrom(block.getClass())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * 工具方法：绕 Y 轴顺时针旋转 ForgeDirection
	 */
	public static ForgeDirection rotateY(ForgeDirection dir) {
		switch (dir) {
			case NORTH: return ForgeDirection.EAST;
			case EAST:  return ForgeDirection.SOUTH;
			case SOUTH: return ForgeDirection.WEST;
			case WEST:  return ForgeDirection.NORTH;
			default:    return dir; // UP/DOWN/UNKNOWN 保持不变
		}
	}

	/**
	 * 处理格雷科技机器六个面的覆盖板数据旋转
	 */
	private static void rotateGTCovers(NBTTagCompound nbt, int steps) {
		for (int i = 0; i < steps; i++) {
			// 缓存旧数据
			int[] oldIDs = new int[6];
			int[] oldDatas = new int[6];
			for (int side = 0; side < 6; side++) {
				oldIDs[side] = nbt.getInteger("mCoverID" + side);
				oldDatas[side] = nbt.getInteger("mCoverData" + side);
			}
			// 执行顺时针面映射：N(2)->E(5)->S(3)->W(4)->N(2)
			nbt.setInteger("mCoverID5", oldIDs[2]); nbt.setInteger("mCoverData5", oldDatas[2]);
			nbt.setInteger("mCoverID3", oldIDs[5]); nbt.setInteger("mCoverData3", oldDatas[5]);
			nbt.setInteger("mCoverID4", oldIDs[3]); nbt.setInteger("mCoverData4", oldDatas[3]);
			nbt.setInteger("mCoverID2", oldIDs[4]); nbt.setInteger("mCoverData2", oldDatas[4]);
			// 上下面(0,1)保持不变
		}
	}
}
