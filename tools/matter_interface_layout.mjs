// 物质接口 GUI 的坐标唯一来源。
// 贴图生成（gen_matter_interface_gui_texture.mjs）、预览合成（preview_matter_interface_gui.mjs）
// 与 Java 源码一致性检查（check_interface_gui_layout.mjs）都只从这里取数。
// 贴图本身只画面板装饰；槽位框 / 3x3 主网格 / 方向选择器 / 输入框全部由 Java 在运行时按同一批坐标绘制。
import zlib from "node:zlib";

export const GUI_W = 176;
export const GUI_H = 244;

// ---------- 槽位（Slot.xDisplayPosition 语义：物品内容区左上角）----------
export const SLOT_PITCH = 18;
export const SLOT_FRAME = 18;
export const FILTER_SLOT_X = 62;
export const FILTER_SLOT_Y = 18;
export const PLAYER_SLOT_X = 8;
export const PLAYER_SLOT_Y = 164;
export const HOTBAR_SLOT_Y = 218;

// ---------- 过滤表：抽入 / 推出两套，同一组 3x3 坐标 ----------
/** 每套过滤表的格数（3x3） */
export const FILTER_SET_SIZE = 9;
/** 过滤槽总数 = 抽入 9 + 推出 9（幽灵槽 0..8 / 9..17） */
export const FILTER_SLOT_COUNT = 18;
/** 过滤表套数 */
export const FILTER_SET_COUNT = 2;
/** 两套表的名字（与 Java 的 activeFilterSet 0 / 1 一一对应） */
export const FILTER_SETS = ["input", "output"];
/**
 * 不活跃那一套过滤槽的屏幕外 Y：GUI 切标签时两套坐标互换，
 * 于是同一时刻只有 9 格落在窗口里 —— 悬停 / 点击 / NEI 拖入的命中判定天然只认激活的那一套。
 */
export const FILTER_OFFSCREEN_Y = -2000;
/** 两套表按 row-major 共用的 9 个格子坐标（相对窗口左上角） */
export const FILTER_SLOT_CELLS = (() => {
	const cells = [];

	for (let row = 0; row < 3; row++)
		for (let col = 0; col < 3; col++)
			cells.push({ x: FILTER_SLOT_X + col * SLOT_PITCH, y: FILTER_SLOT_Y + row * SLOT_PITCH });

	return cells;
})();

/** 当前激活 setIndex 那一套时，某一套的 9 个槽位坐标；不在激活状态的一套整块挪到屏幕外 */
export function filterSetSlots(setIndex, activeSet = 0) {
	const baseY = setIndex === activeSet ? FILTER_SLOT_Y : FILTER_OFFSCREEN_Y;

	return FILTER_SLOT_CELLS.map((cell) => ({ x: cell.x, y: baseY + (cell.y - FILTER_SLOT_Y) }));
}

/** 两套 9 格坐标表（脚本用它断言「同一时刻只有一套在可视区」） */
export function filterSlotTable(activeSet = 0) {
	return { input: filterSetSlots(0, activeSet), output: filterSetSlots(1, activeSet) };
}

// ---------- 过滤表标签（抽入 / 推出）----------
export const FILTER_TAB_INPUT_X = 118;
export const FILTER_TAB_OUTPUT_X = 145;
export const FILTER_TAB_Y = 52;
export const FILTER_TAB_W = 26;
export const FILTER_TAB_H = 12;
/** 激活标签底下那条高亮线的高度 */
export const FILTER_TAB_MARK_H = 2;
/** 过滤区旁「正在编辑哪一套」小字 */
export const FILTER_SET_LABEL_X = 118;
export const FILTER_SET_LABEL_Y = 68;
export const FILTER_SET_LABEL_W = 52;

