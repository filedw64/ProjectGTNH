// 物质接口 GUI 自绘预览：node tools/preview_matter_interface_gui.mjs
//
// 按 GUIMatterInterface 的真实绘制顺序合成最终画面：
//   1) 面板贴图（与 gen_matter_interface_gui_texture.mjs 同一个 panelPixel）
//   2) 45 个槽位框：槽位坐标 - 1 的 18x18 外框（同 Java drawSlotFrames）
//   3) 3x3 主网格：1px 边框 + 模式底色 + 世界方向缩写（同 Java drawGridBackground / drawGridLabels）
//      或方向选择器：整块底板盖住主网格 + 6 个世界方向格子（同 Java drawPickerBackground / drawPickerLabels）
//   4) 2 个输入框底框：GuiTextField enableBackgroundDrawing=true 的真实形态
//   5) 按钮底色与文字
// 文字用自带的 3x5 像素字体画（en_US 文案），所以预览图上的每个格子都能直接读出世界方向。
// 输出：tools/preview_gui.png（点击北面）/ tools/preview_gui_picker.png（抽出方向选择器）
//       以及各自的 x3 最近邻放大版。
import fs from "node:fs";
import path from "node:path";
import {
	GUI_W, GUI_H, SLOTS, CELLS, FIELDS, CELL_SIZE, SLOT_FRAME, TEXT_INK_HEIGHT, ACTIVE_CELL_POSITIONS,
	GRID_LEFT, GRID_TOP, GRID_COLUMNS, GRID_ROWS, CELL_PITCH, GRID_ROLES, PICKER_ORDINALS, PICKER_RECT,
	LABEL_X, TITLE_Y, PRIORITY_LABEL_X, PRIORITY_LABEL_Y, PRIORITY_MINUS_X, PRIORITY_PLUS_X,
	PRIORITY_BUTTON_Y, PRIORITY_BUTTON_SIZE, PRIORITY_VALUE_X, PRIORITY_VALUE_Y,
	STATUS_X, STATUS_Y, NETWORK_Y, HINT_X, HINT_Y, RATE_LABEL_Y, INTERVAL_LABEL_Y,
	UNIT_X, UNIT_W, UNIT_H, APPLY_X, APPLY_Y, APPLY_W, APPLY_H, RATE_FIELD_Y, INTERVAL_FIELD_Y,
	FILTER_TAB_INPUT_X, FILTER_TAB_OUTPUT_X, FILTER_TAB_Y, FILTER_TAB_W, FILTER_TAB_H, FILTER_TAB_MARK_H,
	FILTER_SET_LABEL_X, FILTER_SET_LABEL_Y, filterSetSlots,
	DIRECTION_LETTERS, DIRECTION_NAMES, DIR_UP, DIR_DOWN, DIR_NORTH, DIR_EAST, DIR_WEST, ROLE_NAMES,
	deriveGrid, encodePng, panelPixel, charWidth
} from "./matter_interface_layout.mjs";

const COLOR_TEXT = [0xE8, 0xE8, 0xE8];
const COLOR_WARN = [0xFF, 0xC0, 0x4A];
const COLOR_HOVER = [0xFF, 0xFF, 0xA0];
const COLOR_HINT = [0x8A, 0x8A, 0x8A];
const COLOR_SLOT_SHADOW = [0x12, 0x0C, 0x1C];
const COLOR_SLOT_FILL = [0x24, 0x1C, 0x33];
const COLOR_SLOT_LIGHT = [0x3E, 0x31, 0x57];
const COLOR_CELL_BORDER = [0x14, 0x11, 0x1C];
const COLOR_FACE_OFF = [0x55, 0x55, 0x55];
const COLOR_FACE_EXTRACT = [0x3F, 0xBF, 0x5F];
const COLOR_FACE_INSERT = [0xE0, 0x8A, 0x2B];
const COLOR_FRONT_MARK = [0xFF, 0xE0, 0x70];
const COLOR_PICKER_PANEL = [0x12, 0x0C, 0x1C];
const COLOR_PICKER_CELL = [0x2C, 0x23, 0x40];
const COLOR_PICKER_BORDER = [0x6A, 0x57, 0x94];
const COLOR_PICKER_TEXT = [0x19, 0x10, 0x24];
const COLOR_FIELD_RING = [0xA0, 0xA0, 0xA0];
const COLOR_FIELD_INNER = [0x00, 0x00, 0x00];
const COLOR_BUTTON = [0x8B, 0x8B, 0x8B];
const COLOR_BUTTON_DARK = [0x2B, 0x2B, 0x2B];

