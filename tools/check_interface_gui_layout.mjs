// 物质接口 GUI 一致性检查：node tools/check_interface_gui_layout.mjs
//
// 五件事：
//   1) 几何：控件两两不相交 / 不越界 / 文字行距 / 装饰线不穿控件 / 过滤 3x3 框原点 / 方向选择器底板不压任何控件 /
//      过滤表两套坐标表齐全且同一时刻只有一套在可视区（layout 模块自带）
//   2) 过滤表分套：tile 的两套物品 / 流体表、端口按 mode 选表、NBT 两把键、同步包 18 格、容器默认把推出表摆到屏幕外
//   2) 源码同源：正则解析 GUIMatterInterface.java / ContainerMatterInterface.java 的常量与二维表，
//      逐个与 layout 模块比对 —— Java 与脚本只要有一边改了数字或方向表，这里立刻红
//   3) 朝向推导：硬编码「点击北面」「点击顶面（玩家朝南）」两张期望表，逐格比对 deriveGrid()
//   4) 文案放得下：把 lang 里的真实字符串按 MC 字体宽度表量一遍，断言每个控件都能装下自己的文字
import fs from "node:fs";
import path from "node:path";
import {
	GUI_W, GUI_H, SLOT_PITCH, SLOT_FRAME, FILTER_SLOT_X, FILTER_SLOT_Y, PLAYER_SLOT_X, PLAYER_SLOT_Y, HOTBAR_SLOT_Y,
	GRID_LEFT, GRID_TOP, CELL_SIZE, CELL_PITCH, GRID_COLUMNS, GRID_ROWS, GRID_CELLS, GRID_FRONT_CELL,
	PRIORITY_LABEL_X, PRIORITY_LABEL_Y, PRIORITY_MINUS_X, PRIORITY_PLUS_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_SIZE,
	PRIORITY_VALUE_X, PRIORITY_VALUE_Y, STATUS_X, STATUS_Y, NETWORK_Y, HINT_X, HINT_Y, HINT_MAX_WIDTH, LABEL_X,
	FIELD_X, FIELD_W, FIELD_H,
	UNIT_X, UNIT_W, UNIT_H, RATE_FIELD_Y, INTERVAL_FIELD_Y, RATE_LABEL_Y, INTERVAL_LABEL_Y,
	APPLY_X, APPLY_Y, APPLY_W, APPLY_H, TITLE_Y, TEXT_INK_HEIGHT, CONTROLS, PICKER_RECT, ACTIVE_CELL_POSITIONS,
	GRID_ROLES, PICKER_ORDINALS, ROLE_NAMES, DIRECTION_NAMES, DIR_DOWN, DIR_UP, DIR_NORTH, DIR_SOUTH, DIR_WEST, DIR_EAST,
	FILTER_SET_SIZE, FILTER_SLOT_COUNT, FILTER_SET_COUNT, FILTER_OFFSCREEN_Y, FILTER_TAB_INPUT_X, FILTER_TAB_OUTPUT_X,
	FILTER_TAB_Y, FILTER_TAB_W, FILTER_TAB_H, FILTER_TAB_MARK_H, FILTER_SET_LABEL_X, FILTER_SET_LABEL_Y,
	FILTER_SET_LABEL_W, filterSlotTable,
	deriveGrid, checkLayout, textWidth
} from "./matter_interface_layout.mjs";

const GUI_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "gui", "transport", "GUIMatterInterface.java");
const CONTAINER_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "container", "transport", "ContainerMatterInterface.java");
const TILE_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "tiles", "transport", "TileMatterInterface.java");
const BLOCK_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "blocks", "transport", "BlockMatterInterface.java");
const SYNC_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "network", "packets", "transport", "MatterInterfaceSyncPKT.java");
const ITEM_TILE_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "tiles", "transport", "TileMatterInterfaceItem.java");
const FLUID_TILE_JAVA = path.join("src", "main", "java", "moze_intel", "projecte", "gameObjs", "tiles", "transport", "TileMatterInterfaceFluid.java");

const failures = checkLayout().slice();

// ---------- 1) 解析 Java 常量 ----------

function readSource(file) {
	if (!fs.existsSync(file)) {
		failures.push("找不到源码 " + file);
		return "";
	}

	return fs.readFileSync(file, "utf8");
}

