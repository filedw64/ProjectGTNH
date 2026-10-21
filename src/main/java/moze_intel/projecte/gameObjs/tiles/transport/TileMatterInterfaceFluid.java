package moze_intel.projecte.gameObjs.tiles.transport;

import moze_intel.projecte.transport.TransportType;
import moze_intel.projecte.transport.api.EndpointMode;
import moze_intel.projecte.transport.api.IFluidEndpoint;
import moze_intel.projecte.transport.api.IPortOwner;
import java.lang.reflect.Method;
import java.util.Arrays;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * 流体物质接口：每个设了方向的面最多两个流体端口（抽出 / 推入角色各一）。
 * 抽出端口从该面相邻流体罐抽取 mB 送入网络，推入端口把网内流体送入该面相邻流体罐；
 * 双向面两个端口同时存在，各自带自己的操作方向。
 * simulate 阶段只读 tank 信息，绝不调用 fill/drain，保证零副作用。
 */
public class TileMatterInterfaceFluid extends TileMatterInterface
{
	/** getTankInfo 返回 null 时的替身：所有 tank 遍历都走它，热路径不分配 */
	private static final FluidTankInfo[] NO_TANKS = new FluidTankInfo[0];
	/** 单次请求上限：FluidStack.amount 是 int，再大没有意义，也避免任何加法越过 int 边界 */
	static final int MAX_REQUEST = Integer.MAX_VALUE - 1;
	/** 外来容器实现抛异常只记一条日志，避免每秒 20 次的日志洪水 */
	private boolean warnedBadHandler;

	/**
	 * 「抽入」表的**本体**：每格一个 amount 恒为 1 的流体模板（isFluidEqual 比对的正是它），null = 该格空。
	 * 基类的 {@link #filter} 在这里只剩「展示用物品」（水 → 水桶），不再参与任何判定。
	 */
	protected final FluidStack[] fluidFilter = new FluidStack[FILTER_SET_SIZE];
	/** 「推出」表的**本体**：投递时「该不该收」，与 {@link #fluidFilter} 完全独立，空表 = 全通 */
	protected final FluidStack[] fluidOutputFilter = new FluidStack[FILTER_SET_SIZE];

	@Override
	public TransportType getTransportType()
	{
		return TransportType.FLUID;
	}

	@Override
	protected boolean isValidTarget(TileEntity tile)
	{
		return tile instanceof IFluidHandler;
	}

	@Override
	protected IMatterPort createPort(int index, byte accessSide, boolean extract)
	{
		return new FluidPort(index, accessSide, extract ? EndpointMode.INPUT : EndpointMode.OUTPUT);
	}

	/** 单面单角色流体端口：只认本面相邻流体罐；对象本身长期复用，网络靠身份去重 */
	private final class FluidPort implements IFluidEndpoint, IPortOwner, IMatterPort
	{
		private final int index;
		private final EndpointMode mode;
		/** 访问相邻流体罐时用它的哪一面：0..5 = ForgeDirection ordinal，由本端口独占 */
		private byte accessSide;

		private FluidPort(int index, byte accessSide, EndpointMode mode)
		{
			this.index = index;
			this.accessSide = accessSide;
			this.mode = mode;
		}

		@Override
		public void setAccessSide(byte accessSide)
		{
			this.accessSide = accessSide;
		}

		@Override
		public TileEntity getPortOwner()
		{
			return TileMatterInterfaceFluid.this;
		}

		@Override
		public TransportType getTransportType()
		{
			return TileMatterInterfaceFluid.this.getTransportType();
		}

		@Override
		public int getNetworkColor()
		{
			return TileMatterInterfaceFluid.this.getNetworkColor();
		}

		@Override
		public EndpointMode getMode()
		{
			// 模式在创建端口时就定死了：抽出端口永远是 INPUT，推入端口永远是 OUTPUT
			return mode;
		}

		@Override
		public int getPriority()
		{
			return TileMatterInterfaceFluid.this.getPriority();
		}

