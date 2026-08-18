package moze_intel.projecte.utils;

import net.minecraft.util.Vec3;

/**
 * 纯数学工具类：用于计算射线与三维平面的交点 (MIT License)
 */
public class RayTraceMath {

	public enum Plane { X, Y, Z }

	/**
	 * 计算玩家视线与指定平面的交点坐标
	 * @param eyePos 玩家眼睛的绝对坐标 (Point)
	 * @param lookVec 玩家视线向量 (Vector)
	 * @param plane 限制平面 (X, Y 或 Z)
	 * @param planeValue 平面的固定坐标值 (比如点击了y=64的方块顶部，plane=Y, planeValue=64.0)
	 * @return 悬空交点坐标
	 */
	public static Vec3 getIntersection(Vec3 eyePos, Vec3 lookVec, Plane plane, double planeValue) {
		double t = 0;
		// 射线参数方程：P = Eye + Look * t
		// 求解 t = (PlaneValue - Eye) / Look
		switch (plane) {
			case X:
				if (Math.abs(lookVec.xCoord) < 1e-5) return null; // 平行于平面，无交点
				t = (planeValue - eyePos.xCoord) / lookVec.xCoord;
				break;
			case Y:
				if (Math.abs(lookVec.yCoord) < 1e-5) return null;
				t = (planeValue - eyePos.yCoord) / lookVec.yCoord;
				break;
			case Z:
				if (Math.abs(lookVec.zCoord) < 1e-5) return null;
				t = (planeValue - eyePos.zCoord) / lookVec.zCoord;
				break;
		}

		// 交点在玩家背后
		if (t < 0) return null;

		return Vec3.createVectorHelper(
			eyePos.xCoord + lookVec.xCoord * t,
			eyePos.yCoord + lookVec.yCoord * t,
			eyePos.zCoord + lookVec.zCoord * t
		);
	}
}