function intConstants(source) {
	const map = new Map();
	const pattern = /(?:private|protected|public)?\s*static\s+final\s+int\s+([A-Z0-9_]+)\s*=\s*(-?\d+)\s*;/g;

	for (const match of source.matchAll(pattern))
		map.set(match[1], Number(match[2]));

	return map;
}

function intArrays(source) {
	const map = new Map();
	const pattern = /(?:private|protected|public)?\s*static\s+final\s+int\[\]\s+([A-Z0-9_]+)\s*=\s*\{([^}]*)\}\s*;/g;

	for (const match of source.matchAll(pattern))
		map.set(match[1], match[2].split(",").map((value) => Number(value.trim())));

	return map;
}

const guiSource = readSource(GUI_JAVA);
const containerSource = readSource(CONTAINER_JAVA);
const tileSource = readSource(TILE_JAVA);
const blockSource = readSource(BLOCK_JAVA);
const syncSource = readSource(SYNC_JAVA);
const itemTileSource = readSource(ITEM_TILE_JAVA);
const fluidTileSource = readSource(FLUID_TILE_JAVA);
const guiInts = intConstants(guiSource);
const guiArrays = intArrays(guiSource);
const containerInts = intConstants(containerSource);
const tileInts = intConstants(tileSource);

const EXPECTED_GUI = {
	GRID_LEFT, GRID_TOP, CELL_SIZE, CELL_PITCH, GRID_COLUMNS, GRID_ROWS, GRID_CELLS, GRID_FRONT_CELL,
	PRIORITY_LABEL_X, PRIORITY_LABEL_Y, PRIORITY_MINUS_X, PRIORITY_PLUS_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_SIZE,
	PRIORITY_VALUE_X, PRIORITY_VALUE_Y, STATUS_X, STATUS_Y, NETWORK_Y, HINT_X, HINT_Y, LABEL_X,
	FIELD_X, FIELD_W, FIELD_H, UNIT_X, UNIT_W, UNIT_H, RATE_FIELD_Y, INTERVAL_FIELD_Y, RATE_LABEL_Y, INTERVAL_LABEL_Y,
	APPLY_X, APPLY_Y, APPLY_W, APPLY_H, TEXT_INK_HEIGHT, SLOT_FRAME,
	// 过滤槽与过滤表标签（两套表共用同一组坐标，靠 Y 基准在「屏内 / 屏外」之间切换）
	FILTER_SLOT_X, FILTER_SLOT_Y, FILTER_SLOT_PITCH: SLOT_PITCH, FILTER_OFFSCREEN_Y,
	FILTER_TAB_INPUT_X, FILTER_TAB_OUTPUT_X, FILTER_TAB_Y, FILTER_TAB_W, FILTER_TAB_H, FILTER_TAB_MARK_H,
	FILTER_SET_LABEL_X, FILTER_SET_LABEL_Y
};

for (const [name, expected] of Object.entries(EXPECTED_GUI)) {
	if (!guiInts.has(name))
		failures.push("GUIMatterInterface 里缺少常量 " + name);
	else if (guiInts.get(name) !== expected)
		failures.push("常量不一致 " + name + "：Java=" + guiInts.get(name) + " 脚本=" + expected);
}

const EXPECTED_ARRAYS = { GRID_ROLES, PICKER_ORDINALS };

for (const [name, expected] of Object.entries(EXPECTED_ARRAYS)) {
	const actual = guiArrays.get(name);

	if (!actual)
		failures.push("GUIMatterInterface 里缺少方向表 " + name);
	else if (actual.length !== expected.length || actual.some((value, index) => value !== expected[index]))
		failures.push("方向表不一致 " + name + "：Java=[" + actual.join(",") + "] 脚本=[" + expected.join(",") + "]");
}

const EXPECTED_CONTAINER = { SLOT_PITCH, FILTER_SLOT_X, FILTER_SLOT_Y, FILTER_OFFSCREEN_Y, PLAYER_SLOT_X, PLAYER_SLOT_Y, HOTBAR_SLOT_Y };

// tile 的两套过滤表：每套 9 格，槽位总表 18 格（IInventory 0..8 抽入 / 9..17 推出）
const EXPECTED_TILE = { FILTER_SET_SIZE, FILTER_SIZE: FILTER_SLOT_COUNT };

for (const [name, expected] of Object.entries(EXPECTED_TILE)) {
	if (!tileInts.has(name))
		failures.push("TileMatterInterface 里缺少常量 " + name);
	else if (tileInts.get(name) !== expected)
		failures.push("常量不一致 " + name + "：Java=" + tileInts.get(name) + " 脚本=" + expected);
}

