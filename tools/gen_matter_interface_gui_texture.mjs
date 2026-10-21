// 物质接口 GUI 背景生成器：node tools/gen_matter_interface_gui_texture.mjs
//
// 贴图只负责「暗紫面板 + 外框 + 分区装饰线」，输出 176x244。
// 槽位框 / 六面格子 / 输入框底框一律不画在这里 —— 它们由 GUIMatterInterface 在运行时按同一批坐标绘制，
// 面板函数 panelPixel(x, y) 不接受任何矩形参数，结构上就不可能画出控件形态，贴图与控件因此不会漂。
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import {
	GUI_W, GUI_H, CELLS, FIELDS, SLOTS, SLOT_FRAME, CONTROLS, TEXT_ROWS, SEPARATORS_Y, DIVIDER_X, DIVIDER_Y,
	BOTTOM_MARGIN, checkLayout, encodePng, panelPixel
} from "./matter_interface_layout.mjs";

const OUT = path.join("src", "main", "resources", "assets", "projecte", "textures", "gui", "matter_interface.png");

const failures = checkLayout();

// 保留区：贴图在这些矩形里只允许出现面板装饰像素
const RESERVED = [];

for (const slot of SLOTS)
	RESERVED.push({ name: "slot." + slot.kind, x: slot.x - 1, y: slot.y - 1, w: SLOT_FRAME, h: SLOT_FRAME });

for (const cell of CELLS)
	RESERVED.push({ name: cell.name, x: cell.x, y: cell.y, w: cell.w, h: cell.h });

for (const field of FIELDS)
	RESERVED.push({ name: field.name, x: field.x - 1, y: field.y - 1, w: field.w + 2, h: field.h + 2 });

// ---------- PNG 回读（zlib 解压 + PNG 行过滤还原）----------

function decodePng(buffer) {
	if (buffer.readUInt32BE(0) !== 0x89504E47)
		throw new Error("不是 PNG");

	let offset = 8;
	let width = 0, height = 0;
	const idat = [];

	while (offset < buffer.length) {
		const length = buffer.readUInt32BE(offset);
		const type = buffer.toString("ascii", offset + 4, offset + 8);
		const data = buffer.subarray(offset + 8, offset + 8 + length);

		if (type === "IHDR") {
			width = data.readUInt32BE(0);
			height = data.readUInt32BE(4);

			if (data[8] !== 8 || data[9] !== 6)
				throw new Error("只支持 8bit RGBA");
		}
		else if (type === "IDAT")
			idat.push(Buffer.from(data));
		else if (type === "IEND")
			break;

		offset += 12 + length;
	}

	const raw = zlib.inflateSync(Buffer.concat(idat));
	const stride = width * 4;
	const pixels = Buffer.alloc(stride * height);

	for (let y = 0; y < height; y++) {
		const filter = raw[y * (stride + 1)];

		if (filter !== 0)
			throw new Error("不支持的行过滤 " + filter);

		raw.copy(pixels, y * stride, y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
	}

	return { width, height, pixels };
}

const png = encodePng(GUI_W, GUI_H, panelPixel);

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, png);

const decoded = decodePng(png);

if (decoded.width !== GUI_W || decoded.height !== GUI_H)
	failures.push("PNG 尺寸 " + decoded.width + "x" + decoded.height + " 与预期 " + GUI_W + "x" + GUI_H + " 不符");

// 逐像素回读比对：证明编码器正确，同时证明保留区里没有任何「控件形态」像素
let checkedPixels = 0;

for (let y = 0; y < GUI_H; y++)
	for (let x = 0; x < GUI_W; x++) {
		const index = (y * GUI_W + x) * 4;
		const expected = panelPixel(x, y);

		if (decoded.pixels[index] !== expected[0] || decoded.pixels[index + 1] !== expected[1] || decoded.pixels[index + 2] !== expected[2])
			failures.push("回读像素不符 (" + x + "," + y + ")");

		checkedPixels++;
	}

if (checkedPixels !== GUI_W * GUI_H)
	failures.push("回读像素数 " + checkedPixels + " 与 " + GUI_W * GUI_H + " 不符");

// 保留区必须整体落在窗口内，且与装饰线（分隔线 / 竖线）不相交
for (const rect of RESERVED) {
	if (rect.x < 1 || rect.y < 1 || rect.x + rect.w > GUI_W - 1 || rect.y + rect.h > GUI_H - 1)
		failures.push("保留区越界 " + rect.name + "(" + rect.x + "," + rect.y + "," + rect.w + "," + rect.h + ")");
}

if (failures.length > 0) {
	console.error("自检失败：" + failures.length + " 处");
	console.error(failures.slice(0, 12).join("\n"));
	process.exit(1);
}

console.log("gui background  " + GUI_W + "x" + GUI_H + "  " + png.length + " bytes  " + OUT);
console.log("回读逐像素一致 " + checkedPixels + " px");
console.log("保留区（贴图不得画控件形态）" + RESERVED.length + " 块：槽位 " + SLOTS.length + " / 六面格 " + CELLS.length + " / 输入框 " + FIELDS.length);
console.log("装饰：分隔线 y " + SEPARATORS_Y.map((row) => row + "/" + (row + 1)).join(", ") + "；竖线 x " + DIVIDER_X + "/" + (DIVIDER_X + 1) + " y " + DIVIDER_Y[0] + ".." + DIVIDER_Y[1]);
console.log("快捷栏下方留白 " + BOTTOM_MARGIN + " px；文字行 " + TEXT_ROWS.join(", ") + "（行距 " + TEXT_ROWS.slice(1).map((row, index) => row - TEXT_ROWS[index]).join("/") + "）");
console.log("过滤 3x3 槽位框原点 " + SLOTS.filter((slot) => slot.kind === "filter").map((slot) => "(" + (slot.x - 1) + "," + (slot.y - 1) + ")").join(" "));
console.log("控件表（x..x+w-1 / y..y+h-1）：");

for (const control of CONTROLS)
	console.log("  " + control.name.padEnd(20) + " x " + control.x + ".." + (control.x + control.w - 1) + "  y " + control.y + ".." + (control.y + control.h - 1));

console.log("alignment self-check OK");