		@Override
		public int insertFluid(FluidStack stack, boolean simulate)
		{
			// 只有推入端口（OUTPUT）受限速 / 限频约束：抽出端口在 MatterNetwork.returnFluid 的退回路径上也会调用本方法
			if (mode != EndpointMode.OUTPUT)
				return insertFluidInto(getSideTarget(index), accessSide, stack, simulate);

			// 推出方向只受【推出表】约束：网络投递前判过一次，直接调用本方法的路径同样必须过这一关
			if (stack == null || stack.amount <= 0 || stack.getFluid() == null || !matchesOutputFilter(stack)
				|| !isPushDue())
				return 0;

			// 网络每轮给的预算是 int（≤ 2^31），单次最多搬 2^31 mB；更大的上限靠多轮累积令牌体现
			long allowance = availableTokens();
			int request = (int) Math.min((long) stack.amount, allowance);

			if (request <= 0)
				return 0;

			// 限速时只把允许的 mB 交给容器，调用方传入的 stack 不被改动
			FluidStack offer = request == stack.amount ? stack : copyWithAmount(stack, request);
			int moved = insertFluidInto(getSideTarget(index), accessSide, offer, simulate);

			if (!simulate && moved > 0)
				onPushed(moved);

			return moved;
		}

		@Override
		public FluidStack drainFluid(int maxAmount, boolean simulate)
		{
			return drainFluidFrom(getSideTarget(index), accessSide, maxAmount, simulate);
		}

		@Override
		public boolean matchesFilter(FluidStack stack)
		{
			// 端口按自己的 mode 选表：抽出端口（INPUT）看抽入表，推入端口（OUTPUT）看推出表
			return mode == EndpointMode.OUTPUT
				? TileMatterInterfaceFluid.this.matchesOutputFilter(stack)
				: TileMatterInterfaceFluid.this.matchesFilter(stack);
		}
	}

	/** 把流体灌进该面相邻流体罐（用端口自带的操作方向 accessSide）；调用方传入的 stack 不会被改动 */
	int insertFluidInto(TileEntity target, byte accessSide, FluidStack stack, boolean simulate)
	{
		if (stack == null || stack.amount <= 0 || stack.getFluid() == null)
			return 0;

		if (!(target instanceof IFluidHandler))
			return 0;

		IFluidHandler handler = (IFluidHandler) target;
		ForgeDirection side = ForgeDirection.getOrientation(accessSide);
		Fluid fluid = stack.getFluid();
		int request = Math.min(stack.amount, MAX_REQUEST);

		if (!canFill(handler, side, fluid))
			return 0;

		if (simulate)
		{
			// 只读：空罐按 capacity、同类流体的罐按剩余空间。全部用 long 累加再夹回 int，
			// 台座那种「capacity = Integer.MAX_VALUE」的报数在多个罐相加时不会溢出成负数
			long room = 0L;
			FluidTankInfo[] tanks = tankInfoOf(handler, side);

			for (int i = 0; i < tanks.length; i++)
			{
				FluidTankInfo tank = tanks[i];

				if (tank == null)
					continue;

				if (tank.fluid != null && tank.fluid.getFluid() != fluid)
					continue;

				long free = tank.fluid == null ? (long) tank.capacity : (long) tank.capacity - (long) tank.fluid.amount;

				if (free <= 0L)
					continue;

				room += free;

				if (room >= (long) request)
					return request;
			}

			return (int) Math.min(room, (long) request);
		}

		// 真灌：第三个参数必须为 true。传 false 只是「问一句能装多少」，容器一滴都不会进，
		// 而网络已经把来源那边的真抽取当成成交了 —— 流体一步都不动的根因就在这里。
		// 交给容器的是副本（保留 tag），所以调用方手里的 stack（可能是网络复用的探针）不被改动。
		int filled;

		try
		{
			filled = handler.fill(side, copyWithAmount(stack, request), true);
		}
		catch (Exception e)
		{
			warnBadHandler(handler, "fill", e);
			return 0;
		}

		return Math.max(0, Math.min(filled, request));
	}