if (tileInts.get("FILTER_SIZE") !== tileInts.get("FILTER_SET_SIZE") * FILTER_SET_COUNT)
	failures.push("FILTER_SIZE 不等于 FILTER_SET_SIZE x " + FILTER_SET_COUNT);

for (const [name, expected] of Object.entries(EXPECTED_CONTAINER)) {
	if (!containerInts.has(name))
		failures.push("ContainerMatterInterface 里缺少常量 " + name);
	else if (containerInts.get(name) !== expected)
		failures.push("槽位常量不一致 " + name + "：Java=" + containerInts.get(name) + " 脚本=" + expected);
}

// 窗口尺寸
if (!/this\.xSize\s*=\s*176\s*;/.test(guiSource))
	failures.push("GUIMatterInterface 的 xSize 不是 176");

if (!/this\.ySize\s*=\s*244\s*;/.test(guiSource))
	failures.push("GUIMatterInterface 的 ySize 不是 244");

// 槽位公式必须是「常量 + 列 * SLOT_PITCH」
if (!/FILTER_SLOT_X\s*\+\s*col\s*\*\s*SLOT_PITCH/.test(containerSource))
	failures.push("过滤槽不再使用 FILTER_SLOT_X + col * SLOT_PITCH");

if (!/PLAYER_SLOT_X\s*\+\s*col\s*\*\s*SLOT_PITCH/.test(containerSource))
	failures.push("背包 / 快捷栏不再使用 PLAYER_SLOT_X + col * SLOT_PITCH");

// 居中必须用字体实测值，禁止写死 8
if (/\(\s*CELL_SIZE\s*-\s*8\s*\)/.test(guiSource))
	failures.push("格子文字仍在用写死的 (CELL_SIZE - 8) / 2");

