// 物质管道贴图生成器：node tools/gen_pipe_textures.mjs
// 输出 src/main/resources/assets/projecte/textures/blocks/transport/
// 每种 (type,tier) 16 色 x 2 外观（plain/connected）= 32 张：
// 3 类型（item/fluid/emc）x 3 档（dark/red/gem）x 16 色 x 2 = 288 张 16x16 PNG
import zlib from "node:zlib";
import fs from "node:fs";
import path from "node:path";

const OUT_DIR = path.join("src", "main", "resources", "assets", "projecte", "textures", "blocks", "transport");

// Minecraft 16 色染料基础色
const DYE_COLORS = [
	[233, 236, 236], [240, 118, 19], [189, 68, 179], [58, 175, 217],
	[248, 198, 39], [112, 185, 25], [237, 141, 172], [62, 68, 71],
	[142, 142, 134], [21, 137, 145], [121, 42, 172], [53, 57, 157],
	[114, 71, 40], [84, 109, 27], [161, 39, 34], [20, 21, 25]
];

function clamp(v) {
	return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
}

function mix(a, b, t) {
	return [clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t)];
}

function scale(c, f) {
	return [clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f)];
}

// 层级配色：暗物质 = 深紫暗金属，红物质 = 暗红
const TIERS = {
	dark: {
		plate: [52, 40, 78],
		frame: [26, 18, 40],
		tintBase: [58, 28, 92],
		tintMix: 0.5,
		tintScale: 0.9
	},
	red: {
		plate: [82, 30, 34],
		frame: [34, 12, 16],
		tintBase: [150, 30, 24],
		tintMix: 0.38,
		tintScale: 1.0
	}
};

// EMC 管道的荧光芯色（不随染料变化，保证与物品/流体管道一眼可分）
const EMC_CORE = [232, 255, 255];

// Gem 档配色：白金底板 + 白色高光芯 + 彩虹描边；染料只染管腔/能量臂，16 路信道仍可分辨
const GEM = {
	plate: [206, 182, 118],
	plateHi: [248, 240, 200],
	plateLo: [148, 124, 68],
	frame: [96, 78, 40],
	cell: [252, 252, 248]
};

const RAINBOW = [
	[255, 92, 92], [255, 160, 64], [255, 236, 88], [136, 240, 104],
	[80, 224, 232], [96, 148, 255], [184, 128, 255], [255, 120, 216]
];

function rainbowAt(x, y) {
	return RAINBOW[((x + y) * 3 + x) % RAINBOW.length];
}

// Gem 档：白金底板 + 6x6 白色高光芯（彩虹描边）+ 染料色管腔/能量臂
// 骨架沿用物品/流体（粗管腔）与 EMC（细能量臂）两套，但配色与描边完全不同
function pickGemPixel(x, y, dyeIndex, connected, typeName) {
	const thin = typeName === "emc";
	const bandX = thin ? x >= 7 && x <= 8 : x >= 6 && x <= 9;
	const bandY = thin ? y >= 7 && y <= 8 : y >= 6 && y <= 9;
	const reach = thin ? 2 : 3;
	const armTop = bandX && (connected ? y < 5 : y >= reach && y < 5);
	const armBottom = bandX && (connected ? y > 10 : y > 10 && y <= 15 - reach);
	const armLeft = bandY && (connected ? x < 5 : x >= reach && x < 5);
	const armRight = bandY && (connected ? x > 10 : x > 10 && x <= 15 - reach);
	const cell = x >= 5 && x <= 10 && y >= 5 && y <= 10;
	const arm = mix(scale(DYE_COLORS[dyeIndex], 1.12), GEM.plateHi, thin ? 0.32 : 0.18);

	if (cell || armTop || armBottom || armLeft || armRight) {
		// 彩虹描边：芯体外框按坐标走一遍色相，Gem 档一眼区别于暗物质/红物质
		if (cell && (x === 5 || x === 10 || y === 5 || y === 10))
			return rainbowAt(x, y);

		if (cell) {
			if (x >= 7 && x <= 8 && y >= 7 && y <= 8)
				return GEM.cell;

			return thin ? mix(GEM.cell, rainbowAt(x, y), 0.35) : mix(GEM.cell, arm, 0.22);
		}

		if (!connected && ((armTop && y === reach) || (armBottom && y === 15 - reach) || (armLeft && x === reach) || (armRight && x === 15 - reach)))
			return mix(arm, GEM.frame, 0.55);

		return arm;
	}

	if (x === 0 || y === 0 || x === 15 || y === 15)
		return GEM.frame;

	const noise = ((x * 7 + y * 5) % 7) - 3;

	if ((x + y) % 8 === 0)
		return [clamp(GEM.plateHi[0] + noise), clamp(GEM.plateHi[1] + noise), clamp(GEM.plateHi[2] + noise)];

	if ((x - y + 16) % 6 === 0)
		return [clamp(GEM.plateLo[0] + noise), clamp(GEM.plateLo[1] + noise), clamp(GEM.plateLo[2] + noise)];

	return [clamp(GEM.plate[0] + noise), clamp(GEM.plate[1] + noise), clamp(GEM.plate[2] + noise)];
}