// ---------- 3x3 主网格（也用作方向选择器的底板）----------
// 16x16 一格、步进 16：整块 48x48，左侧 8..55 / 上方 18..65，右缘离过滤槽框（x=61）还有 5px
export const GRID_LEFT = 8;
export const GRID_TOP = 18;
export const CELL_SIZE = 16;
export const CELL_PITCH = 16;
export const GRID_COLUMNS = 3;
export const GRID_ROWS = 3;
export const GRID_CELLS = 9;
/** 『前』= 玩家点开 GUI 的那一面，永远在正中间那一格 */
export const GRID_FRONT_CELL = 4;

// ---------- 优先级（挪到右上角：左侧整块留给 3x3 主网格）----------
export const PRIORITY_LABEL_X = 118;
export const PRIORITY_LABEL_Y = 20;
export const PRIORITY_MINUS_X = 118;
export const PRIORITY_PLUS_X = 148;
export const PRIORITY_BUTTON_Y = 34;
export const PRIORITY_BUTTON_SIZE = 14;
export const PRIORITY_VALUE_X = 140;
export const PRIORITY_VALUE_Y = 37;

// ---------- 状态行 + 悬停提示行 ----------
export const STATUS_X = 8;
export const STATUS_Y = 86;
export const NETWORK_Y = 98;
// 悬停提示（面 / 抽出 / 推入）常留白一行
export const HINT_X = 8;
export const HINT_Y = 110;
// 提示行右端不越过装饰竖线 x=146
export const HINT_MAX_WIDTH = 138;

// ---------- 速率 / 周期 ----------
export const LABEL_X = 8;
export const FIELD_X = 44;
export const FIELD_W = 64;
export const FIELD_H = 16;
export const UNIT_X = 112;
export const UNIT_W = 32;
export const UNIT_H = 16;
export const RATE_FIELD_Y = 122;
export const INTERVAL_FIELD_Y = 142;
export const RATE_LABEL_Y = 126;
export const INTERVAL_LABEL_Y = 146;
export const APPLY_X = 148;
export const APPLY_Y = 132;
export const APPLY_W = 24;
export const APPLY_H = 16;

// ---------- 文本 ----------
export const TITLE_Y = 6;
export const TEXT_INK_HEIGHT = 8;

// ---------- 控件矩形表（全部半开区间 [x, x+w) x [y, y+h)）----------
// 过滤槽只登记「当前可视那一套」（默认抽入）：另一套在屏幕外，不属于窗口内的控件
export const SLOTS = [];

for (const cell of filterSetSlots(0, 0))
	SLOTS.push({ kind: "filter", set: 0, x: cell.x, y: cell.y });

for (let row = 0; row < 3; row++)
	for (let col = 0; col < 9; col++)
		SLOTS.push({ kind: "player", x: PLAYER_SLOT_X + col * SLOT_PITCH, y: PLAYER_SLOT_Y + row * SLOT_PITCH });

for (let col = 0; col < 9; col++)
	SLOTS.push({ kind: "hotbar", x: PLAYER_SLOT_X + col * SLOT_PITCH, y: HOTBAR_SLOT_Y });

// ---------- 世界方向模型（与 ForgeDirection 一一对应）----------
export const DIR_DOWN = 0;
export const DIR_UP = 1;
export const DIR_NORTH = 2;
export const DIR_SOUTH = 3;
export const DIR_WEST = 4;
export const DIR_EAST = 5;

/** 索引 = ForgeDirection ordinal：0 下 1 上 2 北 3 南 4 西 5 东 */
export const DIRECTION_NAMES = ["down", "up", "north", "south", "west", "east"];
/** 与上面一一对应的 offset（x 东 / y 上 / z 南），直接用来做叉积 */
export const DIRECTION_OFFSETS = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];
/** 界面上画的格子缩写（与 en_US.lang 的 side.* 一致） */
export const DIRECTION_LETTERS = ["D", "U", "N", "S", "W", "E"];

/** 角色码：与 GUIMatterInterface.GRID_ROLES 完全同源 */
export const ROLE_UP = 0;
export const ROLE_DOWN = 1;
export const ROLE_LEFT = 2;
export const ROLE_RIGHT = 3;
export const ROLE_FRONT = 4;
export const ROLE_BACK = 5;
export const ROLE_NAMES = ["up", "down", "left", "right", "front", "back"];