// ---------- 3x5 像素字体（预览专用：只为让预览图上的方向 / 文案可读）----------
const FONT = {
	A: "010101111101101", B: "110101110101110", C: "011100100100011", D: "110101101101110",
	E: "111100110100111", F: "111100110100100", G: "011100101101011", H: "101101111101101",
	I: "111010010010111", J: "001001001101010", K: "101101110101101", L: "100100100100111",
	M: "101111111101101", N: "101111101101101", O: "010101101101010", P: "110101110100100",
	Q: "010101101110011", R: "110101110101101", S: "011100010001110", T: "111010010010010",
	U: "101101101101111", V: "101101101101010", W: "101101111111101", X: "101101010101101",
	Y: "101101010010010", Z: "111001010100111",
	0: "111101101101111", 1: "010110010010111", 2: "110001010100111", 3: "110001010001110",
	4: "101101111001001", 5: "111100110001110", 6: "011100111101111", 7: "111001010010010",
	8: "111101111101111", 9: "111101111001110",
	".": "000000000000010", ",": "000000000010100", ":": "000010000010000", ";": "000010000010100",
	"(": "001010010010001", ")": "100010010010100", "-": "000000111000000", "+": "000010111010000",
	"/": "001001010100100", "=": "000111000111000", "%": "101001010100101", "!": "010010010000010",
	"?": "110001010000010", "'": "010010000000000", " ": "000000000000000"
};

const canvas = new Uint8Array(GUI_W * GUI_H * 4);

function plot(x, y, color) {
	if (x < 0 || y < 0 || x >= GUI_W || y >= GUI_H)
		return;

	const index = (y * GUI_W + x) * 4;
	canvas[index] = color[0];
	canvas[index + 1] = color[1];
	canvas[index + 2] = color[2];
	canvas[index + 3] = 255;
}

function fill(x, y, w, h, color) {
	for (let dy = 0; dy < h; dy++)
		for (let dx = 0; dx < w; dx++)
			plot(x + dx, y + dy, color);
}

function frame(x, y, w, h, color) {
	for (let dx = 0; dx < w; dx++) {
		plot(x + dx, y, color);
		plot(x + dx, y + h - 1, color);
	}

	for (let dy = 0; dy < h; dy++) {
		plot(x, y + dy, color);
		plot(x + w - 1, y + dy, color);
	}
}

/** 3x5 像素字体：字宽取 layout 的 MC 字体宽度表，字形在其内左对齐 + 1px 左边距 */
function drawText(x, y, value, color) {
	let cursor = x;

	for (const ch of value) {
		const advance = charWidth(ch);
		const glyph = FONT[ch] || FONT[ch.toUpperCase()];

		if (glyph) {
			for (let row = 0; row < 5; row++)
				for (let col = 0; col < 3; col++)
					if (glyph[row * 3 + col] === "1")
						plot(cursor + 1 + col, y + 2 + row, color);
		}
		else if (ch !== " ")
			fill(cursor + 1, y + 2, Math.max(1, advance - 1), 5, color);

		cursor += advance;
	}
}

function textWidthOf(value) {
	let width = 0;

	for (const ch of value)
		width += charWidth(ch);

	return width;
}

function drawCenteredCellText(position, label, color) {
	const cell = CELLS[position];

	drawText(cell.x + Math.floor((CELL_SIZE - textWidthOf(label)) / 2), cell.y + Math.floor((CELL_SIZE - 9) / 2), label, color);
}

// ---------- 场景 ----------

function hasValue(sides, face) {
	return sides[face] >= 0;
}

function faceColor(scene, face) {
	const extract = hasValue(scene.extract, face);
	const insert = hasValue(scene.insert, face);

	if (extract && insert)
		return COLOR_FACE_EXTRACT;

	if (extract)
		return COLOR_FACE_EXTRACT;

	if (insert)
		return COLOR_FACE_INSERT;

	return COLOR_FACE_OFF;
}

function drawInnerFrame(x, y, color) {
	const inner = x + 2;
	const innerY = y + 2;
	const innerEnd = x + CELL_SIZE - 2;
	const innerEndY = y + CELL_SIZE - 2;

	fill(inner, innerY, innerEnd - inner, 1, color);
	fill(inner, innerEndY - 1, innerEnd - inner, 1, color);
	fill(inner, innerY + 1, 1, innerEndY - innerY - 2, color);
	fill(innerEnd - 1, innerY + 1, 1, innerEndY - innerY - 2, color);
}