function pickPixel(x, y, dyeIndex, connected, tierName, typeName) {
	if (tierName === "gem")
		return pickGemPixel(x, y, dyeIndex, connected, typeName);

	if (typeName === "emc")
		return pickEmcPixel(x, y, dyeIndex, connected, tierName);

	const tier = TIERS[tierName];
	const tint = scale(mix(DYE_COLORS[dyeIndex], tier.tintBase, tier.tintMix), tier.tintScale);

	const core = x >= 5 && x <= 10 && y >= 5 && y <= 10;
	const bandX = x >= 6 && x <= 9;
	const bandY = y >= 6 && y <= 9;

	// 四个方向的管道臂：connected 直达方块边缘，plain 只留 2px 收口
	const armTop = bandX && (connected ? y < 5 : y >= 3 && y < 5);
	const armBottom = bandX && (connected ? y > 10 : y > 10 && y <= 12);
	const armLeft = bandY && (connected ? x < 5 : x >= 3 && x < 5);
	const armRight = bandY && (connected ? x > 10 : x > 10 && x <= 12);

	if (core || armTop || armBottom || armLeft || armRight) {
		const outline = (core && (x === 5 || x === 10 || y === 5 || y === 10))
			|| (bandX && (armTop || armBottom) && (x === 6 || x === 9))
			|| (bandY && (armLeft || armRight) && (y === 6 || y === 9));

		if (outline)
			return scale(tint, 0.42);

		if (!connected && ((armTop && y === 3) || (armBottom && y === 12) || (armLeft && x === 3) || (armRight && x === 12)))
			return mix(tint, tier.frame, 0.62);

		if (core && x >= 7 && x <= 8 && y >= 7 && y <= 8)
			return scale(tint, 1.35);

		return tint;
	}

	// 金属底板：1px 边框 + 斜向明暗条纹
	if (x === 0 || y === 0 || x === 15 || y === 15)
		return tier.frame;

	const noise = ((x * 5 + y * 11) % 9) - 4;

	if ((x + y) % 7 === 0)
		return [clamp(tier.plate[0] + 8 + noise), clamp(tier.plate[1] + 8 + noise), clamp(tier.plate[2] + 10 + noise)];

	if ((x - y + 16) % 7 === 0)
		return [clamp(tier.plate[0] - 6 + noise), clamp(tier.plate[1] - 6 + noise), clamp(tier.plate[2] - 6 + noise)];

	return [clamp(tier.plate[0] + noise), clamp(tier.plate[1] + noise), clamp(tier.plate[2] + noise)];
}

