package moze_intel.projecte.gameObjs.gui.transport;

import java.util.Arrays;
import java.util.List;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.transport.ContainerMatterInterface;
import moze_intel.projecte.gameObjs.gui.component.RefinedButton;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.transport.MatterInterfaceButtonPKT;
import moze_intel.projecte.transport.TransportType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * 物质接口 GUI（176x244）：3x3 六面方向网格、优先级 0..9、速率上限 / 输出频率、9 格过滤槽与连接状态。
 *
 * 坐标唯一来源：贴图只画暗紫面板与外框装饰，**不画任何槽位底框 / 六面格子 / 输入框底框**。
 * 槽位框在 drawSlotFrames() 里遍历 this.inventorySlots.inventorySlots，用 Slot.xDisplayPosition - 1 现算；
 * 六面格子（以及盖在它上面的方向选择器）用本页的 cellX/cellY/CELL_SIZE；输入框用 GuiTextField 自带底框。
 * 于是「看到的」和「点得到的」永远出自同一批数字，贴图与槽位不可能再错位。
 *
 * 网格朝向随点击面旋转：『前』= 玩家右键点开 GUI 的那一面（BlockMatterInterface#onBlockActivated 的 side，
 * 由 tile 随同步包下发），『后』是它的反面；前是水平面时上/下 = UP/DOWN，
 * 前是竖直面（UP/DOWN）时上/下由玩家水平朝向决定。左/右一律 前 × 上 的叉积。
 * 每个格子里写的是**该面在世界里的绝对方向**，悬停时状态行显示『面：前(北) 抽：上 推：无』。
 *
 * 格子外观 = 由方向推导的模式：都没设灰、只设抽出绿、只设推入橙、两个都设绿底 + 橙内框。
 * 左键点主格子 → 打开『抽出方向』选择器；右键 → 『推入方向』选择器；
 * 选择器里再点一次当前已选中的方向 = 清除（置 -1），ESC 或点选择器外部 = 关闭且不修改。
 * 界面上的所有状态都来自服务端同步包，客户端不查询网络；只有「容器就在手边但六面全关」这一条提示
 * 由客户端就地读相邻方块实体得到（见 TileMatterInterface#hasAdjacentContainer）。
 * 速率 / 频率：两个输入框只收数字（空串按 0），单位按钮各自独立循环 /s ↔ /tick，
 * 点「应用」才把 (rateLimit, rateUnit, intervalValue, intervalUnit) 一次性发给服务端。
 *
 * 把 NEI 物品面板拖到过滤槽上设 ghost 标记的能力由 {@link #handleExternalDrag(int, int, ItemStack, int)} 提供，
 * 只发模板包、绝不改动被拖动的那一份（不复制也不消耗）。**本类及其父链不出现任何 NEI 类型**：
 * NEI 的接口适配放在 moze_intel.projecte.gameObjs.gui.transport.nei.GUIMatterInterfaceNei，
 * 只在 NEI 存在时由 utils.GuiHandler 实例化 —— 无 NEI 的整合包不会在这里类加载失败。
 */
public class GUIMatterInterface extends GuiContainer
{
	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/matter_interface.png");

	// 暗物质紫底上必须用浅色文字
	private static final int COLOR_TEXT = 0xE8E8E8;
	private static final int COLOR_HOVER = 0xFFFFA0;
	private static final int COLOR_ERROR = 0xFF5555;
	/** 「容器在手边但六面全关」的提醒色：不是错误，但需要玩家动作 */
	private static final int COLOR_WARN = 0xFFC04A;
	/** 输入框空态提示（灰） */
	private static final int COLOR_HINT = 0x8A8A8A;

	// 主格子底色 = 由方向推导出来的模式（ARGB，高字节不透明）
	/** 都没设：灰 */
	private static final int COLOR_FACE_OFF = 0xFF555555;
	/** 只设抽出：绿 */
	private static final int COLOR_FACE_EXTRACT = 0xFF3FBF5F;
	/** 只设推入：橙 */
	private static final int COLOR_FACE_INSERT = 0xFFE08A2B;
	/** 双向：绿底，再套一圈橙色内框（COLOR_FACE_INSERT） */
	private static final int COLOR_FACE_BOTH = 0xFF3FBF5F;
	/** 格子 1px 外框：比任何状态底色都深，压住边缘不糊 */
	private static final int COLOR_CELL_BORDER = 0xFF14111C;
	/** 『前』格子的外框：金黄，一眼能看出网格是按哪一面转的 */
	private static final int COLOR_FRONT_MARK = 0xFFFFE070;

	// 方向选择器（盖在主网格同一块区域上）
	private static final int COLOR_PICKER_PANEL = 0xFF120C1C;
	private static final int COLOR_PICKER_CELL = 0xFF2C2340;
	private static final int COLOR_PICKER_BORDER = 0xFF6A5794;
	private static final int COLOR_PICKER_TEXT = 0xFF191024;

	// 槽位框（Java 自绘）：18x18 = 1px 外阴影 + 内容区 + 1px 内高光
	private static final int SLOT_FRAME = 18;
	private static final int COLOR_SLOT_SHADOW = 0xFF120C1C;
	private static final int COLOR_SLOT_FILL = 0xFF241C33;
	private static final int COLOR_SLOT_LIGHT = 0xFF3E3157;

	// 流体标记色块：没有容器物品（气体）或不想看桶图标时，靠这块颜色说明「这里是水」
	/** 色块边长（1px 深色描边另算，共 12x12，居中在 16x16 内容区里） */
	private static final int FLUID_SWATCH_SIZE = 10;
	/** 色块描边色：与格子外框同色，压住边缘不糊 */
	private static final int COLOR_FLUID_SWATCH_BORDER = 0xFF14111C;
	/** 流体取不到颜色（外模组抛异常）时用白色半透明，绝不因为「画不出来」而崩 GUI */
	private static final int COLOR_FLUID_SWATCH_FALLBACK = 0x80E8E8E8;
	/** 色块一律半透明，压不住底下的槽位底色 */
	private static final int FLUID_SWATCH_ALPHA = 0x80000000;

	// 3x3 主网格 / 方向选择器：每格 16x16、步进 16（与鼠标判定区完全重合），整块 48x48
	private static final int GRID_LEFT = 8;
	private static final int GRID_TOP = 18;
	private static final int CELL_SIZE = 16;
	private static final int CELL_PITCH = 16;
	private static final int GRID_COLUMNS = 3;
	private static final int GRID_ROWS = 3;
	/** 格子总数（含三个留空位置） */
	private static final int GRID_CELLS = 9;
	/** 『前』所在的位置：正中间那一格 */
	private static final int GRID_FRONT_CELL = 4;

	/** 主网格 3x3 的角色表（row-major，-1 = 留空）：0 上 1 下 2 左 3 右 4 前 5 后 */
	private static final int[] GRID_ROLES = { -1, 0, -1, 2, 4, 3, 5, 1, -1 };
	/** 方向选择器 3x3 的世界绝对方向（row-major，值 = ForgeDirection ordinal，-1 = 留空） */
	private static final int[] PICKER_ORDINALS = { -1, 1, -1, 4, 5, 2, 3, 0, -1 };

	// 优先级：挪到右上角，标签 + 两个 14x14 按钮 + 中间数值（左侧整块让给 3x3 主网格）
	private static final int PRIORITY_LABEL_X = 118;
	private static final int PRIORITY_LABEL_Y = 20;
	private static final int PRIORITY_MINUS_X = 118;
	private static final int PRIORITY_PLUS_X = 148;
	private static final int PRIORITY_BUTTON_Y = 34;
	private static final int PRIORITY_BUTTON_SIZE = 14;
	private static final int PRIORITY_VALUE_X = 140;
	private static final int PRIORITY_VALUE_Y = 37;

	// 状态行 + 悬停提示行（提示只在鼠标压住六面格子 / 选择器时出现，这条位置是常留白）
	private static final int STATUS_X = 8;
	private static final int STATUS_Y = 86;
	private static final int NETWORK_Y = 98;
	private static final int HINT_X = 8;
	private static final int HINT_Y = 110;

	// 速率 / 周期两行：标签 + 数字输入框 + 单位按钮；右边是「应用」
	private static final int LABEL_X = 8;
	private static final int FIELD_X = 44;
	private static final int FIELD_W = 64;
	private static final int FIELD_H = 16;
	private static final int UNIT_X = 112;
	private static final int UNIT_W = 32;
	private static final int UNIT_H = 16;
	private static final int RATE_FIELD_Y = 122;
	private static final int INTERVAL_FIELD_Y = 142;
	private static final int RATE_LABEL_Y = 126;
	private static final int INTERVAL_LABEL_Y = 146;
	private static final int APPLY_X = 148;
	private static final int APPLY_Y = 132;
	private static final int APPLY_W = 24;
	private static final int APPLY_H = 16;

	private static final int ID_PRIORITY_DOWN = 1;
	private static final int ID_PRIORITY_UP = 2;
	private static final int ID_RATE_UNIT = 3;
	private static final int ID_INTERVAL_UNIT = 4;
	private static final int ID_APPLY = 5;
	private static final int ID_FILTER_INPUT = 6;
	private static final int ID_FILTER_OUTPUT = 7;

	// ---------- 过滤表标签（抽入 / 推出）：只切客户端看到的那一套 ----------
	/** 过滤槽 3x3 坐标（与 ContainerMatterInterface 同源）：切换标签时两套在「屏内 / 屏外」之间互换 */
	private static final int FILTER_SLOT_PITCH = 18;
	private static final int FILTER_SLOT_X = 62;
	private static final int FILTER_SLOT_Y = 18;
	/** 不活跃那一套的屏幕外 Y（与 ContainerMatterInterface.FILTER_OFFSCREEN_Y 同值） */
	private static final int FILTER_OFFSCREEN_Y = -2000;
	/** 两个标签按钮：并排贴在优先级按钮下方，宽 26 高 12 */
	private static final int FILTER_TAB_INPUT_X = 118;
	private static final int FILTER_TAB_OUTPUT_X = 145;
	private static final int FILTER_TAB_Y = 52;
	private static final int FILTER_TAB_W = 26;
	private static final int FILTER_TAB_H = 12;
	/** 激活标签底下那条高亮线的高度 */
	private static final int FILTER_TAB_MARK_H = 2;
	/** 过滤区旁的「正在编辑哪一套」小字 */
	private static final int FILTER_SET_LABEL_X = 118;
	private static final int FILTER_SET_LABEL_Y = 68;

	/** Long.MAX_VALUE 的十进制位数：输入框再多也收不下更大的数 */
	private static final int MAX_DIGITS = 19;
	/** 文本墨迹高度：getFontHeight() 在 1.7.10 是 FontRenderer.FONT_HEIGHT（9），实际墨迹 8 行 */
	private static final int TEXT_INK_HEIGHT = 8;

	/** 拿不到点击面（旧包 / 未同步）时的回退：前 = 南 */
	private static final ForgeDirection FALLBACK_FRONT = ForgeDirection.SOUTH;

	/**
	 * ForgeDirection ordinal → 面名键，索引就是 ordinal：下 上 北 南 西 东。
	 * 主网格格子里写的、悬停提示里显示的都是这套**世界绝对方向**。
	 */
	private static final String[] DIR_KEYS = {
		"pe.matterinterface.side.down", "pe.matterinterface.side.up", "pe.matterinterface.side.north",
		"pe.matterinterface.side.south", "pe.matterinterface.side.west", "pe.matterinterface.side.east"
	};

	/** 角色码（{@link #GRID_ROLES} 的取值）→ 语言键，顺序即角色码：上 下 左 右 前 后 */
	private static final String[] ROLE_KEYS = {
		"pe.matterinterface.role.up", "pe.matterinterface.role.down", "pe.matterinterface.role.left",
		"pe.matterinterface.role.right", "pe.matterinterface.role.front", "pe.matterinterface.role.back"
	};

	private final TileMatterInterface tile;
	private NumberField rateField;
	private NumberField intervalField;

	/** 客户端本地待应用的单位（0 = /s，1 = /tick）：点「应用」才发给服务端 */
	private int localRateUnit;
	private int localIntervalUnit;
	/** 玩家动过输入框：动过之后不再用同步过来的值覆盖文本 */
	private boolean rateDirty;
	private boolean intervalDirty;

	/** 9 个格子各自对应的世界方向（ForgeDirection ordinal），-1 = 留空 */
	private final byte[] cellDirs = new byte[GRID_CELLS];
	/** cellDirs 是按哪个点击面算出来的：点击面变了才重算 */
	private int cachedClickSide = -2;
	/** 玩家打开 GUI 时的水平朝向：『前』是上 / 下时，用它决定网格的上 / 下 */
	private ForgeDirection playerFacing = ForgeDirection.SOUTH;

	/**
	 * 当前正在编辑的过滤表：0 = 抽入（槽位 0..8），1 = 推出（槽位 9..17）。
	 * 纯客户端状态：服务端只按槽位索引区分两套表，永远不需要知道你在看哪一套。
	 */
	private int activeFilterSet;

	/** 方向选择器是否打开（打开时整块盖在主网格上） */
	private boolean pickerOpen;
	/** true = 正在选抽出方向，false = 正在选推入方向 */
	private boolean pickerExtract;
	/** 选择器对应的主网格格子位置 0..8 */
	private int pickerCell = -1;

	public GUIMatterInterface(InventoryPlayer invPlayer, TileMatterInterface tile)
	{
		super(new ContainerMatterInterface(invPlayer, tile));
		this.tile = tile;
		this.xSize = 176;
		this.ySize = 244;
	}

	@Override
	public void initGui()
	{
		super.initGui();

		// 玩家朝向只在这里取一次：GUI 打开后玩家基本不会再转身，重复取反而会让网格抖动
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;

		if (player != null)
			this.playerFacing = horizontalFacing(player);

		refreshGrid();

		if (rateField == null)
		{
			rateField = new NumberField(fontRendererObj, 0, 0, FIELD_W, FIELD_H);
			intervalField = new NumberField(fontRendererObj, 0, 0, FIELD_W, FIELD_H);
			rateField.setText(textOf(tile.getRateLimit()));
			intervalField.setText(textOf(tile.getIntervalValue()));
			localRateUnit = tile.getRateUnit();
			localIntervalUnit = tile.getIntervalUnit();
		}

		// 输入框用绝对屏幕坐标：界面缩放 / 窗口尺寸变了只挪位置，文本保持
		rateField.xPosition = guiLeft + FIELD_X;
		rateField.yPosition = guiTop + RATE_FIELD_Y;
		intervalField.xPosition = guiLeft + FIELD_X;
		intervalField.yPosition = guiTop + INTERVAL_FIELD_Y;

		buttonList.add(new RefinedButton(ID_PRIORITY_DOWN, guiLeft + PRIORITY_MINUS_X, guiTop + PRIORITY_BUTTON_Y,
			PRIORITY_BUTTON_SIZE, PRIORITY_BUTTON_SIZE, "-"));
		buttonList.add(new RefinedButton(ID_PRIORITY_UP, guiLeft + PRIORITY_PLUS_X, guiTop + PRIORITY_BUTTON_Y,
			PRIORITY_BUTTON_SIZE, PRIORITY_BUTTON_SIZE, "+"));
		buttonList.add(new RefinedButton(ID_RATE_UNIT, guiLeft + UNIT_X, guiTop + RATE_FIELD_Y, UNIT_W, UNIT_H, unitLabel(localRateUnit)));
		buttonList.add(new RefinedButton(ID_INTERVAL_UNIT, guiLeft + UNIT_X, guiTop + INTERVAL_FIELD_Y, UNIT_W, UNIT_H, unitLabel(localIntervalUnit)));
		buttonList.add(new RefinedButton(ID_APPLY, guiLeft + APPLY_X, guiTop + APPLY_Y, APPLY_W, APPLY_H,
			StatCollector.translateToLocal("pe.matterinterface.apply")));
		buttonList.add(new RefinedButton(ID_FILTER_INPUT, guiLeft + FILTER_TAB_INPUT_X, guiTop + FILTER_TAB_Y,
			FILTER_TAB_W, FILTER_TAB_H, StatCollector.translateToLocal("pe.matterinterface.tab.input")));
		buttonList.add(new RefinedButton(ID_FILTER_OUTPUT, guiLeft + FILTER_TAB_OUTPUT_X, guiTop + FILTER_TAB_Y,
			FILTER_TAB_W, FILTER_TAB_H, StatCollector.translateToLocal("pe.matterinterface.tab.output")));

		// 槽位坐标每次打开界面都要重摆：界面缩放 / 重开都会重建 buttonList 与世界，槽位数组则一直复用
		applyFilterSlotLayout();
		refreshFilterTabs();
	}

	/**
	 * 按 {@link #activeFilterSet} 把两套过滤槽摆开：激活那一套落在 3x3 可视区，另一套整块挪到屏幕外。
	 * 这一份坐标是**客户端独占**的（Slot.xDisplayPosition / yDisplayPosition 只影响渲染与命中判定），
	 * 服务端容器里的槽位坐标从未被读过，所以切换标签不需要任何网络往返。
	 */
	private void applyFilterSlotLayout()
	{
		List slots = this.inventorySlots.inventorySlots;
		int setSize = TileMatterInterface.FILTER_SET_SIZE;

		for (int index = 0; index < TileMatterInterface.FILTER_SIZE && index < slots.size(); index++)
		{
			Slot slot = (Slot) slots.get(index);
			int position = index % setSize;
			int baseY = index / setSize == activeFilterSet ? FILTER_SLOT_Y : FILTER_OFFSCREEN_Y;

			slot.xDisplayPosition = FILTER_SLOT_X + (position % GRID_COLUMNS) * FILTER_SLOT_PITCH;
			slot.yDisplayPosition = baseY + (position / GRID_COLUMNS) * FILTER_SLOT_PITCH;
		}
	}

	/** 激活的标签用高亮字色，另一个用普通字色：两个按钮的状态完全由 activeFilterSet 推导 */
	private void refreshFilterTabs()
	{
		for (int i = 0; i < buttonList.size(); i++)
		{
			Object entry = buttonList.get(i);

			if (!(entry instanceof GuiButton))
				continue;

			GuiButton button = (GuiButton) entry;

			if (button.id == ID_FILTER_INPUT)
				button.packedFGColour = activeFilterSet == 0 ? COLOR_HOVER : COLOR_TEXT;
			else if (button.id == ID_FILTER_OUTPUT)
				button.packedFGColour = activeFilterSet == 1 ? COLOR_HOVER : COLOR_TEXT;
		}
	}

	@Override
	public void updateScreen()
	{
		super.updateScreen();
		refreshGrid();

		// 同步包可能在 GUI 打开后才到：没被玩家改过、也没在编辑的输入框跟随服务端值
		if (!rateDirty && !rateField.isFocused())
			rateField.setText(textOf(tile.getRateLimit()));

		if (!intervalDirty && !intervalField.isFocused())
			intervalField.setText(textOf(tile.getIntervalValue()));
	}

	/**
	 * 按点击面算 9 个格子各自的世界方向。点击面（{@code tile.getClickSide()}）没变就直接返回，
	 * 所以每帧调用也只多一次整数比较。
	 *
	 * 向量演算（x 东 / y 上 / z 南，与 ForgeDirection 的 offset 一致）：
	 * 前 = 点击面的外法线（正对玩家的那一面），后 = 前 的反面。
	 * 前 是水平面时：上 = UP、下 = DOWN；左 = 前 × 上。
	 * 前 是竖直面（UP/DOWN）时：上 = 玩家水平朝向的反面、下 = 玩家水平朝向，左 = 前 × 上。
	 * 叉积用 offset 直接算，结果仍是六个基本方向之一，因此不会出现斜向。
	 */
	private void refreshGrid()
	{
		int clickSide = tile.getClickSide();

		if (clickSide == cachedClickSide)
			return;

		cachedClickSide = clickSide;
		Arrays.fill(cellDirs, TileMatterInterface.SIDE_UNSET);

		ForgeDirection front = clickSide >= 0 && clickSide < TileMatterInterface.SIDE_COUNT
			? ForgeDirection.getOrientation(clickSide) : FALLBACK_FRONT;
		ForgeDirection up;

		if (front.offsetY == 0)
			up = ForgeDirection.UP;
		else
			up = playerFacing.getOpposite();

		ForgeDirection left = cross(front, up);

		setCell(1, up);
		setCell(3, left);
		setCell(4, front);
		setCell(5, left.getOpposite());
		setCell(6, front.getOpposite());
		setCell(7, up.getOpposite());
	}

	private void setCell(int position, ForgeDirection dir)
	{
		if (position >= 0 && position < GRID_CELLS && dir != ForgeDirection.UNKNOWN)
			cellDirs[position] = (byte) dir.ordinal();
	}

	/** 轴对齐单位向量的叉积 a × b：两个输入都是六个基本方向，结果同样是基本方向 */
	private static ForgeDirection cross(ForgeDirection a, ForgeDirection b)
	{
		int x = a.offsetY * b.offsetZ - a.offsetZ * b.offsetY;
		int y = a.offsetZ * b.offsetX - a.offsetX * b.offsetZ;
		int z = a.offsetX * b.offsetY - a.offsetY * b.offsetX;

		if (x != 0)
			return x > 0 ? ForgeDirection.EAST : ForgeDirection.WEST;

		if (y != 0)
			return y > 0 ? ForgeDirection.UP : ForgeDirection.DOWN;

		if (z != 0)
			return z > 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH;

		return ForgeDirection.UNKNOWN;
	}

	/** 玩家水平朝向（只看 yaw）：0 南 / 1 西 / 2 北 / 3 东，与 EntityPlayer#getHorizontalFacing 同一套整数 */
	private static ForgeDirection horizontalFacing(EntityPlayer player)
	{
		switch (MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3)
		{
			case 0:
				return ForgeDirection.SOUTH;
			case 1:
				return ForgeDirection.WEST;
			case 2:
				return ForgeDirection.NORTH;
			default:
				return ForgeDirection.EAST;
		}
	}

	@Override
	protected void actionPerformed(GuiButton button)
	{
		super.actionPerformed(button);

		switch (button.id)
		{
			case ID_PRIORITY_DOWN:
			case ID_PRIORITY_UP:
				// 先做本地更新，服务端回包会覆盖成权威值
				tile.localAddPriority(button.id == ID_PRIORITY_DOWN ? -1 : 1);
				PacketHandler.sendToServer(new MatterInterfaceButtonPKT(tile.xCoord, tile.yCoord, tile.zCoord, button.id));
				break;
			case ID_RATE_UNIT:
				localRateUnit = toggleUnit(localRateUnit);
				button.displayString = unitLabel(localRateUnit);
				break;
			case ID_INTERVAL_UNIT:
				localIntervalUnit = toggleUnit(localIntervalUnit);
				button.displayString = unitLabel(localIntervalUnit);
				break;
			case ID_APPLY:
				applyLimits();
				break;
			case ID_FILTER_INPUT:
			case ID_FILTER_OUTPUT:
				// 只切本地状态：两套槽位坐标互换，同一时刻只有 9 格在可视区；服务端按槽位索引区分，不受影响
				activeFilterSet = button.id == ID_FILTER_INPUT ? 0 : 1;
				applyFilterSlotLayout();
				refreshFilterTabs();
				break;
			default:
				break;
		}
	}

	/** 把两个输入框的当前内容 + 两个单位一次发给服务端，并把被夹过的值回显出来 */
	private void applyLimits()
	{
		long rate = parseLimit(rateField.getText());
		long interval = parseLimit(intervalField.getText());

		// 空串 → 0；解析失败或超过 Long.MAX_VALUE → 夹到上限。回显让玩家立刻看到服务端将收到什么
		rateField.setText(textOf(rate));
		intervalField.setText(textOf(interval));
		rateDirty = false;
		intervalDirty = false;

		PacketHandler.sendToServer(MatterInterfaceButtonPKT.limits(tile.xCoord, tile.yCoord, tile.zCoord,
			rate, localRateUnit, interval, localIntervalUnit));
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode)
	{
		// 选择器开着时 ESC 只关选择器：这次点开的选择器不算「设置」，直接丢弃
		if (pickerOpen && keyCode == Keyboard.KEY_ESCAPE)
		{
			pickerOpen = false;
			return;
		}

		if (rateField.isFocused() || intervalField.isFocused())
		{
			// ESC 先退出编辑，不然输入框抢走键盘后界面就关不掉了
			if (keyCode == Keyboard.KEY_ESCAPE)
			{
				rateField.setFocused(false);
				intervalField.setFocused(false);
				return;
			}

			if (rateField.isFocused())
			{
				rateField.textboxKeyTyped(typedChar, keyCode);
				rateDirty = true;
			}
			else
			{
				intervalField.textboxKeyTyped(typedChar, keyCode);
				intervalDirty = true;
			}

			return;
		}

		super.keyTyped(typedChar, keyCode);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
	{
		refreshGrid();

		if (pickerOpen)
		{
			int position = pickerPosAt(mouseX, mouseY);

			if (position >= 0)
			{
				if (mouseButton == 0)
					chooseDirection(position);

				return;
			}

			// 点选择器外部：关闭且不修改，同时吞掉这次点击（不落到下面的格子 / 槽位 / 输入框上）
			pickerOpen = false;
			return;
		}

		int gridPos = gridPosAt(mouseX, mouseY);

		// 主格子：左键 = 抽出方向，右键 = 推入方向
		if (gridPos >= 0 && (mouseButton == 0 || mouseButton == 1))
		{
			pickerOpen = true;
			pickerExtract = mouseButton == 0;
			pickerCell = gridPos;
			return;
		}

		// 点框内 → 聚焦并吞掉这次点击（不落到下面的槽位上）；点框外 → 两个框都失焦
		boolean onRate = clickField(rateField, mouseX, mouseY, mouseButton);
		boolean onInterval = clickField(intervalField, mouseX, mouseY, mouseButton);

		if (onRate || onInterval)
			return;

		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	/**
	 * 选中选择器里的某个方向：写入对应数组并关闭选择器。
	 * 再点一次当前已选中的方向 = 清除（-1）；客户端先本地写一份好立刻看到效果，服务端回包再覆盖成权威值。
	 */
	private void chooseDirection(int position)
	{
		pickerOpen = false;

		if (position < 0 || position >= GRID_CELLS || pickerCell < 0 || pickerCell >= GRID_CELLS)
			return;

		int dir = PICKER_ORDINALS[position];
		int face = cellDirs[pickerCell] & 255;

		if (dir < 0 || dir >= TileMatterInterface.SIDE_COUNT || face >= TileMatterInterface.SIDE_COUNT)
			return;

		byte current = pickerExtract ? tile.getExtractSide(face) : tile.getInsertSide(face);
		int value = current == dir ? TileMatterInterface.SIDE_UNSET : dir;

		tile.localSetSideDirection(face, pickerExtract, (byte) value);
		PacketHandler.sendToServer(MatterInterfaceButtonPKT.direction(tile.xCoord, tile.yCoord, tile.zCoord,
			pickerExtract, face, value));
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		super.drawScreen(mouseX, mouseY, partialTicks);

		// 输入框是绝对屏幕坐标，必须画在 GuiContainer 的 (guiLeft, guiTop) 平移之外
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		drawField(rateField);
		drawField(intervalField);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	/** 输入框底框由 GuiTextField 自己按 xPosition/width 画（坐标同源），这里只补文字与空态灰提示 */
	private void drawField(NumberField field)
	{
		field.drawTextBox();

		if (field.getText().isEmpty())
			fontRendererObj.drawString("0", field.xPosition + 4,
				field.yPosition + (field.height - TEXT_INK_HEIGHT) / 2, COLOR_HINT);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY)
	{
		refreshGrid();
		// 先画色块再画文字：色块落在过滤槽内容区里，物品图标由 GuiContainer 在本层之后叠上来
		drawFluidFilterSwatches();
		// 过滤表标签的激活高亮线 + 「正在编辑哪一套」小字
		drawFilterTabMarks();
		drawFilterSetLabel();

		fontRendererObj.drawString(StatCollector.translateToLocal(getTitleKey()), LABEL_X, 6, COLOR_TEXT);

		if (pickerOpen)
			drawPickerLabels(mouseX, mouseY);
		else
			drawGridLabels(mouseX, mouseY);

		fontRendererObj.drawString(StatCollector.translateToLocal("pe.matterinterface.priority"), PRIORITY_LABEL_X, PRIORITY_LABEL_Y, COLOR_TEXT);
		drawCenteredString(fontRendererObj, Integer.toString(tile.getPriority()), PRIORITY_VALUE_X, PRIORITY_VALUE_Y, COLOR_TEXT);

		drawStatusLine();
		drawHoverLine(mouseX, mouseY);

		String network = tile.displayPipes > 0
			? StatCollector.translateToLocalFormatted("pe.matterinterface.network.connected", tile.displayPipes)
			: StatCollector.translateToLocal("pe.matterinterface.network.disconnected");
		fontRendererObj.drawString(network, STATUS_X, NETWORK_Y, tile.displayPipes > 0 ? COLOR_TEXT : COLOR_ERROR);

		fontRendererObj.drawString(StatCollector.translateToLocal("pe.matterinterface.rate"), LABEL_X, RATE_LABEL_Y, COLOR_TEXT);
		fontRendererObj.drawString(StatCollector.translateToLocal("pe.matterinterface.interval"), LABEL_X, INTERVAL_LABEL_Y, COLOR_TEXT);
	}

	/**
	 * 状态行三态：已连接 / 容器就在手边但六面全关 / 真的没有容器。
	 * 第二态是实机反馈加的：以前只报「未连接容器」，玩家以为接口坏了，其实是六面默认全关。
	 */
	private void drawStatusLine()
	{
		String key;
		int color;

		if (tile.displayConnected)
		{
			key = "pe.matterinterface.status.connected";
			color = COLOR_TEXT;
		}
		else if (tile.isAllSidesOff() && tile.hasAdjacentContainer())
		{
			key = "pe.matterinterface.status.alloff";
			color = COLOR_WARN;
		}
		else
		{
			key = "pe.matterinterface.status.disconnected";
			color = COLOR_ERROR;
		}

		fontRendererObj.drawString(StatCollector.translateToLocal(key), STATUS_X, STATUS_Y, color);
	}

	/**
	 * 流体标记色块：流体接口的每个过滤槽，只要有流体模板就按 {@code fluid.getColor()} 画一块
	 * 10x10 半透明方块 + 1px 描边。展示物品（水桶之类）由 GuiContainer 在本层之后叠上去，
	 * 所以「容器物品优先、色块兜底」是免费得到的；气体这类没有容器物品 / 方块形态的流体只剩色块。
	 */
	private void drawFluidFilterSwatches()
	{
		List slots = this.inventorySlots.inventorySlots;

		for (int index = 0; index < TileMatterInterface.FILTER_SIZE && index < slots.size(); index++)
		{
			Slot probe = (Slot) slots.get(index);

			// 不活跃那一套整块被挪到屏幕外：它的色块跟着一起不画
			if (probe.yDisplayPosition < 0)
				continue;

			FluidStack template = tile.getFluidFilter(index);

			if (template == null || template.getFluid() == null)
				continue;

			Slot slot = (Slot) slots.get(index);
			int x = slot.xDisplayPosition + (SLOT_FRAME - 2 - FLUID_SWATCH_SIZE) / 2;
			int y = slot.yDisplayPosition + (SLOT_FRAME - 2 - FLUID_SWATCH_SIZE) / 2;

			Gui.drawRect(x - 1, y - 1, x + FLUID_SWATCH_SIZE + 1, y + FLUID_SWATCH_SIZE + 1, COLOR_FLUID_SWATCH_BORDER);
			Gui.drawRect(x, y, x + FLUID_SWATCH_SIZE, y + FLUID_SWATCH_SIZE, fluidColor(template));
		}

		// Gui.drawRect 会把 glColor 留在最后一次用的颜色上，复原以免影响后面的物品渲染
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	/** 激活标签底下压一条 2px 高亮线：深色面板上光靠字色差别不够醒目 */
	private void drawFilterTabMarks()
	{
		int x = activeFilterSet == 1 ? FILTER_TAB_OUTPUT_X : FILTER_TAB_INPUT_X;

		Gui.drawRect(x, FILTER_TAB_Y + FILTER_TAB_H - FILTER_TAB_MARK_H, x + FILTER_TAB_W,
			FILTER_TAB_Y + FILTER_TAB_H, COLOR_HOVER);

		// Gui.drawRect 会把 glColor 留在最后一次用的颜色上，复原以免影响后面的物品渲染
		GL11.glColor4f(1F, 1F, 1F, 1F);
	}

	/**
	 * 过滤区旁边的小字：现在编辑的是哪一张表（抽入 / 推出）。
	 * 两套共用同一组坐标、只有 9 格看得见，这行字是唯一能确认「我这一标落在哪一套」的提示。
	 */
	private void drawFilterSetLabel()
	{
		String key = activeFilterSet == 1 ? "pe.matterinterface.tab.output" : "pe.matterinterface.tab.input";

		fontRendererObj.drawString(StatCollector.translateToLocalFormatted("pe.matterinterface.editing",
			StatCollector.translateToLocal(key)), FILTER_SET_LABEL_X, FILTER_SET_LABEL_Y, COLOR_HINT);
	}

	/** 流体颜色 → 半透明 ARGB；外模组的流体实现抛异常时退到白色 */
	private static int fluidColor(FluidStack template)
	{
		try
		{
			return FLUID_SWATCH_ALPHA | (template.getFluid().getColor() & 0xFFFFFF);
		}
		catch (Exception e)
		{
			return COLOR_FLUID_SWATCH_FALLBACK;
		}
	}

	/**
	 * 悬停提示行（y=110 那一条常留白）：
	 * 主网格上是「面：前(北) 抽：上 推：无」，过滤槽上是流体名，选择器开着时是当前选择器的用途与操作说明。
	 */
	private void drawHoverLine(int mouseX, int mouseY)
	{
		if (pickerOpen)
		{
			String key = pickerExtract ? "pe.matterinterface.picker.extract" : "pe.matterinterface.picker.insert";
			fontRendererObj.drawString(StatCollector.translateToLocal(key), HINT_X, HINT_Y, COLOR_HOVER);
			return;
		}

		if (drawFluidFilterHover(mouseX, mouseY))
			return;

		int position = gridPosAt(mouseX, mouseY);

		if (position < 0)
			return;

		int face = cellDirs[position] & 255;

		String text = StatCollector.translateToLocalFormatted("pe.matterinterface.hover",
			StatCollector.translateToLocal(ROLE_KEYS[GRID_ROLES[position]]),
			directionLabel(face),
			directionLabel(tile.getExtractSide(face)),
			directionLabel(tile.getInsertSide(face)));

		fontRendererObj.drawString(text, HINT_X, HINT_Y, COLOR_HOVER);
	}

	/**
	 * 悬停过滤槽：用 {@code fluid.getLocalizedName()} 占住提示行，**不走物品 tooltip** ——
	 * 物品接口的幽灵标记本来就靠物品 tooltip 显示，两条 tooltip 会互相打架。
	 *
	 * @return true 表示这一行已经被流体名占用
	 */
	private boolean drawFluidFilterHover(int mouseX, int mouseY)
	{
		int index = getFilterSlotAt(mouseX, mouseY);

		if (index < 0)
			return false;

		FluidStack template = tile.getFluidFilter(index);

		if (template == null || template.getFluid() == null)
			return false;

		fontRendererObj.drawString(StatCollector.translateToLocalFormatted("pe.matterinterface.filter.fluid",
			template.getFluid().getLocalizedName()), HINT_X, HINT_Y, COLOR_HOVER);
		return true;
	}

	/** 方向 → 世界绝对方向名；-1 显示「无」 */
	private static String directionLabel(int direction)
	{
		if (direction < 0 || direction >= TileMatterInterface.SIDE_COUNT)
			return StatCollector.translateToLocal("pe.matterinterface.dir.none");

		return StatCollector.translateToLocal(DIR_KEYS[direction]);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY)
	{
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

		drawSlotFrames();

		if (pickerOpen)
			drawPickerBackground(mouseX, mouseY);
		else
			drawGridBackground();
	}

	/**
	 * 槽位框：直接遍历容器里真实的 Slot，用 xDisplayPosition - 1 现算 18x18 外框。
	 * 框与槽位是同一份数据，所以贴图怎么改都不可能再和槽位错位。
	 */
	private void drawSlotFrames()
	{
		List slots = this.inventorySlots.inventorySlots;

		for (int i = 0; i < slots.size(); i++)
		{
			Slot slot = (Slot) slots.get(i);

			// 不活跃那一套过滤槽在屏幕外（y = -2000）：不画框，免得白画一遍还溢出窗口
			if (slot.yDisplayPosition < 0)
				continue;

			int x = guiLeft + slot.xDisplayPosition - 1;
			int y = guiTop + slot.yDisplayPosition - 1;

			Gui.drawRect(x, y, x + SLOT_FRAME, y + SLOT_FRAME, COLOR_SLOT_SHADOW);
			Gui.drawRect(x + 1, y + 1, x + SLOT_FRAME - 1, y + SLOT_FRAME - 1, COLOR_SLOT_FILL);
			Gui.drawRect(x + 1, y + SLOT_FRAME - 2, x + SLOT_FRAME - 1, y + SLOT_FRAME - 1, COLOR_SLOT_LIGHT);
			Gui.drawRect(x + SLOT_FRAME - 2, y + 1, x + SLOT_FRAME - 1, y + SLOT_FRAME - 1, COLOR_SLOT_LIGHT);
		}
	}

	/** 主网格：每个格子 1px 边框 + 模式底色（双向再套一圈橙内框），『前』的边框换成金黄 */
	private void drawGridBackground()
	{
		for (int position = 0; position < GRID_CELLS; position++)
		{
			int face = cellDirs[position] & 255;

			if (face >= TileMatterInterface.SIDE_COUNT)
				continue;

			int x = guiLeft + cellX(position);
			int y = guiTop + cellY(position);

			Gui.drawRect(x, y, x + CELL_SIZE, y + CELL_SIZE, position == GRID_FRONT_CELL ? COLOR_FRONT_MARK : COLOR_CELL_BORDER);
			Gui.drawRect(x + 1, y + 1, x + CELL_SIZE - 1, y + CELL_SIZE - 1, faceColor(face));

			if (hasExtract(face) && hasInsert(face))
				drawInnerFrame(x, y, COLOR_FACE_INSERT);
		}
	}

	/** 格子的模式底色：都没设灰 / 只设抽绿 / 只设推橙 / 双向绿底（橙框另画） */
	private int faceColor(int face)
	{
		boolean extract = hasExtract(face);
		boolean insert = hasInsert(face);

		if (extract && insert)
			return COLOR_FACE_BOTH;

		if (extract)
			return COLOR_FACE_EXTRACT;

		if (insert)
			return COLOR_FACE_INSERT;

		return COLOR_FACE_OFF;
	}

	private boolean hasExtract(int face)
	{
		return tile.getExtractSide(face) >= 0;
	}

	private boolean hasInsert(int face)
	{
		return tile.getInsertSide(face) >= 0;
	}

	/** 内缩 2px 的 1px 方框：外层留 1px 底色 + 1px 边框，双向面因此是「绿底 + 橙框」 */
	private static void drawInnerFrame(int x, int y, int color)
	{
		int inner = x + 2;
		int innerY = y + 2;
		int innerEnd = x + CELL_SIZE - 2;
		int innerEndY = y + CELL_SIZE - 2;

		Gui.drawRect(inner, innerY, innerEnd, innerY + 1, color);
		Gui.drawRect(inner, innerEndY - 1, innerEnd, innerEndY, color);
		Gui.drawRect(inner, innerY + 1, inner + 1, innerEndY - 1, color);
		Gui.drawRect(innerEnd - 1, innerY + 1, innerEnd, innerEndY - 1, color);
	}

	/** 方向选择器：整块不透明底盖住主网格，再画 6 个方向格子（当前已选中的那个用状态色高亮） */
	private void drawPickerBackground(int mouseX, int mouseY)
	{
		int panelX = guiLeft + GRID_LEFT;
		int panelY = guiTop + GRID_TOP;
		int panelW = GRID_COLUMNS * CELL_PITCH;
		int panelH = GRID_ROWS * CELL_PITCH;

		Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PICKER_PANEL);

		int face = pickerCell >= 0 && pickerCell < GRID_CELLS ? cellDirs[pickerCell] & 255 : -1;
		int current = face < TileMatterInterface.SIDE_COUNT
			? (pickerExtract ? tile.getExtractSide(face) : tile.getInsertSide(face)) : TileMatterInterface.SIDE_UNSET;
		int hovered = pickerPosAt(mouseX, mouseY);

		for (int position = 0; position < GRID_CELLS; position++)
		{
			int dir = PICKER_ORDINALS[position];

			if (dir < 0)
				continue;

			int x = guiLeft + cellX(position);
			int y = guiTop + cellY(position);
			int fill = dir == current ? (pickerExtract ? COLOR_FACE_EXTRACT : COLOR_FACE_INSERT) : COLOR_PICKER_CELL;

			Gui.drawRect(x, y, x + CELL_SIZE, y + CELL_SIZE, position == hovered ? COLOR_FRONT_MARK : COLOR_PICKER_BORDER);
			Gui.drawRect(x + 1, y + 1, x + CELL_SIZE - 1, y + CELL_SIZE - 1, fill);
		}
	}

	/** 主网格格子里写的是该面在世界里的绝对方向（上 / 北 / 东 …），字宽与字高都取字体实测值居中 */
	private void drawGridLabels(int mouseX, int mouseY)
	{
		int hovered = gridPosAt(mouseX, mouseY);

		for (int position = 0; position < GRID_CELLS; position++)
		{
			int face = cellDirs[position] & 255;

			if (face >= TileMatterInterface.SIDE_COUNT)
				continue;

			drawCenteredCellText(position, StatCollector.translateToLocal(DIR_KEYS[face]),
				position == hovered ? COLOR_HOVER : COLOR_TEXT);
		}
	}

	/** 选择器格子：世界绝对方向（上 / 西 / 东 / 北 / 南 / 下），已选中的用深色字压在状态色底上 */
	private void drawPickerLabels(int mouseX, int mouseY)
	{
		int face = pickerCell >= 0 && pickerCell < GRID_CELLS ? cellDirs[pickerCell] & 255 : -1;
		int current = face < TileMatterInterface.SIDE_COUNT
			? (pickerExtract ? tile.getExtractSide(face) : tile.getInsertSide(face)) : TileMatterInterface.SIDE_UNSET;

		for (int position = 0; position < GRID_CELLS; position++)
		{
			int dir = PICKER_ORDINALS[position];

			if (dir < 0)
				continue;

			drawCenteredCellText(position, StatCollector.translateToLocal(DIR_KEYS[dir]),
				dir == current ? COLOR_PICKER_TEXT : COLOR_TEXT);
		}
	}

	private void drawCenteredCellText(int position, String label, int color)
	{
		int textWidth = fontRendererObj.getStringWidth(label);
		int textHeight = fontRendererObj.FONT_HEIGHT;

		fontRendererObj.drawString(label,
			cellX(position) + (CELL_SIZE - textWidth) / 2,
			cellY(position) + (CELL_SIZE - textHeight) / 2,
			color);
	}

	/** 屏幕坐标 → 主网格格子位置（只认算出来有方向的格子），未命中返回 -1 */
	private int gridPosAt(int mouseX, int mouseY)
	{
		int position = cellPosAt(mouseX, mouseY);

		return position >= 0 && (cellDirs[position] & 255) < TileMatterInterface.SIDE_COUNT ? position : -1;
	}

	/** 屏幕坐标 → 选择器格子位置（只认 6 个方向格），未命中返回 -1 */
	private int pickerPosAt(int mouseX, int mouseY)
	{
		int position = cellPosAt(mouseX, mouseY);

		return position >= 0 && PICKER_ORDINALS[position] >= 0 ? position : -1;
	}

	/** 屏幕坐标 → 3x3 格子位置（不问格子内容），未命中返回 -1 */
	private int cellPosAt(int mouseX, int mouseY)
	{
		int localX = mouseX - guiLeft;
		int localY = mouseY - guiTop;

		for (int position = 0; position < GRID_CELLS; position++)
			if (localX >= cellX(position) && localX < cellX(position) + CELL_SIZE
				&& localY >= cellY(position) && localY < cellY(position) + CELL_SIZE)
				return position;

		return -1;
	}

	private static int cellX(int position)
	{
		return GRID_LEFT + (position % GRID_COLUMNS) * CELL_PITCH;
	}

	private static int cellY(int position)
	{
		return GRID_TOP + (position / GRID_COLUMNS) * CELL_PITCH;
	}

	private String getTitleKey()
	{
		return tile.getTransportType() == TransportType.ITEM ? "pe.matterinterface.title.item" : "pe.matterinterface.title.fluid";
	}

	/** 点在输入框框内就聚焦并返回 true，框外一律失焦 */
	private static boolean clickField(GuiTextField field, int mouseX, int mouseY, int mouseButton)
	{
		boolean inside = mouseX >= field.xPosition && mouseX < field.xPosition + field.width
			&& mouseY >= field.yPosition && mouseY < field.yPosition + field.height;

		field.mouseClicked(mouseX, mouseY, mouseButton);
		return inside && mouseButton == 0;
	}

	private static int toggleUnit(int unit)
	{
		return unit == 1 ? 0 : 1;
	}

	private static String unitLabel(int unit)
	{
		return StatCollector.translateToLocal(unit == 1 ? "pe.matterinterface.unit.tick" : "pe.matterinterface.unit.second");
	}

	/** 存储值 → 输入框文本：0 显示为空串，交给灰色提示 */
	private static String textOf(long value)
	{
		return value > 0L ? Long.toString(value) : "";
	}

	/** 空串按 0；非数字或超过 Long.MAX_VALUE 一律夹到上限，逐位累加因此不会抛异常 */
	private static long parseLimit(String text)
	{
		if (text == null || text.isEmpty())
			return 0L;

		long value = 0L;

		for (int i = 0; i < text.length(); i++)
		{
			char digitChar = text.charAt(i);

			if (digitChar < '0' || digitChar > '9')
				return Long.MAX_VALUE;

			int digit = digitChar - '0';

			if (value > (Long.MAX_VALUE - digit) / 10L)
				return Long.MAX_VALUE;

			value = value * 10L + digit;
		}

		return value;
	}

	// ---------- ghost 槽拖放（外部模组入口，本类不含任何 NEI 类型） ----------

	/**
	 * 外部拖放入口（NEI 等）：命中过滤槽就把被拖的那一份当模板发出去，右键拖（button == 1）视为清除。
	 * 刻意不动 draggedStack.stackSize：调用方会按 size == 0 删掉被拖的那份，
	 * 我们只取模板 —— 既不消耗玩家的物品，也不会复制。
	 * 未命中返回 false，让调用方继续走它的默认处理。
	 *
	 * @return true 表示这次拖放已被本 GUI 消费
	 */
	public boolean handleExternalDrag(int mouseX, int mouseY, ItemStack draggedStack, int button)
	{
		int slot = getFilterSlotAt(mouseX, mouseY);

		if (slot < 0)
			return false;

		ItemStack template = button == 1 ? null : draggedStack;

		tile.setFilterTemplate(slot, template);
		PacketHandler.sendToServer(MatterInterfaceButtonPKT.filter(tile.xCoord, tile.yCoord, tile.zCoord, slot, template));

		return true;
	}

	/** 屏幕坐标 → 过滤槽索引（按槽位内容区 16x16 判定），未命中返回 -1 */
	private int getFilterSlotAt(int mouseX, int mouseY)
	{
		List slots = this.inventorySlots.inventorySlots;

		for (int index = 0; index < TileMatterInterface.FILTER_SIZE && index < slots.size(); index++)
		{
			Slot slot = (Slot) slots.get(index);

			// 屏幕外那一套（y = -2000）永远命中不了：「同一时刻只有 9 格可点」就是靠这条实现的
			if (slot.yDisplayPosition < 0)
				continue;

			int x = guiLeft + slot.xDisplayPosition;
			int y = guiTop + slot.yDisplayPosition;

			if (mouseX >= x && mouseX < x + SLOT_FRAME - 2 && mouseY >= y && mouseY < y + SLOT_FRAME - 2)
				return index;
		}

		return -1;
	}

	/** 只收数字的输入框：非数字按键直接丢弃，绝不落到 GuiScreen 的快捷键上 */
	private static final class NumberField extends GuiTextField
	{
		private NumberField(FontRenderer fontRenderer, int x, int y, int width, int height)
		{
			super(fontRenderer, x, y, width, height);
			// 底框由 drawTextBox() 按 xPosition/width 现画：与点击区同一份坐标，贴图不再参与
			setEnableBackgroundDrawing(true);
			setMaxStringLength(MAX_DIGITS);
			setTextColor(COLOR_TEXT);
			setDisabledTextColour(COLOR_TEXT);
		}

		@Override
		public boolean textboxKeyTyped(char typedChar, int keyCode)
		{
			if (typedChar >= '0' && typedChar <= '9')
				return super.textboxKeyTyped(typedChar, keyCode);

			if (Character.isISOControl(typedChar) || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
				|| keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
				|| keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END)
				return super.textboxKeyTyped(typedChar, keyCode);

			return false;
		}
	}
}
