package moze_intel.projecte.utils;

public enum BuildShape {
	// 2-Clicks (只需起点和终点)
	LINE("Line", 2),
	WALL("Wall", 2),
	FLOOR("Floor", 2),

	// 3-Clicks (需要起点、中点确定底面，终点确定高度)
	CUBE("Cube (Solid)", 3),
	CUBE_HOLLOW("Cube (Hollow)", 3),
	CUBE_WIRE("Cube (Wireframe)", 3),
	SPHERE("Sphere (Solid)", 3),
	SPHERE_HOLLOW("Sphere (Hollow)", 3),
	CYLINDER("Cylinder (Solid)", 3),
	CYLINDER_HOLLOW("Cylinder (Hollow)", 3),
	SLOPE("Slope", 3);

	private final String displayName;
	private final int requiredClicks;

	BuildShape(String displayName, int requiredClicks) {
		this.displayName = displayName;
		this.requiredClicks = requiredClicks;
	}

	public String getDisplayName() { return displayName; }
	public int getRequiredClicks() { return requiredClicks; }

	/** 形状遍历回调：服务端批量建造与客户端线框预览共用同一套迭代顺序 */
	public interface BlockVisitor {
		void visit(int x, int y, int z);
	}

	/**
	 * 统一的形状遍历入口。服务端放置与客户端预览都必须走这里，避免两处迭代逻辑再次分叉。
	 * 迭代顺序固定：LINE 用 Bresenham 式步进（i = 0..dist），其余形状为 x -> y -> z 三层正序循环（含端点）。
	 */
	public void forEachBlock(int startX, int startY, int startZ, int endX, int endY, int endZ, BlockVisitor visitor) {
		if (this == LINE) {
			// 直线由自己的步进处理，不经过 isBlockInShape
			int dist = (int) Math.ceil(Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2) + Math.pow(endZ - startZ, 2)));
			for (int i = 0; i <= dist; i++) {
				double t = dist == 0 ? 0 : (double) i / dist;
				int px = (int) Math.round(startX + (endX - startX) * t);
				int py = (int) Math.round(startY + (endY - startY) * t);
				int pz = (int) Math.round(startZ + (endZ - startZ) * t);
				visitor.visit(px, py, pz);
			}
			return;
		}

		int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
		int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
		int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (!isBlockInShape(x, y, z, startX, startY, startZ, endX, endY, endZ)) continue;
					visitor.visit(x, y, z);
				}
			}
		}
	}

	public boolean isBlockInShape(int x, int y, int z, int startX, int startY, int startZ, int endX, int endY, int endZ) {
		int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
		int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
		int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

		switch (this) {
			case CUBE:
			case LINE: // 直线由外部的 Bresenham 算法独立处理
				return true;
			case WALL:
				// 强制变成 2D 墙壁，防止鼠标抖动导致墙壁变厚
				if (Math.abs(endX - startX) > Math.abs(endZ - startZ)) return z == startZ;
				else return x == startX;
			case FLOOR:
				return y == startY;
			case CUBE_HOLLOW:
				return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
			case CUBE_WIRE:
				int edges = 0;
				if (x == minX || x == maxX) edges++;
				if (y == minY || y == maxY) edges++;
				if (z == minZ || z == maxZ) edges++;
				return edges >= 2;
			case SPHERE:
			case SPHERE_HOLLOW:
				double cx = (minX + maxX) / 2.0; double cy = (minY + maxY) / 2.0; double cz = (minZ + maxZ) / 2.0;
				double rx = Math.max((maxX - minX) / 2.0, 0.5);
				double ry = Math.max((maxY - minY) / 2.0, 0.5);
				double rz = Math.max((maxZ - minZ) / 2.0, 0.5);
				double distSq = Math.pow((x - cx)/rx, 2) + Math.pow((y - cy)/ry, 2) + Math.pow((z - cz)/rz, 2);
				if (this == SPHERE) return distSq <= 1.0;
				double innerRx = Math.max(rx - 1.0, 0.1); double innerRy = Math.max(ry - 1.0, 0.1); double innerRz = Math.max(rz - 1.0, 0.1);
				double innerDistSq = Math.pow((x - cx)/innerRx, 2) + Math.pow((y - cy)/innerRy, 2) + Math.pow((z - cz)/innerRz, 2);
				return distSq <= 1.0 && innerDistSq > 1.0;
			case CYLINDER:
			case CYLINDER_HOLLOW:
				double cylCx = (minX + maxX) / 2.0; double cylCz = (minZ + maxZ) / 2.0;
				double cylRx = Math.max((maxX - minX) / 2.0, 0.5); double cylRz = Math.max((maxZ - minZ) / 2.0, 0.5);
				double cylDistSq = Math.pow((x - cylCx)/cylRx, 2) + Math.pow((z - cylCz)/cylRz, 2);
				if (this == CYLINDER) return cylDistSq <= 1.0;
				boolean isTopOrBottom = (y == minY || y == maxY);
				double cylInnerRx = Math.max(cylRx - 1.0, 0.1); double cylInnerRz = Math.max(cylRz - 1.0, 0.1);
				double cylInnerDistSq = Math.pow((x - cylCx)/cylInnerRx, 2) + Math.pow((z - cylCz)/cylInnerRz, 2);
				return cylDistSq <= 1.0 && (isTopOrBottom || cylInnerDistSq > 1.0);
			case SLOPE:
				// 支持 4 个方向的斜坡
				boolean slopeOnX = (maxX - minX) > (maxZ - minZ);
				if (slopeOnX) {
					if (maxX == minX) return true;
					double t = (double)(x - startX) / (endX - startX); // t 在 0 到 1 之间
					return y == (int) Math.round(startY + (endY - startY) * t);
				} else {
					if (maxZ == minZ) return true;
					double t = (double)(z - startZ) / (endZ - startZ);
					return y == (int) Math.round(startY + (endY - startY) * t);
				}
			default: return true;
		}
	}
}