/** 主网格 3x3 的角色表（row-major，-1 = 留空）：空 上 空 / 左 前 右 / 后 下 空 */
export const GRID_ROLES = [-1, ROLE_UP, -1, ROLE_LEFT, ROLE_FRONT, ROLE_RIGHT, ROLE_BACK, ROLE_DOWN, -1];
/** 方向选择器 3x3 的世界绝对方向（row-major，-1 = 留空）：空 上 空 / 西 东 北 / 南 下 空 */
export const PICKER_ORDINALS = [-1, DIR_UP, -1, DIR_WEST, DIR_EAST, DIR_NORTH, DIR_SOUTH, DIR_DOWN, -1];

/** 有格子的位置（主网格与选择器共用同一批位置） */
export const ACTIVE_CELL_POSITIONS = [1, 3, 4, 5, 6, 7];
/** 选择器整块底板：正好等于主网格的包围盒，因此它在结构上不可能压到槽位 / 输入框 */
export const PICKER_RECT = { x: GRID_LEFT, y: GRID_TOP, w: GRID_COLUMNS * CELL_PITCH, h: GRID_ROWS * CELL_PITCH };

export const CELLS = [];

for (let position = 0; position < GRID_CELLS; position++)
	CELLS.push({
		position,
		name: "cell." + position,
		x: GRID_LEFT + (position % GRID_COLUMNS) * CELL_PITCH,
		y: GRID_TOP + Math.floor(position / GRID_COLUMNS) * CELL_PITCH,
		w: CELL_SIZE,
		h: CELL_SIZE
	});

export function oppositeDirection(dir) {
	return dir < 0 ? -1 : dir ^ 1;
}

/** 轴对齐单位向量的叉积 a × b；两个输入都是六个基本方向之一，结果也是（或 -1） */
export function crossDirection(a, b) {
	const oa = DIRECTION_OFFSETS[a];
	const ob = DIRECTION_OFFSETS[b];

	if (!oa || !ob)
		return -1;

	const x = oa[1] * ob[2] - oa[2] * ob[1];
	const y = oa[2] * ob[0] - oa[0] * ob[2];
	const z = oa[0] * ob[1] - oa[1] * ob[0];

	if (x !== 0)
		return x > 0 ? DIR_EAST : DIR_WEST;

	if (y !== 0)
		return y > 0 ? DIR_UP : DIR_DOWN;

	if (z !== 0)
		return z > 0 ? DIR_SOUTH : DIR_NORTH;

	return -1;
}

/**
 * 3x3 主网格的朝向推导（与 GUIMatterInterface#refreshGrid 逐步对应）。
 *
 * 向量演算（x 东 / y 上 / z 南）：
 *   前 = 点击面的外法线（玩家右键点开 GUI 的那一面），后 = 前 的反面；
 *   前 是水平面 → 上 = UP，下 = DOWN；
 *   前 是竖直面 → 上 = 玩家水平朝向的反面（玩家朝南看该面 ⇒ 上 = 北），下 = 玩家水平朝向；
 *   左 = 前 × 上。
 * 点击北面（front = NORTH）：上 = UP，下 = DOWN，
 *   左 = (0,0,-1) × (0,1,0) = (0*0 - (-1)*1, (-1)*0 - 0*0, 0*1 - 0*0) = (1,0,0) = EAST，
 *   也就是玩家站在北边朝南看时自己的左手边 = 东（正确，不会再左右颠倒）。
 * 点击顶面（front = UP）且玩家朝南：上 = 北、下 = 南，
 *   左 = (0,1,0) × (0,0,-1) = (1*(-1) - 0*0, 0*0 - 0*(-1), 0*0 - 1*0) = (-1,0,0) = WEST。
 *
 * @returns 长度 9 的数组：位置 → 世界方向 ordinal，-1 表示留空
 */