	/** 从该面相邻流体罐抽流体（用端口自带的操作方向 accessSide）；simulate 只读 tank 信息，真抽取才 drain */
	FluidStack drainFluidFrom(TileEntity target, byte accessSide, int maxAmount, boolean simulate)
	{
		if (maxAmount <= 0)
			return null;

		if (!(target instanceof IFluidHandler))
			return null;

		IFluidHandler handler = (IFluidHandler) target;
		ForgeDirection side = ForgeDirection.getOrientation(accessSide);
		FluidTankInfo[] tanks = tankInfoOf(handler, side);
		int request = Math.min(maxAmount, MAX_REQUEST);

		for (int i = 0; i < tanks.length; i++)
		{
			FluidTankInfo tank = tanks[i];

			if (tank == null || tank.fluid == null || tank.fluid.amount <= 0 || tank.fluid.getFluid() == null)
				continue;

			Fluid fluid = tank.fluid.getFluid();

			// 抽取侧同样受过滤表约束；simulate 与真抽取共用这一条判定，才能选中同一罐
			if (!matchesFilter(tank.fluid))
				continue;

			if (!canDrain(handler, side, fluid))
				continue;

			// 两边的量都是正的 int（台座会报到 Integer.MAX_VALUE / 2），min 之后再夹一次上限，
			// 这里既不做加法也不做乘法，不存在溢出成负数后「看起来没流体」的可能
			int amount = Math.min(request, tank.fluid.amount);

			if (amount <= 0)
				continue;

			if (simulate)
				return new FluidStack(fluid, amount);

			FluidStack drained;

			try
			{
				// 第三个参数必须为 true：传 false 只是「问一句」，容器一滴都不会少，
				// 网络却把返回值当成真抽到的货在搬运（守恒红线也在这里：只报不抽 = 凭空造流体）
				drained = handler.drain(side, new FluidStack(fluid, amount), true);
			}
			catch (Exception e)
			{
				warnBadHandler(handler, "drain", e);
				return null;
			}

			if (drained == null || drained.getFluid() == null || drained.amount <= 0)
				continue;

			return copyWithAmount(drained, Math.min(drained.amount, amount));
		}

		return null;
	}

	/** getTankInfo 允许返回 null / 抛异常：一律当成「没有 tank」，不让外来实现炸穿整张网络 */
	private FluidTankInfo[] tankInfoOf(IFluidHandler handler, ForgeDirection side)
	{
		try
		{
			FluidTankInfo[] tanks = handler.getTankInfo(side);
			return tanks == null ? NO_TANKS : tanks;
		}
		catch (Exception e)
		{
			warnBadHandler(handler, "getTankInfo", e);
			return NO_TANKS;
		}
	}

	private boolean canFill(IFluidHandler handler, ForgeDirection side, Fluid fluid)
	{
		try
		{
			return handler.canFill(side, fluid);
		}
		catch (Exception e)
		{
			warnBadHandler(handler, "canFill", e);
			return false;
		}
	}

	private boolean canDrain(IFluidHandler handler, ForgeDirection side, Fluid fluid)
	{
		try
		{
			return handler.canDrain(side, fluid);
		}
		catch (Exception e)
		{
			warnBadHandler(handler, "canDrain", e);
			return false;
		}
	}

	/** 外来容器实现抛异常：只记第一条，避免每 tick 刷日志；整张网络继续跑别的端点 */
	private void warnBadHandler(IFluidHandler handler, String method, Exception e)
	{
		if (warnedBadHandler)
			return;

		warnedBadHandler = true;
		PELogger.logWarn("Matter fluid interface at " + xCoord + "/" + yCoord + "/" + zCoord
			+ " ignored an exception from " + handler.getClass().getName() + "." + method + ": " + e);
	}

	/** 只改数量的副本：保留 tag，且绝不改动调用方（可能是网络复用的探针）持有的 stack */
	private static FluidStack copyWithAmount(FluidStack stack, int amount)
	{
		FluidStack copy = stack.copy();
		copy.amount = amount;
		return copy;
	}

	/** 槽位索引 → 该套流体表：0..8 抽入表，9..17 推出表（与基类的槽位索引同号） */
	private FluidStack[] fluidTable(int slot)
	{
		return slot < FILTER_SET_SIZE ? fluidFilter : fluidOutputFilter;
	}