function drawGrid(scene) {
	for (const position of ACTIVE_CELL_POSITIONS) {
		const face = scene.cells[position];

		if (face < 0)
			continue;

		const cell = CELLS[position];

		fill(cell.x, cell.y, CELL_SIZE, CELL_SIZE, position === 4 ? COLOR_FRONT_MARK : COLOR_CELL_BORDER);
		fill(cell.x + 1, cell.y + 1, CELL_SIZE - 2, CELL_SIZE - 2, faceColor(scene, face));

		if (hasValue(scene.extract, face) && hasValue(scene.insert, face))
			drawInnerFrame(cell.x, cell.y, COLOR_FACE_INSERT);
	}
}

function drawGridLabels(scene) {
	for (const position of ACTIVE_CELL_POSITIONS) {
		const face = scene.cells[position];

		if (face < 0)
			continue;

		drawCenteredCellText(position, DIRECTION_LETTERS[face], position === scene.hoverCell ? COLOR_HOVER : COLOR_TEXT);
	}
}

function drawPicker(scene) {
	fill(PICKER_RECT.x, PICKER_RECT.y, PICKER_RECT.w, PICKER_RECT.h, COLOR_PICKER_PANEL);

	const face = scene.cells[scene.picker.cell];
	const current = scene.picker.extract ? scene.extract[face] : scene.insert[face];
	const accent = scene.picker.extract ? COLOR_FACE_EXTRACT : COLOR_FACE_INSERT;

	for (const position of ACTIVE_CELL_POSITIONS) {
		const dir = PICKER_ORDINALS[position];
		const cell = CELLS[position];

		fill(cell.x, cell.y, CELL_SIZE, CELL_SIZE, COLOR_PICKER_BORDER);
		fill(cell.x + 1, cell.y + 1, CELL_SIZE - 2, CELL_SIZE - 2, dir === current ? accent : COLOR_PICKER_CELL);
	}
}

function drawPickerLabels(scene) {
	const face = scene.cells[scene.picker.cell];
	const current = scene.picker.extract ? scene.extract[face] : scene.insert[face];

	for (const position of ACTIVE_CELL_POSITIONS) {
		const dir = PICKER_ORDINALS[position];

		drawCenteredCellText(position, DIRECTION_LETTERS[dir], dir === current ? COLOR_PICKER_TEXT : COLOR_TEXT);
	}
}

function button(x, y, w, h, label) {
	frame(x, y, w, h, COLOR_BUTTON_DARK);
	fill(x + 1, y + 1, w - 2, h - 2, COLOR_BUTTON);
	drawText(x + Math.floor((w - textWidthOf(label)) / 2), y + Math.floor((h - 9) / 2), label, COLOR_TEXT);
}

/** 过滤表标签按钮：激活的那一个用高亮字色 + 底部高亮线（同 Java drawFilterTabMarks） */
function tabButton(x, label, active) {
	button(x, FILTER_TAB_Y, FILTER_TAB_W, FILTER_TAB_H, label);

	if (active)
		fill(x, FILTER_TAB_Y + FILTER_TAB_H - FILTER_TAB_MARK_H, FILTER_TAB_W, FILTER_TAB_MARK_H, COLOR_HOVER);
	else
		drawText(x + Math.floor((FILTER_TAB_W - textWidthOf(label)) / 2), FILTER_TAB_Y + Math.floor((FILTER_TAB_H - 9) / 2), label, COLOR_HINT);
}

/** 幽灵标记：只画在可视那一套的槽位里（另一套整块在屏幕外，画了也只会落在窗口外） */
function drawFilterMarks(scene) {
	if (!scene.marks)
		return;

	for (const mark of scene.marks) {
		// 取「激活那一套」的坐标：另一个下标算出来必然在屏幕外，会被下面的 y < 0 跳过
		const slot = filterSetSlots(scene.activeFilterSet, scene.activeFilterSet)[mark.slot];

		if (!slot || slot.y < 0)
			continue;

		fill(slot.x + 4, slot.y + 4, 8, 8, mark.color);
	}
}