export function deriveGrid(frontOrdinal, playerFacingOrdinal = DIR_SOUTH) {
	const cells = new Array(GRID_CELLS).fill(-1);
	const front = frontOrdinal >= 0 && frontOrdinal < 6 ? frontOrdinal : DIR_SOUTH;
	const facing = playerFacingOrdinal >= 0 && playerFacingOrdinal < 6 ? playerFacingOrdinal : DIR_SOUTH;
	const up = DIRECTION_OFFSETS[front][1] === 0 ? DIR_UP : oppositeDirection(facing);
	const left = crossDirection(front, up);

	cells[1] = up;
	cells[3] = left;
	cells[4] = front;
	cells[5] = oppositeDirection(left);
	cells[6] = oppositeDirection(front);
	cells[7] = oppositeDirection(up);

	return cells;
}

export const FIELDS = [
	{ name: "rate.field", x: FIELD_X, y: RATE_FIELD_Y, w: FIELD_W, h: FIELD_H },
	{ name: "interval.field", x: FIELD_X, y: INTERVAL_FIELD_Y, w: FIELD_W, h: FIELD_H }
];

export const CONTROLS = [
	{ name: "title", x: LABEL_X, y: TITLE_Y, w: 140, h: TEXT_INK_HEIGHT },
	...CELLS.filter((cell) => ACTIVE_CELL_POSITIONS.includes(cell.position)),
	{ name: "priority.label", x: PRIORITY_LABEL_X, y: PRIORITY_LABEL_Y, w: 52, h: TEXT_INK_HEIGHT },
	{ name: "priority.minus", x: PRIORITY_MINUS_X, y: PRIORITY_BUTTON_Y, w: PRIORITY_BUTTON_SIZE, h: PRIORITY_BUTTON_SIZE },
	{ name: "priority.plus", x: PRIORITY_PLUS_X, y: PRIORITY_BUTTON_Y, w: PRIORITY_BUTTON_SIZE, h: PRIORITY_BUTTON_SIZE },
	{ name: "priority.value", x: PRIORITY_VALUE_X - 4, y: PRIORITY_VALUE_Y, w: 10, h: TEXT_INK_HEIGHT },
	{ name: "status.line", x: STATUS_X, y: STATUS_Y, w: 160, h: TEXT_INK_HEIGHT },
	{ name: "network.line", x: STATUS_X, y: NETWORK_Y, w: 160, h: TEXT_INK_HEIGHT },
	{ name: "hint.line", x: HINT_X, y: HINT_Y, w: HINT_MAX_WIDTH, h: TEXT_INK_HEIGHT },
	{ name: "rate.label", x: LABEL_X, y: RATE_LABEL_Y, w: FIELD_X - LABEL_X, h: TEXT_INK_HEIGHT },
	...FIELDS.slice(0, 1),
	{ name: "rate.unit", x: UNIT_X, y: RATE_FIELD_Y, w: UNIT_W, h: UNIT_H },
	{ name: "interval.label", x: LABEL_X, y: INTERVAL_LABEL_Y, w: FIELD_X - LABEL_X, h: TEXT_INK_HEIGHT },
	...FIELDS.slice(1),
	{ name: "interval.unit", x: UNIT_X, y: INTERVAL_FIELD_Y, w: UNIT_W, h: UNIT_H },
	{ name: "apply", x: APPLY_X, y: APPLY_Y, w: APPLY_W, h: APPLY_H },
	{ name: "filter.tab.input", x: FILTER_TAB_INPUT_X, y: FILTER_TAB_Y, w: FILTER_TAB_W, h: FILTER_TAB_H },
	{ name: "filter.tab.output", x: FILTER_TAB_OUTPUT_X, y: FILTER_TAB_Y, w: FILTER_TAB_W, h: FILTER_TAB_H },
	{ name: "filter.set.label", x: FILTER_SET_LABEL_X, y: FILTER_SET_LABEL_Y, w: FILTER_SET_LABEL_W, h: TEXT_INK_HEIGHT },
	...SLOTS.map((slot, index) => ({ name: "slot." + slot.kind + "." + index, x: slot.x - 1, y: slot.y - 1, w: SLOT_FRAME, h: SLOT_FRAME }))
];