// EMC 管道：细能量臂 + 6x6 荧光芯，与物品/流体管道的粗管腔 + 金属底板区分；
// 染料色决定底板与能量臂的色调，芯部固定高亮青白
function pickEmcPixel(x, y, dyeIndex, connected, tierName) {
	const tier = TIERS[tierName];
	const tint = scale(mix(DYE_COLORS[dyeIndex], tier.tintBase, 0.55), 0.85);
	// 臂是染色的能量线（提亮后再拉向青白），底板也吃一点染料，保证 16 色信道仍然可辨
	const spark = mix(scale(tint, 1.25), EMC_CORE, 0.5);
	const plate = mix(tier.plate, tint, 0.3);

	const cell = x >= 5 && x <= 10 && y >= 5 && y <= 10;
	const bandX = x >= 7 && x <= 8;
	const bandY = y >= 7 && y <= 8;

	const armTop = bandX && (connected ? y < 5 : y >= 2 && y < 5);
	const armBottom = bandX && (connected ? y > 10 : y > 10 && y <= 13);
	const armLeft = bandY && (connected ? x < 5 : x >= 2 && x < 5);
	const armRight = bandY && (connected ? x > 10 : x > 10 && x <= 13);

	if (cell || armTop || armBottom || armLeft || armRight) {
		// 芯体外框压暗一圈，读起来像一块发光的电池
		if (cell && (x === 5 || x === 10 || y === 5 || y === 10))
			return scale(tint, 0.45);

		if (cell) {
			if (x >= 7 && x <= 8 && y >= 7 && y <= 8)
				return mix(spark, EMC_CORE, 0.7);

			return x === 7 || x === 8 || y === 7 || y === 8 ? mix(spark, EMC_CORE, 0.3) : spark;
		}

		// plain 的臂末端收口，connected 直达方块边缘
		if (!connected && ((armTop && y === 2) || (armBottom && y === 13) || (armLeft && x === 2) || (armRight && x === 13)))
			return mix(spark, tier.frame, 0.5);

		return spark;
	}

	if (x === 0 || y === 0 || x === 15 || y === 15)
		return tier.frame;

	const noise = ((x * 3 + y * 7) % 7) - 3;

	// 底板上的荧光走线：每 4 像素一道暗青缝隙
	if (x % 4 === 2 || y % 4 === 2)
		return mix(plate, spark, 0.18);

	if ((x + y) % 5 === 0)
		return [clamp(plate[0] + 6 + noise), clamp(plate[1] + 8 + noise), clamp(plate[2] + 12 + noise)];

	return [clamp(plate[0] + noise), clamp(plate[1] + noise), clamp(plate[2] + 4 + noise)];
}

function renderPng(dyeIndex, connected, tierName, typeName) {
	const width = 16;
	const height = 16;
	const raw = Buffer.alloc((width * 4 + 1) * height);

	for (let y = 0; y < height; y++) {
		const rowStart = y * (width * 4 + 1);
		raw[rowStart] = 0; // filter type 0

		for (let x = 0; x < width; x++) {
			const color = pickPixel(x, y, dyeIndex, connected, tierName, typeName);
			const offset = rowStart + 1 + x * 4;
			raw[offset] = color[0];
			raw[offset + 1] = color[1];
			raw[offset + 2] = color[2];
			raw[offset + 3] = 255;
		}
	}

	return Buffer.concat([signature(), chunk("IHDR", ihdr(width, height)), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

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

function signature() {
	return Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
}

function ihdr(width, height) {
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header[8] = 8; // bit depth
	header[9] = 6; // RGBA
	header[10] = 0; // deflate
	header[11] = 0; // filter
	header[12] = 0; // no interlace
	return header;
}

function chunk(type, data) {
	const length = Buffer.alloc(4);
	length.writeUInt32BE(data.length, 0);
	const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body), 0);
	return Buffer.concat([length, body, crc]);
}

fs.mkdirSync(OUT_DIR, { recursive: true });

let count = 0;

for (const type of ["item", "fluid", "emc"]) {
	for (const tierName of ["dark", "red", "gem"]) {
		for (let color = 0; color < 16; color++) {
			for (const variant of ["plain", "connected"]) {
				const file = path.join(OUT_DIR, `${type}_${tierName}_${color}_${variant}.png`);
				fs.writeFileSync(file, renderPng(color, variant === "connected", tierName, type));
				count++;
			}
		}
	}
}

console.log(`generated ${count} pipe textures into ${OUT_DIR}`);