function renderScene(scene, outPath) {
	for (let y = 0; y < GUI_H; y++)
		for (let x = 0; x < GUI_W; x++) {
			const color = panelPixel(x, y);
			const index = (y * GUI_W + x) * 4;
			canvas[index] = color[0];
			canvas[index + 1] = color[1];
			canvas[index + 2] = color[2];
			canvas[index + 3] = 255;
		}

	// 1) 45 个槽位框
	for (const slot of SLOTS) {
		const x = slot.x - 1;
		const y = slot.y - 1;

		fill(x, y, SLOT_FRAME, SLOT_FRAME, COLOR_SLOT_SHADOW);
		fill(x + 1, y + 1, SLOT_FRAME - 2, SLOT_FRAME - 2, COLOR_SLOT_FILL);
		fill(x + 1, y + SLOT_FRAME - 2, SLOT_FRAME - 2, 1, COLOR_SLOT_LIGHT);
		fill(x + SLOT_FRAME - 2, y + 1, 1, SLOT_FRAME - 2, COLOR_SLOT_LIGHT);
	}

	// 1b) 幽灵过滤标记（可视那一套的槽位里画方块）
	drawFilterMarks(scene);

	// 2) 主网格或方向选择器
	if (scene.picker)
		drawPicker(scene);
	else
		drawGrid(scene);

	// 3) 输入框底框 + 空态提示 0
	for (const field of FIELDS) {
		frame(field.x - 1, field.y - 1, field.w + 2, field.h + 2, COLOR_FIELD_RING);
		fill(field.x, field.y, field.w, field.h, COLOR_FIELD_INNER);
		drawText(field.x + 4, field.y + Math.floor((field.h - TEXT_INK_HEIGHT) / 2), "0", COLOR_HINT);
	}

	// 4) 按钮
	button(PRIORITY_MINUS_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_SIZE, PRIORITY_BUTTON_SIZE, "-");
	button(PRIORITY_PLUS_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_SIZE, PRIORITY_BUTTON_SIZE, "+");
	button(UNIT_X, RATE_FIELD_Y, UNIT_W, UNIT_H, "/tick");
	button(UNIT_X, INTERVAL_FIELD_Y, UNIT_W, UNIT_H, "/s");
	button(APPLY_X, APPLY_Y, APPLY_W, APPLY_H, "Set");
	// 过滤表标签：抽入 / 推出，只有激活的一个高亮
	tabButton(FILTER_TAB_INPUT_X, "IN", scene.activeFilterSet === 0);
	tabButton(FILTER_TAB_OUTPUT_X, "OUT", scene.activeFilterSet === 1);

	// 5) 文字层
	drawText(LABEL_X, TITLE_Y, scene.title, COLOR_TEXT);

	if (scene.picker)
		drawPickerLabels(scene);
	else
		drawGridLabels(scene);

	drawText(PRIORITY_LABEL_X, PRIORITY_LABEL_Y, "Priority", COLOR_TEXT);
	drawText(PRIORITY_VALUE_X - Math.floor(textWidthOf("5") / 2), PRIORITY_VALUE_Y, "5", COLOR_TEXT);
	drawText(STATUS_X, STATUS_Y, scene.status, scene.statusWarn ? COLOR_WARN : COLOR_TEXT);
	drawText(STATUS_X, NETWORK_Y, scene.network, scene.networkDown ? COLOR_HINT : COLOR_TEXT);

	if (scene.hint)
		drawText(HINT_X, HINT_Y, scene.hint, COLOR_HOVER);

	drawText(LABEL_X, RATE_LABEL_Y, "Rate", COLOR_TEXT);
	drawText(LABEL_X, INTERVAL_LABEL_Y, "Period", COLOR_TEXT);

	// 过滤区旁的小字：现在编辑的是哪一套
	drawText(FILTER_SET_LABEL_X, FILTER_SET_LABEL_Y, scene.activeFilterSet === 1 ? "Edit: Out" : "Edit: In", COLOR_HINT);

	fs.mkdirSync(path.dirname(outPath), { recursive: true });
	fs.writeFileSync(outPath, encodePng(GUI_W, GUI_H, pixel));

	// 3 倍最近邻放大版：报告里用来肉眼核对居中、左 / 右有没有搞反（不是游戏内资源）
	const scaled = outPath.replace(/\.png$/, "_x3.png");
	const SCALE = 3;

	fs.writeFileSync(scaled, encodePng(GUI_W * SCALE, GUI_H * SCALE, (x, y) => pixel(Math.floor(x / SCALE), Math.floor(y / SCALE))));

	return { outPath, scaled };
}