// 文字行（文本墨迹顶行），相邻行间距 >= 12
export const TEXT_ROWS = [TITLE_Y, PRIORITY_LABEL_Y, PRIORITY_VALUE_Y, FILTER_TAB_Y, FILTER_SET_LABEL_Y, STATUS_Y, NETWORK_Y, HINT_Y, RATE_LABEL_Y, INTERVAL_LABEL_Y];

export const BOTTOM_MARGIN = GUI_H - (HOTBAR_SLOT_Y + SLOT_FRAME - 1);
export const HOTBAR_PITCH = HOTBAR_SLOT_Y - (PLAYER_SLOT_Y + 2 * SLOT_PITCH);

// ---------- 面板装饰（贴图唯一的绘制内容）----------
const PLATE = [42, 33, 58];
const FRAME = [14, 10, 21];
const EDGE = [78, 62, 104];
const HAIRLINE = [26, 20, 38];
const HAIRLINE_HI = [62, 49, 84];
const DIVIDER = [54, 43, 75];

export const SEPARATORS_Y = [83, 108, 160];
export const DIVIDER_X = 146;
export const DIVIDER_Y = [110, 150];

function clamp(value) {
	return value < 0 ? 0 : value > 255 ? 255 : Math.round(value);
}

function shade(color, delta) {
	return [clamp(color[0] + delta), clamp(color[1] + delta), clamp(color[2] + delta)];
}

export function panelPixel(x, y) {
	if (x === 0 || y === 0 || x === GUI_W - 1 || y === GUI_H - 1)
		return FRAME;

	if (x === 1 || y === 1 || x === GUI_W - 2 || y === GUI_H - 2)
		return EDGE;

	for (const row of SEPARATORS_Y) {
		if (y === row)
			return HAIRLINE;

		if (y === row + 1)
			return HAIRLINE_HI;
	}

	if ((x === DIVIDER_X || x === DIVIDER_X + 1) && y >= DIVIDER_Y[0] && y <= DIVIDER_Y[1])
		return x === DIVIDER_X ? DIVIDER : HAIRLINE_HI;

	const noise = ((x * 7 + y * 13) % 9) - 4;

	if ((x + y) % 11 === 0)
		return shade(PLATE, 9 + noise);

	if ((x - y + GUI_W) % 13 === 0)
		return shade(PLATE, -7 + noise);

	return shade(PLATE, noise);
}