	/**
	 * 写入过滤槽标记：**标记的本体是流体**，手里的物品只是「玩家拿它来做标记」的载体。
	 * 能解析出流体就把该流体记进该槽所属的那张流体表（0..8 抽入 / 9..17 推出），
	 * 基类那张物品表只留一个展示用物品（容器物品优先，退到流体方块）；
	 * 解析不出流体的物品**不允许**当标记：该槽直接清空，绝不留一个语义不明的物品标记。
	 */
	@Override
	public void setFilterTemplate(int slot, ItemStack stack)
	{
		if (slot < 0 || slot >= FILTER_SIZE)
			return;

		FluidStack template = resolveFluid(stack);
		int index = filterIndex(slot);

		if (template == null)
		{
			clearFilter(slot);
			return;
		}

		fluidTable(slot)[index] = template;
		// 展示物品可能为 null（既没有对应桶、流体也没有方块形态的气体）：那种槽位只靠 GUI 色块显示
		super.setFilterTemplate(slot, displayStackFor(template));
	}

	/** 清除标记：流体模板与展示物品必须一起清，否则会出现「槽里有桶但不过滤」的假象 */
	@Override
	protected void clearFilter(int slot)
	{
		if (slot < 0 || slot >= FILTER_SIZE)
			return;

		fluidTable(slot)[filterIndex(slot)] = null;
		super.clearFilter(slot);
	}

	/** 该槽的流体标记（amount 恒为 1）；空槽 / 越界返回 null。GUI 用它画色块与悬停文案 */
	@Override
	public FluidStack getFluidFilter(int slot)
	{
		return slot >= 0 && slot < FILTER_SIZE ? fluidTable(slot)[filterIndex(slot)] : null;
	}

	@Override
	protected void applyFluidFilter(FluidStack[] newFluidFilter, FluidStack[] newFluidOutputFilter)
	{
		applyFluidTable(fluidFilter, newFluidFilter);
		applyFluidTable(fluidOutputFilter, newFluidOutputFilter);
	}

	/** 同步包 → 某一张流体表：越界槽位清空，只收归一后的模板 */
	private static void applyFluidTable(FluidStack[] table, FluidStack[] source)
	{
		if (source == null)
			return;

		for (int i = 0; i < table.length; i++)
			table[i] = i < source.length ? normalizeTemplate(source[i]) : null;
	}

	@Override
	protected FluidStack[] getFluidFilterForSync()
	{
		return fluidFilter;
	}

	@Override
	protected FluidStack[] getFluidOutputFilterForSync()
	{
		return fluidOutputFilter;
	}

	/** 抽入表判定：抽取时「该不该抽」（INPUT 端口与 drainFluid 用） */
	public boolean matchesFilter(FluidStack stack)
	{
		return matchesTable(fluidFilter, stack);
	}

	/** 推出表判定：投递时「该不该收」（OUTPUT 端口与网络 matchesFilter 用）；推出表全空 = 全通 */
	public boolean matchesOutputFilter(FluidStack stack)
	{
		return matchesTable(fluidOutputFilter, stack);
	}

	/**
	 * 单表判定：全空 → 通过（没设过滤就是什么都过），否则任一模板匹配 → 通过。
	 * null / 空流体 / amount <= 0 一律通过（调用方本来也不该拿这种 stack 来问）。
	 */
	private static boolean matchesTable(FluidStack[] table, FluidStack stack)
	{
		if (stack == null || stack.getFluid() == null || stack.amount <= 0)
			return true;

		boolean hasTemplate = false;

		for (int i = 0; i < table.length; i++)
		{
			FluidStack template = table[i];

			// 取不到流体的槽位一律「该槽忽略」，绝不让一个坏模板把整条过滤判定判成 false
			if (template == null || template.getFluid() == null)
				continue;

			hasTemplate = true;

			if (sameFluid(template, stack))
				return true;
		}

		return !hasTemplate;
	}

	/**
	 * 模板比对：{@link FluidStack#isFluidEqual} 是主判据；
	 * 空 tag 与无 tag 在它眼里算「不相等」，而对过滤判定毫无意义，这里抹平（GTNH 的流体常带 tag）。
	 */
	private static boolean sameFluid(FluidStack template, FluidStack stack)
	{
		if (template.isFluidEqual(stack))
			return true;

		return template.getFluid() == stack.getFluid() && isEmptyTag(template.tag) && isEmptyTag(stack.tag);
	}

	private static boolean isEmptyTag(NBTTagCompound tag)
	{
		return tag == null || tag.hasNoTags();
	}