if (!/getStringWidth\(/.test(guiSource))
	failures.push("格子文字没有用 getStringWidth 实测字宽");

if (!/FONT_HEIGHT/.test(guiSource))
	failures.push("格子文字没有用 FONT_HEIGHT 实测字高");

// 槽位框必须遍历真实 Slot，而不是另抄一份坐标
if (!/drawSlotFrames/.test(guiSource) || !/inventorySlots\.inventorySlots/.test(guiSource))
	failures.push("槽位框不是遍历 this.inventorySlots.inventorySlots 画出来的");

if (!/xDisplayPosition\s*-\s*1/.test(guiSource) || !/yDisplayPosition\s*-\s*1/.test(guiSource))
	failures.push("槽位框没有用 Slot.xDisplayPosition - 1 / yDisplayPosition - 1 定位");

// 输入框底框必须交给 GuiTextField 自己画
if (!/setEnableBackgroundDrawing\(true\)/.test(guiSource))
	failures.push("输入框没有启用 GuiTextField 自带底框");

// 3x3 网格 / 选择器共用一个命中判定：格子坐标只能从 cellX/cellY 来
if (!/private static int cellX\(/.test(guiSource) || !/private static int cellY\(/.test(guiSource))
	failures.push("格子坐标没有集中到 cellX/cellY");

if (!/pickerPosAt\(/.test(guiSource) || !/chooseDirection\(/.test(guiSource))
	failures.push("方向选择器缺少命中判定 pickerPosAt / 选中处理 chooseDirection");

// 左键 = 抽出选择器，右键 = 推入选择器
if (!/pickerExtract\s*=\s*mouseButton\s*==\s*0/.test(guiSource))
	failures.push("主格子没有按鼠标键区分抽出 / 推入选择器");

// 再点一次当前方向 = 清除
if (!/current\s*==\s*dir\s*\?\s*TileMatterInterface\.SIDE_UNSET/.test(guiSource))
	failures.push("选择器没有实现「再点一次当前方向 = 清除」");

// 发包必须走 MatterInterfaceButtonPKT.direction
if (!/MatterInterfaceButtonPKT\.direction\(/.test(guiSource))
	failures.push("GUI 没有用 MatterInterfaceButtonPKT.direction 发包");

// 数据模型：tile 写 ExtractSides / InsertSides，不再写旧键
if (!/setByteArray\("ExtractSides"/.test(tileSource) || !/setByteArray\("InsertSides"/.test(tileSource))
	failures.push("TileMatterInterface 没有写 ExtractSides / InsertSides");

if (/setByteArray\("SideModes"/.test(tileSource) || /setByteArray\("AccessSides"/.test(tileSource))
	failures.push("TileMatterInterface 还在写旧键 SideModes / AccessSides");

// 双向面两个端口：getNetworkPorts 里抽出 / 推入各取一次
if (!/portAt\(index, true\)/.test(tileSource) || !/portAt\(index, false\)/.test(tileSource))
	failures.push("getNetworkPorts 没有为双向面产出两个端口");

// 点击面：方块记录 side，同步包下发 clickSide
if (!/setClickSide\(side\)/.test(blockSource))
	failures.push("BlockMatterInterface 没有记录点击面");

if (!/clickSide/.test(syncSource))
	failures.push("MatterInterfaceSyncPKT 没有下发 clickSide");

// ---------- 1b) 过滤表分套：数据模型 / 端口选表 / NBT / 同步 / 容器 / GUI ----------
// 这一整段只做字符串包含判断（不用正则），避免脚本自身成为转义地雷
const has = (source, needle) => source.includes(needle);

// 物品接口：抽入表 + 推出表，各 FILTER_SET_SIZE 格
if (!has(tileSource, 'ItemStack[] filter = new ItemStack[FILTER_SET_SIZE]'))
	failures.push('TileMatterInterface 的抽入表不是 FILTER_SET_SIZE 格');
if (!has(tileSource, 'ItemStack[] outputFilter = new ItemStack[FILTER_SET_SIZE]'))
	failures.push('TileMatterInterface 没有推出表 outputFilter[FILTER_SET_SIZE]');

// 槽位索引自带表：0..8 抽入 / 9..17 推出
if (!has(tileSource, 'slot < FILTER_SET_SIZE ? filter : outputFilter'))
	failures.push('TileMatterInterface 的 itemTable 没有按槽位索引分区');
if (!has(tileSource, 'public static int filterIndex(int slot)'))
	failures.push('TileMatterInterface 缺少 filterIndex（槽位 → 表内格）');

// NBT：两把键（旧键 Filter 只喂抽入表，OutputFilter 缺失 = 推出表为空 = 全通）
if (!has(tileSource, 'setTag("Filter"') || !has(tileSource, 'setTag("OutputFilter"'))
	failures.push('TileMatterInterface 没有分别写 Filter / OutputFilter');
if (!has(tileSource, 'readItemFilter(nbt, "Filter"') || !has(tileSource, 'readItemFilter(nbt, "OutputFilter"'))
	failures.push('TileMatterInterface 没有分别读 Filter / OutputFilter');
if (!has(fluidTileSource, 'setTag("FluidFilter"') || !has(fluidTileSource, 'setTag("FluidOutputFilter"'))
	failures.push('TileMatterInterfaceFluid 没有分别写 FluidFilter / FluidOutputFilter');
if (!has(fluidTileSource, 'getTagList("FluidFilter"') || !has(fluidTileSource, 'getTagList("FluidOutputFilter"'))
	failures.push('TileMatterInterfaceFluid 没有分别读 FluidFilter / FluidOutputFilter');

// 端口按自己的 mode 选表
for (const [label, source] of [['物品', itemTileSource], ['流体', fluidTileSource]]) {
	if (!has(source, 'mode == EndpointMode.OUTPUT'))
		failures.push(label + '端口没有按 mode 选表');
	if (!has(source, 'matchesOutputFilter('))
		failures.push(label + '端口没有调用 matchesOutputFilter（推出表判定）');
}

if (!has(itemTileSource, 'matchesTable(outputFilter, stack)'))
	failures.push('物品 matchesOutputFilter 没有读 outputFilter 表');
if (!has(fluidTileSource, 'matchesTable(fluidOutputFilter, stack)'))
	failures.push('流体 matchesOutputFilter 没有读 fluidOutputFilter 表');

// 同步包：两套物品模板 + 18 条目流体标记（0..8 抽入 / 9..17 推出）
if (!has(syncSource, 'pkt.filter, pkt.outputFilter'))
	failures.push('MatterInterfaceSyncPKT 没有把两套物品模板一起交给 applySync');
if (!has(syncSource, 'pkt.fluidFilter') || !has(syncSource, 'pkt.fluidOutputFilter'))
	failures.push('MatterInterfaceSyncPKT 没有把两套流体标记一起交给 applySync');
if (!has(syncSource, 'new FluidStack[FILTER_SET * 2]'))
	failures.push('MatterInterfaceSyncPKT 的流体标记列表不是 18 条目（FILTER_SET * 2）');

// 容器：18 个幽灵槽，推出那一套默认在屏幕外
if (!has(containerSource, 'FILTER_SET_COUNT'))
	failures.push('ContainerMatterInterface 没有按两套表建槽');
if (!has(containerSource, 'set == 0 ? FILTER_SLOT_Y : FILTER_OFFSCREEN_Y'))
	failures.push('ContainerMatterInterface 没有把不活跃那一套过滤槽摆到屏幕外');
if (!has(containerSource, 'slotIndex < TileMatterInterface.FILTER_SIZE'))
	failures.push('ContainerMatterInterface 的 shift-click 区间没有跟着 FILTER_SIZE 后移');

// GUI：标签按钮 + 槽位移屏（同一时刻只有一套可点可看）
if (!has(guiSource, 'private void applyFilterSlotLayout('))
	failures.push('GUIMatterInterface 没有 applyFilterSlotLayout（两套槽位坐标互换）');
if (!has(guiSource, 'yDisplayPosition = baseY'))
	failures.push('GUIMatterInterface 没有按激活标签写 yDisplayPosition');
if (!has(guiSource, 'ID_FILTER_INPUT') || !has(guiSource, 'ID_FILTER_OUTPUT'))
	failures.push('GUIMatterInterface 缺少抽入 / 推出两个标签按钮');
if (!has(guiSource, '"pe.matterinterface.tab.input"') || !has(guiSource, '"pe.matterinterface.tab.output"'))
	failures.push('GUIMatterInterface 没有使用 tab 文案键');
if (!has(guiSource, '"pe.matterinterface.editing"'))
	failures.push('GUIMatterInterface 没有显示「正在编辑哪一套」小字');
if (!has(guiSource, 'yDisplayPosition < 0'))
	failures.push('GUIMatterInterface 没有跳过屏幕外那一套（槽位框 / 色块 / 命中判定）');

// GT 反射：类名查找 + 静态缓存，且绝不 import gregtech
if (!has(fluidTileSource, 'getFluidFromDisplayStack') || !has(fluidTileSource, 'getFluidForFilledItem'))
	failures.push('流体解析链缺少 GTUtility 两级反射');
if (!has(fluidTileSource, 'findClass("gregtech.api.util.GTUtility")') || !has(fluidTileSource, 'Class.forName('))
	failures.push('GT 反射没有按类名查 GTUtility');
if (fluidTileSource.split('\n').some((line) => line.trim().startsWith('import gregtech.')))
	failures.push('流体 tile 硬 import 了 gregtech 类（GT 是 compileOnly，必须走反射）');
if (!has(fluidTileSource, 'static final Method FROM_DISPLAY_STACK') || !has(fluidTileSource, 'static final Method FOR_FILLED_ITEM'))
	failures.push('GT 反射方法没有做静态缓存');

// 两套 9 格坐标表 ↔ Java 常量由上面的 EXPECTED_* 逐项比对；这里再验一次「同一时刻只有一套在可视区」
for (const active of [0, 1]) {
	const table = filterSlotTable(active);
	const visible = [...table.input, ...table.output].filter((slot) => slot.y >= 0);

	if (visible.length !== FILTER_SET_SIZE)
		failures.push('activeSet=' + active + ' 时可视区里有 ' + visible.length + ' 格（应为 ' + FILTER_SET_SIZE + '）');
}

// ---------- 2) 朝向推导自检 ----------
// 四组水平面 + 一组竖直面，期望表全部手工推导后硬编码（左 = 前 × 上，等价「玩家正对该面时的左手边」）
// 点击北面：上 = UP、左 = (0,0,-1)×(0,1,0) = EAST（玩家站北边朝南看，左手边是东），后 = SOUTH、下 = DOWN、右 = WEST
const EXPECT_NORTH = [-1, DIR_UP, -1, DIR_EAST, DIR_NORTH, DIR_WEST, DIR_SOUTH, DIR_DOWN, -1];
// 点击南面：左 = (0,0,1)×(0,1,0) = WEST，后 = NORTH
const EXPECT_SOUTH = [-1, DIR_UP, -1, DIR_WEST, DIR_SOUTH, DIR_EAST, DIR_NORTH, DIR_DOWN, -1];
// 点击西面：左 = (-1,0,0)×(0,1,0) = NORTH，后 = EAST
const EXPECT_WEST = [-1, DIR_UP, -1, DIR_NORTH, DIR_WEST, DIR_SOUTH, DIR_EAST, DIR_DOWN, -1];
// 点击东面：左 = (1,0,0)×(0,1,0) = SOUTH，后 = WEST
const EXPECT_EAST = [-1, DIR_UP, -1, DIR_SOUTH, DIR_EAST, DIR_NORTH, DIR_WEST, DIR_DOWN, -1];
// 点击顶面且玩家朝南：上 = 北、下 = 南、左 = (0,1,0)×(0,0,-1) = WEST、右 = EAST、后 = 下
const EXPECT_TOP_SOUTH = [-1, DIR_NORTH, -1, DIR_WEST, DIR_UP, DIR_EAST, DIR_DOWN, DIR_SOUTH, -1];
// 点击底面且玩家朝南：上 = 北、下 = 南、左 = (0,-1,0)×(0,0,-1) = EAST、右 = WEST、后 = 上
const EXPECT_BOTTOM_SOUTH = [-1, DIR_NORTH, -1, DIR_EAST, DIR_DOWN, DIR_WEST, DIR_UP, DIR_SOUTH, -1];

const DERIVATION_CASES = [
	["点击北面", deriveGrid(DIR_NORTH), EXPECT_NORTH],
	["点击南面", deriveGrid(DIR_SOUTH), EXPECT_SOUTH],
	["点击西面", deriveGrid(DIR_WEST), EXPECT_WEST],
	["点击东面", deriveGrid(DIR_EAST), EXPECT_EAST],
	["点击顶面 / 玩家朝南", deriveGrid(DIR_UP, DIR_SOUTH), EXPECT_TOP_SOUTH],
	["点击底面 / 玩家朝南", deriveGrid(DIR_DOWN, DIR_SOUTH), EXPECT_BOTTOM_SOUTH]
];

for (const [label, actual, expected] of DERIVATION_CASES) {
	if (actual.length !== expected.length || actual.some((value, index) => value !== expected[index]))
		failures.push("朝向推导错误 " + label + "：得到 [" + actual.join(",") + "] 期望 [" + expected.join(",") + "]");
}

// 反过来验一遍「左 = 玩家左手侧」：前 取四个水平方向时，左必须等于 前 × UP
for (const front of [DIR_NORTH, DIR_SOUTH, DIR_WEST, DIR_EAST]) {
	const cells = deriveGrid(front);
	const left = cells[3];

	if (left <= 0 - 1 || DIRECTION_NAMES[left] === undefined)
		failures.push("水平面朝向 " + DIRECTION_NAMES[front] + " 的左格没有方向");

	if (cells[4] !== front || cells[6] !== (front ^ 1))
		failures.push("水平面朝向 " + DIRECTION_NAMES[front] + " 的前 / 后格不对");
}

// ---------- 3) 文案宽度 ----------

function loadLang(file) {
	const map = new Map();

	if (!fs.existsSync(file))
		return map;

	for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
		const index = line.indexOf("=");

		if (index > 0 && !line.startsWith("#"))
			map.set(line.slice(0, index).trim(), line.slice(index + 1));
	}

	return map;
}

const LOCALES = [
	{ name: "en_US", file: path.join("src", "main", "resources", "assets", "projecte", "lang", "en_US.lang") },
	{ name: "zh_CN", file: path.join("src", "main", "resources", "assets", "projecte", "lang", "zh_CN.lang") }
];

// 每条：[key, 允许的最大宽度, 说明]
const FIT_RULES = [
	["pe.matterinterface.title.item", 160, "标题行"],
	["pe.matterinterface.title.fluid", 160, "标题行"],
	["pe.matterinterface.priority", 52, "优先级标签"],
	["pe.matterinterface.status.connected", 160, "状态行"],
	["pe.matterinterface.status.disconnected", 160, "状态行"],
	["pe.matterinterface.status.alloff", 160, "状态行（六面全关提示）"],
	["pe.matterinterface.rate", FIELD_X - LABEL_X - 2, "速率标签"],
	["pe.matterinterface.interval", FIELD_X - LABEL_X - 2, "周期标签"],
	["pe.matterinterface.unit.second", UNIT_W - 4, "单位按钮"],
	["pe.matterinterface.unit.tick", UNIT_W - 4, "单位按钮"],
	["pe.matterinterface.apply", APPLY_W - 4, "应用按钮"],
	["pe.matterinterface.picker.extract", HINT_MAX_WIDTH, "选择器提示行（抽出）"],
	["pe.matterinterface.picker.insert", HINT_MAX_WIDTH, "选择器提示行（推入）"],
	["pe.matterinterface.dir.none", 20, "「无」占位"],
	["pe.matterinterface.tab.input", FILTER_TAB_W - 4, "过滤表标签（抽入）"],
	["pe.matterinterface.tab.output", FILTER_TAB_W - 4, "过滤表标签（推出）"],
	["pe.matterinterface.tab.input", FILTER_TAB_W - 4, "过滤表标签（抽入）"],
	["pe.matterinterface.tab.output", FILTER_TAB_W - 4, "过滤表标签（推出）"],
	...["up", "north", "east", "down", "south", "west"].map((side) => ["pe.matterinterface.side." + side, CELL_SIZE - 2, "格子方向缩写"]),
	...ROLE_NAMES.map((role) => ["pe.matterinterface.role." + role, 40, "悬停角色名"])
];

const langRows = [];

for (const locale of LOCALES) {
	const lang = loadLang(locale.file);

	if (lang.size === 0) {
		failures.push("读不到语言文件 " + locale.file);
		continue;
	}

	for (const [key, limit, what] of FIT_RULES) {
		if (!lang.has(key)) {
			failures.push(locale.name + " 缺少语言键 " + key);
			continue;
		}

		const value = lang.get(key);
		const width = textWidth(value);

		langRows.push({ locale: locale.name, key, value, width, limit, what });

		if (width > limit)
			failures.push(locale.name + " 文案放不下：" + key + " = \"" + value + "\" 实测 " + width + "px > 上限 " + limit + "px（" + what + "）");
	}

	// 「正在编辑哪一套」小字带 %s：用两个标签文案做样本
		if (lang.has("pe.matterinterface.editing"))
			for (const key of ["pe.matterinterface.tab.input", "pe.matterinterface.tab.output"]) {
				if (!lang.has(key))
					continue;

				const sample = lang.get("pe.matterinterface.editing").replace("%s", lang.get(key));
				const width = textWidth(sample);

				langRows.push({ locale: locale.name, key: "pe.matterinterface.editing/" + key, value: sample, width, limit: FILTER_SET_LABEL_W, what: "过滤编辑小字" });

				if (width > FILTER_SET_LABEL_W)
					failures.push(locale.name + " 文案放不下：editing 样本 \"" + sample + "\" 实测 " + width + "px > 上限 " + FILTER_SET_LABEL_W + "px（过滤编辑小字）");
			}

		// 网络行带 %s，用 4 位管道数做最坏样本
	if (lang.has("pe.matterinterface.network.connected")) {
		const sample = lang.get("pe.matterinterface.network.connected").replace("%s", "9999");
		const width = textWidth(sample);

		langRows.push({ locale: locale.name, key: "pe.matterinterface.network.connected", value: sample, width, limit: 160, what: "网络行" });

		if (width > 160)
			failures.push(locale.name + " 文案放不下：network.connected 样本 " + width + "px > 160px");
	}

	// 悬停提示行 4 个位置参数：角色 / 该面绝对方向 / 抽出方向 / 推入方向，取最宽的一组做样本
	const SIDE_NAME_KEYS = ["up", "north", "east", "down", "south", "west"].map((side) => "pe.matterinterface.side." + side);
	const ROLE_KEYS = ROLE_NAMES.map((role) => "pe.matterinterface.role." + role);
	const REQUIRED = ["pe.matterinterface.hover", "pe.matterinterface.dir.none", ...SIDE_NAME_KEYS, ...ROLE_KEYS];

	for (const key of REQUIRED)
		if (!lang.has(key))
			failures.push(locale.name + " 缺少语言键 " + key);

	if (REQUIRED.every((key) => lang.has(key))) {
		let worst = null;
		const none = lang.get("pe.matterinterface.dir.none");
		const directionLabels = [...SIDE_NAME_KEYS.map((key) => lang.get(key)), none];

		for (const role of ROLE_KEYS)
			for (const side of SIDE_NAME_KEYS)
				for (const extract of directionLabels)
					for (const insert of directionLabels) {
						const sample = lang.get("pe.matterinterface.hover")
							.replace("%1$s", lang.get(role))
							.replace("%2$s", lang.get(side))
							.replace("%3$s", extract)
							.replace("%4$s", insert);
						const width = textWidth(sample);

						if (worst === null || width > worst.width)
							worst = { sample, width };
					}

		langRows.push({ locale: locale.name, key: "pe.matterinterface.hover（最宽样本）", value: worst.sample, width: worst.width, limit: HINT_MAX_WIDTH, what: "悬停提示行" });

		if (worst.width > HINT_MAX_WIDTH)
			failures.push(locale.name + " 文案放不下：hover 最宽样本 \"" + worst.sample + "\" 实测 " + worst.width + "px > 上限 " + HINT_MAX_WIDTH + "px（悬停提示行）");
	}
}

// ---------- 输出 ----------

console.log("物质接口 GUI 布局检查");
console.log("窗口 " + GUI_W + "x" + GUI_H);

const cellXs = [0, 1, 2].map((col) => GRID_LEFT + col * CELL_PITCH);
const cellYs = [0, 1, 2].map((row) => GRID_TOP + row * CELL_PITCH);

console.log("3x3 网格 " + CELL_SIZE + "x" + CELL_SIZE + " 步进 " + CELL_PITCH + "，列 x=" + cellXs.join("/") + " 行 y=" + cellYs.join("/"));
console.log("选择器底板 (" + PICKER_RECT.x + "," + PICKER_RECT.y + ") " + PICKER_RECT.w + "x" + PICKER_RECT.h + "，压在 6 个格子上，不压任何槽位 / 输入框");
console.log("过滤槽 " + SLOT_PITCH + " 步进 @(" + FILTER_SLOT_X + "," + FILTER_SLOT_Y + ")，框原点 (" + (FILTER_SLOT_X - 1) + "," + (FILTER_SLOT_Y - 1) + ") 起");
console.log("背包 y=" + [0, 1, 2].map((row) => PLAYER_SLOT_Y + row * SLOT_PITCH).join("/") + "，快捷栏 y=" + HOTBAR_SLOT_Y + "，框高 " + SLOT_FRAME);
console.log("过滤表 " + FILTER_SET_COUNT + " 套 x " + FILTER_SET_SIZE + " 格（槽位 0.." + (FILTER_SLOT_COUNT - 1) + "），共用同一组 3x3 坐标；不活跃的一套整块挪到 y=" + FILTER_OFFSCREEN_Y);

for (const active of [0, 1]) {
	const table = filterSlotTable(active);
	const shown = [...table.input, ...table.output].filter((slot) => slot.y >= 0).map((slot) => slot.x + "/" + slot.y);

	console.log("  activeSet=" + active + "（" + ["抽入", "推出"][active] + "）可视 " + shown.length + " 格：" + shown.join(" "));
}
console.log("");
console.log("朝向推导（位置 → 角色 / 世界方向）");

for (const [label, , cells] of DERIVATION_CASES) {
	const text = ACTIVE_CELL_POSITIONS
		.map((position) => (GRID_ROLES[position] >= 0 ? ROLE_NAMES[GRID_ROLES[position]] + "=" + DIRECTION_NAMES[cells[position]] : ""))
		.filter(Boolean)
		.join("  ");

	console.log("  " + label.padEnd(18) + text);
}

console.log("");
console.log("控件表（x..x+w-1 / y..y+h-1）");

for (const control of CONTROLS)
	if (!control.name.startsWith("slot."))
		console.log("  " + control.name.padEnd(18) + " x " + String(control.x).padStart(3) + ".." + String(control.x + control.w - 1).padStart(3) + "   y " + String(control.y).padStart(3) + ".." + String(control.y + control.h - 1).padStart(3));

console.log("  (另有 " + CONTROLS.filter((control) => control.name.startsWith("slot.")).length + " 个槽位框，与槽位定义同源)");
console.log("");
console.log("文案宽度（MC 1.7.10 默认字体宽度表）");

for (const row of langRows)
	console.log("  " + row.locale + " " + row.key.padEnd(38) + " " + String(row.width).padStart(3) + "px / 上限 " + String(row.limit).padStart(3) + "px  " + JSON.stringify(row.value));

console.log("");

if (failures.length > 0) {
	console.error("检查失败：" + failures.length + " 处");
	console.error(failures.slice(0, 20).join("\n"));
	process.exit(1);
}

console.log("几何与两套过滤表坐标 / Java 常量与方向表 / 分套数据模型 / 朝向推导 / 文案宽度 五项检查全部通过");