// ---------- 机器自查 ----------
export function checkLayout() {
	const failures = [];
	const inBounds = (rect) => rect.x >= 2 && rect.y >= 2 && rect.x + rect.w <= GUI_W - 2 && rect.y + rect.h <= GUI_H - 2;
	const overlaps = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

	// 控件两两不相交
	for (let i = 0; i < CONTROLS.length; i++)
		for (let j = i + 1; j < CONTROLS.length; j++) {
			const a = CONTROLS[i], b = CONTROLS[j];

			if (overlaps(a, b))
				failures.push("控件相交 " + a.name + "(" + a.x + "," + a.y + "," + a.w + "," + a.h + ") 与 " + b.name + "(" + b.x + "," + b.y + "," + b.w + "," + b.h + ")");
		}

	// 控件不越界
	for (const control of CONTROLS)
		if (!inBounds(control))
			failures.push("控件越界 " + control.name + "(" + control.x + "," + control.y + "," + control.w + "," + control.h + ")");

	// 装饰线与任何控件都不重合（否则看起来像框线穿控件）
	for (const row of SEPARATORS_Y)
		for (const dy of [0, 1])
			for (const control of CONTROLS)
				if (control.y <= row + dy && row + dy < control.y + control.h)
					failures.push("装饰线 y=" + (row + dy) + " 穿过控件 " + control.name);

	for (const dx of [DIVIDER_X, DIVIDER_X + 1])
		for (let y = DIVIDER_Y[0]; y <= DIVIDER_Y[1]; y++)
			for (const control of CONTROLS)
				if (control.x <= dx && dx < control.x + control.w && control.y <= y && y < control.y + control.h)
					failures.push("装饰竖线 x=" + dx + ",y=" + y + " 穿过控件 " + control.name);

	// 相邻文字行间距
	for (let i = 1; i < TEXT_ROWS.length; i++)
		if (TEXT_ROWS[i] - TEXT_ROWS[i - 1] < 12)
			failures.push("文字行间距不足 " + TEXT_ROWS[i - 1] + " -> " + TEXT_ROWS[i]);

	// 过滤 3x3 的框原点必须是 61/79/97 与 17/35/53
	const expectedFilterOrigins = [[61, 17], [79, 17], [97, 17], [61, 35], [79, 35], [97, 35], [61, 53], [79, 53], [97, 53]];
	const filterSlots = SLOTS.filter((slot) => slot.kind === "filter");

	filterSlots.forEach((slot, index) => {
		const [ex, ey] = expectedFilterOrigins[index];

		if (slot.x - 1 !== ex || slot.y - 1 !== ey)
			failures.push("过滤槽框原点错位 #" + index + " 期望 (" + ex + "," + ey + ") 实际 (" + (slot.x - 1) + "," + (slot.y - 1) + ")");
	});

	// 背包 / 快捷栏行距必须与过滤 3x3 同一个 18 步进
	if (SLOT_PITCH !== 18)
		failures.push("槽位步进不是 18：" + SLOT_PITCH);

	if (HOTBAR_PITCH !== 18)
		failures.push("快捷栏与背包末行步进不是 18：" + HOTBAR_PITCH);

	// 底部留白：快捷栏框底边到窗口底边 >= 8px（当前 244 - 235 = 9）
	if (BOTTOM_MARGIN < 8)
		failures.push("快捷栏下方留白不足 " + BOTTOM_MARGIN);

	// 悬停提示行不得越过装饰竖线
	if (HINT_X + HINT_MAX_WIDTH > DIVIDER_X)
		failures.push("悬停提示行右端 " + (HINT_X + HINT_MAX_WIDTH) + " 越过装饰竖线 x=" + DIVIDER_X);

	// 主网格必须正好铺满选择器底板，而且格子之间不重叠、不越出底板
	if (PICKER_RECT.w !== GRID_COLUMNS * CELL_PITCH || PICKER_RECT.h !== GRID_ROWS * CELL_PITCH)
		failures.push("选择器底板尺寸 " + PICKER_RECT.w + "x" + PICKER_RECT.h + " 与 3x3 格子范围不符");

	if (!inBounds(PICKER_RECT))
		failures.push("选择器底板越界 " + JSON.stringify(PICKER_RECT));

	// 选择器盖在主网格上：除 9 个格子以外，它不许碰到任何控件（槽位 / 输入框 / 按钮 / 文字行）
	for (const control of CONTROLS) {
		if (control.name.startsWith("cell."))
			continue;

		if (overlaps(PICKER_RECT, control))
			failures.push("选择器底板压在控件 " + control.name + "(" + control.x + "," + control.y + "," + control.w + "," + control.h + ") 上");
	}

	// 6 个有格子的位置必须正好落在选择器底板内，且互不重叠
	const activeCells = CELLS.filter((cell) => ACTIVE_CELL_POSITIONS.includes(cell.position));

	if (activeCells.length !== 6)
		failures.push("有格子的位置不是 6 个：" + activeCells.length);

	for (const cell of activeCells)
		if (cell.x < PICKER_RECT.x || cell.y < PICKER_RECT.y || cell.x + cell.w > PICKER_RECT.x + PICKER_RECT.w || cell.y + cell.h > PICKER_RECT.y + PICKER_RECT.h)
			failures.push("格子越出选择器底板 " + cell.name + "(" + cell.x + "," + cell.y + ")");

	// 角色表 / 选择器方向表必须各用满 6 个方向，且留空位置一致
	for (const [name, table, expected] of [["GRID_ROLES", GRID_ROLES, 6], ["PICKER_ORDINALS", PICKER_ORDINALS, 6]]) {
		const used = table.filter((value) => value >= 0);

		if (used.length !== expected)
			failures.push(name + " 有值的位置不是 " + expected + " 个：" + used.length);

		if (new Set(used).size !== expected)
			failures.push(name + " 里出现重复方向：" + used.join(","));

		for (let position = 0; position < GRID_CELLS; position++) {
			const active = ACTIVE_CELL_POSITIONS.includes(position);

			if (active && table[position] < 0)
				failures.push(name + " 位置 " + position + " 应该有值");

			if (!active && table[position] >= 0)
				failures.push(name + " 位置 " + position + " 应该留空");
		}
	}

	// 选择器的 6 个方向必须正好是 ForgeDirection 的六个方向
	const pickerSet = new Set(PICKER_ORDINALS.filter((value) => value >= 0));

	for (let dir = 0; dir < 6; dir++)
		if (!pickerSet.has(dir))
			failures.push("选择器缺少方向 " + DIRECTION_NAMES[dir]);

	// ---------- 过滤表两套：坐标表齐全，且同一时刻只有一套在可视区 ----------
	if (FILTER_SLOT_CELLS.length !== FILTER_SET_SIZE)
		failures.push("过滤表格数不是 " + FILTER_SET_SIZE + "：" + FILTER_SLOT_CELLS.length);

	if (FILTER_SLOT_COUNT !== FILTER_SET_SIZE * FILTER_SET_COUNT)
		failures.push("过滤槽总数 " + FILTER_SLOT_COUNT + " != 每套 " + FILTER_SET_SIZE + " x " + FILTER_SET_COUNT);

	const visibleSlots = (slots) => slots.filter((slot) => slot.y >= 0 && slot.y + SLOT_FRAME <= GUI_H && slot.x >= 0 && slot.x + SLOT_FRAME <= GUI_W);

	for (const active of [0, 1]) {
		const table = filterSlotTable(active);

		for (let index = 0; index < FILTER_SETS.length; index++) {
			const name = FILTER_SETS[index];
			const slots = table[name];
			const visible = visibleSlots(slots);

			if (slots.length !== FILTER_SET_SIZE)
				failures.push("过滤表 " + name + " 的槽位不是 " + FILTER_SET_SIZE + " 个：" + slots.length);

			if (index === active && visible.length !== FILTER_SET_SIZE)
				failures.push("激活的过滤表 " + name + "（activeSet=" + active + "）有 " + visible.length + " 格在可视区（应为 " + FILTER_SET_SIZE + "）");

			if (index !== active && visible.length !== 0)
				failures.push("不活跃的过滤表 " + name + "（activeSet=" + active + "）还有 " + visible.length + " 格留在可视区");
		}

		// 可视区里任意两格都不许重叠：两套同坐标，重叠就意味着两套同时可点
		const onScreen = [...table.input, ...table.output].filter((slot) => slot.y >= 0);

		for (let i = 0; i < onScreen.length; i++)
			for (let j = i + 1; j < onScreen.length; j++) {
				const a = onScreen[i], b = onScreen[j];

				if (overlaps({ x: a.x, y: a.y, w: SLOT_FRAME, h: SLOT_FRAME }, { x: b.x, y: b.y, w: SLOT_FRAME, h: SLOT_FRAME }))
					failures.push("可视区里的过滤槽互相重叠（activeSet=" + active + "，" + a.x + "," + a.y + " 与 " + b.x + "," + b.y + "）");
			}
	}

	// 两套的 9 格必须共用同一组横向排布，切换只换 Y 基准
	const inputSet = filterSetSlots(0, 0);
	const outputSet = filterSetSlots(1, 0);

	for (let i = 0; i < FILTER_SET_SIZE; i++) {
		if (inputSet[i].x !== outputSet[i].x)
			failures.push("两套过滤表第 " + i + " 格的 X 不一致：抽入 " + inputSet[i].x + " / 推出 " + outputSet[i].x);

		if (outputSet[i].y >= 0)
			failures.push("默认没激活的推出表第 " + i + " 格没有挪出屏幕：" + outputSet[i].y);
	}

	// 标签按钮与「正在编辑」小字必须落在过滤槽右侧的空白列里，不许压槽位
	const filterExtras = [
		{ name: "过滤标签-抽入", x: FILTER_TAB_INPUT_X, y: FILTER_TAB_Y, w: FILTER_TAB_W, h: FILTER_TAB_H },
		{ name: "过滤标签-推出", x: FILTER_TAB_OUTPUT_X, y: FILTER_TAB_Y, w: FILTER_TAB_W, h: FILTER_TAB_H },
		{ name: "过滤编辑小字", x: FILTER_SET_LABEL_X, y: FILTER_SET_LABEL_Y, w: FILTER_SET_LABEL_W, h: TEXT_INK_HEIGHT }
	];

	for (const extra of filterExtras)
		for (const cell of inputSet)
			if (overlaps(extra, { x: cell.x - 1, y: cell.y - 1, w: SLOT_FRAME, h: SLOT_FRAME }))
				failures.push(extra.name + " 压在过滤槽 (" + cell.x + "," + cell.y + ") 上");

	return failures;
}