	/**
	 * 容器物品 / NEI 流体显示栈 → 流体模板，四级解析，任何一级拿到非空 FluidStack 就用它：
	 * <ol>
	 * <li>{@link IFluidContainerItem#getFluid} —— 能读出「这个容器里装了什么」</li>
	 * <li>{@link FluidContainerRegistry#getFluidForFilledItem} —— 满桶 / 满单元这类注册过的容器</li>
	 * <li>{@code GTUtility.getFluidFromDisplayStack}（反射）—— NEI 里的「流体」条目（流体显示栈）</li>
	 * <li>{@code GTUtility.getFluidForFilledItem}（反射）—— GT 自己的容器物品</li>
	 * </ol>
	 * 每一级单独 try/catch：某一级抛异常只降级下一级，不会把整条解析链打断。
	 * 全都没解析出流体返回 null —— 该物品不会被当成标记。
	 */
	private static FluidStack resolveFluid(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null)
			return null;

		FluidStack template = resolveByContainerItem(stack);

		if (template == null)
			template = resolveByRegistry(stack);

		if (template == null)
			template = GtFluidSupport.fromDisplayStack(stack);

		if (template == null)
			template = GtFluidSupport.forFilledItem(stack);

		return template;
	}

	/** 第 1 级：IFluidContainerItem#getFluid */
	private static FluidStack resolveByContainerItem(ItemStack stack)
	{
		Item item = stack.getItem();

		if (!(item instanceof IFluidContainerItem))
			return null;

		try
		{
			FluidStack fluid = ((IFluidContainerItem) item).getFluid(stack);
			return fluid != null && fluid.amount > 0 ? normalizeTemplate(fluid) : null;
		}
		catch (Exception e)
		{
			// 外模组的容器物品可能在这个 API 上抛异常：交给下一级
			return null;
		}
	}

	/** 第 2 级：FluidContainerRegistry#getFluidForFilledItem */
	private static FluidStack resolveByRegistry(ItemStack stack)
	{
		try
		{
			return normalizeTemplate(FluidContainerRegistry.getFluidForFilledItem(stack));
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * GT 反射桥：GT 在本工程里只是 compileOnly，**不能**import 任何 gregtech 类，所以两级 GT 解析全走反射。
	 * 类与 Method 只在静态初始化里查一次并缓存（查不到就保持 null），之后每次解析只多一次 Method.invoke；
	 * GT 没装 / 方法被改名 / 调用时抛异常，一律静默降级成「这一级解析不出」，前两级与整条过滤链照常工作。
	 */
	private static final class GtFluidSupport
	{
		/** gregtech.api.util.GTUtility：GT 未加载时为 null */
		private static final Class<?> UTILITY = findClass("gregtech.api.util.GTUtility");
		/** {@code FluidStack getFluidFromDisplayStack(ItemStack)}：NEI 流体显示栈 → 流体 */
		private static final Method FROM_DISPLAY_STACK = findMethod("getFluidFromDisplayStack", ItemStack.class);
		/** {@code FluidStack getFluidForFilledItem(ItemStack, boolean)}：GT 容器物品 → 流体 */
		private static final Method FOR_FILLED_ITEM = findMethod("getFluidForFilledItem", ItemStack.class, boolean.class);

		private static Class<?> findClass(String name)
		{
			try
			{
				// initialize = false：只是查类，不触发 GTUtility 的静态初始化
				return Class.forName(name, false, TileMatterInterfaceFluid.class.getClassLoader());
			}
			catch (Throwable e)
			{
				// 没有 GT（或不带 GTUtility 的版本）：两级 GT 解析一起降级
				return null;
			}
		}

		private static Method findMethod(String name, Class<?>... parameters)
		{
			if (UTILITY == null)
				return null;

			try
			{
				return UTILITY.getMethod(name, parameters);
			}
			catch (Throwable e)
			{
				return null;
			}
		}

		/** 第 3 级：NEI 里的流体显示栈（物品形态的流体条目）→ 流体 */
		private static FluidStack fromDisplayStack(ItemStack stack)
		{
			return invoke(FROM_DISPLAY_STACK, stack);
		}

		/** 第 4 级：GT 自己的容器物品（allowContainer = true，允许空容器参与判定） */
		private static FluidStack forFilledItem(ItemStack stack)
		{
			return invoke(FOR_FILLED_ITEM, stack, Boolean.TRUE);
		}

		private static FluidStack invoke(Method method, Object... arguments)
		{
			if (method == null)
				return null;

			try
			{
				Object result = method.invoke(null, arguments);
				return result instanceof FluidStack ? normalizeTemplate((FluidStack) result) : null;
			}
			catch (Throwable e)
			{
				// InvocationTargetException / IllegalAccess / 返回 null … 全部按「解析不出」处理
				return null;
			}
		}
	}

	/** 模板归一：amount 固定 1，空 tag 归一成 null（见 {@link #sameFluid}），无流体返回 null */
	private static FluidStack normalizeTemplate(FluidStack fluid)
	{
		if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0)
			return null;

		FluidStack template = fluid.copy();
		template.amount = 1;

		if (isEmptyTag(template.tag))
			template.tag = null;

		return template;
	}

	/**
	 * 展示用物品：优先「该流体的容器物品」（水 → 水桶），取不到退到流体方块，两者都没有返回 null。
	 * 只是给槽位渲染一个图标，不参与任何判定；GUI 另有色块兜底。
	 */
	private static ItemStack displayStackFor(FluidStack template)
	{
		Fluid fluid = template.getFluid();

		try
		{
			ItemStack filled = FluidContainerRegistry.fillFluidContainer(
				new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME), new ItemStack(Items.bucket));

			if (filled != null)
				return filled.copy();
		}
		catch (Exception e)
		{
			// 外模组在容器注册表上抛异常只影响「怎么画」，不影响过滤判定
		}

		Block block = fluid.getBlock();

		return block == null ? null : new ItemStack(block);
	}

	/**
	 * 过滤标记 → NBT 列表（存盘与同步包共用同一份格式）：每个非空槽一个 Slot 字节 + FluidStack 自身字段。
	 * 槽位字节就是数组下标，因此同样能装「抽入 9 + 推出 9」的 18 格拼装表（同步包用 0..17，存盘每个键各自 0..8）。
	 */
	public static NBTTagList writeFilterNBT(FluidStack[] templates)
	{
		NBTTagList list = new NBTTagList();

		if (templates == null)
			return list;

		for (int i = 0; i < templates.length; i++)
		{
			FluidStack template = templates[i];

			if (template == null || template.getFluid() == null)
				continue;

			NBTTagCompound entry = new NBTTagCompound();
			entry.setByte("Slot", (byte) i);
			// writeToNBT 会把 tag == null 的模板补成空 tag（改写调用方持有的栈），因此写副本
			template.copy().writeToNBT(entry);
			list.appendTag(entry);
		}

		return list;
	}

	/** NBT 列表 → 过滤标记（存盘与同步包共用）：先清空 out，只认 0..out.length-1 且能解析出的槽 */
	public static void readFilterNBT(NBTTagList list, FluidStack[] out)
	{
		if (out == null)
			return;

		Arrays.fill(out, (FluidStack) null);

		if (list == null)
			return;

		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound entry = list.getCompoundTagAt(i);
			int slot = entry.getByte("Slot") & 255;

			if (slot >= out.length)
				continue;

			FluidStack template = normalizeTemplate(FluidStack.loadFluidStackFromNBT(entry));

			if (template != null)
				out[slot] = template;
		}
	}

	/**
	 * 迁移语义：旧存档只有 {@code FluidFilter} 一个键，它读进**抽入表**；
	 * {@code FluidOutputFilter} 缺失 ⇒ 推出表为空 ⇒ 推出方向全通（不拦），玩家要拦再自己标。
	 */
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		if (nbt.hasKey("FluidFilter"))
		{
			readFilterNBT(nbt.getTagList("FluidFilter", 10), fluidFilter);
		}
		else
		{
			// 更旧的存档：过滤槽里只有「容器物品」这一份标记，就地解析成流体，别让玩家的过滤设置静静地失效
			Arrays.fill(fluidFilter, (FluidStack) null);

			for (int i = 0; i < FILTER_SET_SIZE; i++)
				fluidFilter[i] = resolveFluid(filter[i]);
		}

		readFilterNBT(nbt.getTagList("FluidOutputFilter", 10), fluidOutputFilter);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setTag("FluidFilter", writeFilterNBT(fluidFilter));
		nbt.setTag("FluidOutputFilter", writeFilterNBT(fluidOutputFilter));
	}
}