function pixel(x, y) {
	const index = (y * GUI_W + x) * 4;
	return [canvas[index], canvas[index + 1], canvas[index + 2], canvas[index + 3]];
}

// ---------- 两个场景 ----------
// 面索引 = ForgeDirection ordinal：0 下 1 上 2 北 3 南 4 西 5 东
const UNSET = -1;
const sceneExtract = [UNSET, DIR_DOWN, DIR_UP, UNSET, UNSET, UNSET];
const sceneInsert = [UNSET, UNSET, DIR_DOWN, UNSET, UNSET, DIR_WEST];

const gridScene = {
	title: "Item Matter Interface",
	cells: deriveGrid(DIR_NORTH),
	extract: sceneExtract,
	insert: sceneInsert,
	// 鼠标停在『前』格子（位置 4 = 点击的北面）上：悬停提示行显示 面 / 抽 / 推
	hoverCell: 4,
	picker: null,
	// 抽入模式：标签停在「抽入」，抽入表里标了 2 格（绿块 = 抽入表的幽灵标记）
	activeFilterSet: 0,
	marks: [{ slot: 0, color: COLOR_FACE_EXTRACT }, { slot: 4, color: COLOR_FACE_EXTRACT }],
	status: "Container connected",
	statusWarn: false,
	network: "Network: 12 pipes",
	networkDown: false,
	hint: "front(N) out U in D"
};

const pickerScene = {
	title: "Item Matter Interface",
	cells: deriveGrid(DIR_NORTH),
	extract: sceneExtract,
	insert: sceneInsert,
	hoverCell: -1,
	// 左键点了『前』格子 → 打开『抽出方向』选择器；当前抽出方向是 UP，所以选择器的「上」格高亮
	picker: { cell: 4, extract: true },
	activeFilterSet: 0,
	marks: [{ slot: 0, color: COLOR_FACE_EXTRACT }, { slot: 4, color: COLOR_FACE_EXTRACT }],
	status: "Container connected",
	statusWarn: false,
	network: "Network: 12 pipes",
	networkDown: false,
	hint: "Out: click=set, again=clear"
};

// 推出模式：标签切到「推出」，同一组 3x3 坐标上是推出表的幽灵标记（橙块）
const outputScene = {
	title: "Item Matter Interface",
	cells: deriveGrid(DIR_NORTH),
	extract: sceneExtract,
	insert: sceneInsert,
	hoverCell: -1,
	picker: null,
	activeFilterSet: 1,
	marks: [{ slot: 2, color: COLOR_FACE_INSERT }, { slot: 8, color: COLOR_FACE_INSERT }],
	status: "Container connected",
	statusWarn: false,
	network: "Network: 12 pipes",
	networkDown: false,
	hint: "Editing the OUT filter set"
};

const grid = renderScene(gridScene, path.join("tools", "preview_gui.png"));
const picker = renderScene(pickerScene, path.join("tools", "preview_gui_picker.png"));
const output = renderScene(outputScene, path.join("tools", "preview_gui_filter_out.png"));

console.log("scene 1 (抽入模式 / 点击北面)   -> " + grid.outPath + " / " + grid.scaled);
console.log("scene 2 (抽入模式 / 方向选择器) -> " + picker.outPath + " / " + picker.scaled);
console.log("scene 3 (推出模式)             -> " + output.outPath + " / " + output.scaled);
console.log("");
console.log("cell -> 世界方向（点击北面）：");

for (const [position, face] of gridScene.cells.entries())
	if (face >= 0)
		console.log("  位置 " + position + "  角色 " + ROLE_NAMES[GRID_ROLES[position]].padEnd(6) + "  " + DIRECTION_NAMES[face]);

console.log("");
console.log("选择器 位置 -> 世界方向（严格按规格）：");

for (const [position, dir] of PICKER_ORDINALS.entries())
	if (dir >= 0)
		console.log("  位置 " + position + "  " + DIRECTION_NAMES[dir]);

console.log("");
console.log("格子状态：上 = 绿(只抽出) / 前(北) = 绿底+橙框(双向) / 东 = 橙(只推入) / 其余灰");
console.log("");
console.log("过滤表：scene 1/2 = 抽入（标签 IN 高亮，绿块 = 抽入表标记），scene 3 = 推出（标签 OUT 高亮，橙块 = 推出表标记）");
console.log("两套共用同一组 3x3 坐标，不活跃的一套在 y=" + (-2000) + "，因此同一时刻只有 9 格可点可看");