// ---------- MC 1.7.10 默认字体宽度表（ascii.png 的 glyph 宽度，含 1px 字距）----------
const NARROW = { i: 2, "!": 2, ".": 2, ",": 2, ":": 2, ";": 2, "'": 3, "|": 2, l: 3, " ": 4, I: 4, "[": 4, "]": 4, t: 4, f: 5, k: 5, "(": 5, ")": 5, "{": 5, "}": 5, '"': 5, "*": 5, "<": 5, ">": 5, "@": 7 };

export function charWidth(ch) {
	const code = ch.codePointAt(0);

	// CJK / 全角：unicode 字体页里每字 9px
	if (code >= 0x2E80)
		return 9;

	if (NARROW[ch] !== undefined)
		return NARROW[ch];

	return 6;
}

export function textWidth(text) {
	let width = 0;

	for (const ch of text)
		width += charWidth(ch);

	return width;
}

// ---------- PNG 编码（无第三方依赖）----------
const CRC_TABLE = (() => {
	const table = new Int32Array(256);

	for (let n = 0; n < 256; n++) {
		let c = n;

		for (let k = 0; k < 8; k++)
			c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;

		table[n] = c;
	}

	return table;
})();

function crc32(buffer) {
	let c = 0xFFFFFFFF;

	for (let i = 0; i < buffer.length; i++)
		c = CRC_TABLE[(c ^ buffer[i]) & 0xFF] ^ (c >>> 8);

	return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length, 0);
	const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body), 0);
	return Buffer.concat([length, body, crc]);
}

export function encodePng(width, height, pixelFn) {
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header[8] = 8;
	header[9] = 6;

	const raw = Buffer.alloc((width * 4 + 1) * height);

	for (let y = 0; y < height; y++) {
		const rowStart = y * (width * 4 + 1);
		raw[rowStart] = 0;

		for (let x = 0; x < width; x++) {
			const color = pixelFn(x, y);
			const offset = rowStart + 1 + x * 4;
			raw[offset] = color[0];
			raw[offset + 1] = color[1];
			raw[offset + 2] = color[2];
			raw[offset + 3] = color.length > 3 ? color[3] : 255;
		}
	}

	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
		chunk("IHDR", header),
		chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
		chunk("IEND", Buffer.alloc(0))
	]);
}
