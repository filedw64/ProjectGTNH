package moze_intel.projecte.utils;

/**
 * 建筑之杖模式：id 与 NBT "Mode" 字节一一对应，0/1/2 的语义已冻结，老存档必须照旧可用。
 */
public enum WandMode {
	MODE_3X3((byte) 0, "pe.builderswand.mode.3x3", 1, false),
	MODE_5X5((byte) 1, "pe.builderswand.mode.5x5", 2, false),
	MODE_PLANE((byte) 2, "pe.builderswand.mode.plane", 0, true),
	MODE_7X7((byte) 3, "pe.builderswand.mode.7x7", 3, false),
	MODE_PILLAR((byte) 4, "pe.builderswand.mode.pillar", 0, false),
	MODE_H_PILLAR((byte) 6, "pe.builderswand.mode.hpillar", 0, false),
	MODE_CUBE((byte) 5, "pe.builderswand.mode.cube", 1, false);

	public static final byte DEFAULT_ID = 0;

	// 按实际模式数分配：新增模式后 fromId(新 id) 必须仍能命中
	private static final WandMode[] BY_ID = new WandMode[values().length];

	static {
		for (WandMode mode : values()) {
			int id = mode.id;
			if (id >= 0 && id < BY_ID.length) {
				BY_ID[id] = mode;
			}
		}
	}

	private final byte id;
	private final String langKey;
	private final int radius;
	private final boolean blockBudget;

	WandMode(byte id, String langKey, int radius, boolean blockBudget) {
		this.id = id;
		this.langKey = langKey;
		this.radius = radius;
		this.blockBudget = blockBudget;
	}

	// 越界或非法一律回落 3x3，坏 NBT 不会让建筑之杖失效
	public static WandMode fromId(byte id) {
		if (id < 0 || id >= BY_ID.length) return MODE_3X3;

		WandMode mode = BY_ID[id];
		return mode == null ? MODE_3X3 : mode;
	}

	public byte getId() {
		return this.id;
	}

	public String getLangKey() {
		return this.langKey;
	}

	// 只有平面无限模式受整次操作的方块预算限制
	public boolean usesBlockBudget() {
		return this.blockBudget;
	}

	public int getRadius() {
		return this.radius;
	}
}
