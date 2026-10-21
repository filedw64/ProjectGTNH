package moze_intel.projecte.gameObjs.tiles.transport;

import moze_intel.projecte.transport.TransportType;
import moze_intel.projecte.transport.api.EndpointMode;
import moze_intel.projecte.transport.api.IItemEndpoint;
import moze_intel.projecte.transport.api.IPortOwner;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * 物品物质接口：每个设了方向的面最多两个物品端口（抽出 / 推入角色各一）。
 * 抽出端口从该面相邻容器抽取物品送入网络，推入端口把网内物品送入该面相邻容器；
 * 双向面两个端口同时存在，各自带自己的操作方向。
 * 所有 simulate 调用都不改动容器状态，也不改动调用方传入的 ItemStack。
 */
public class TileMatterInterfaceItem extends TileMatterInterface
{
	/** 无限位容器用的槽位索引缓存，避免每次分配数组 */
	private int[] plainSlots;

	@Override
	public TransportType getTransportType()
	{
		return TransportType.ITEM;
	}

	@Override
	protected boolean isValidTarget(TileEntity tile)
	{
		return tile instanceof IInventory;
	}

	@Override
	protected IMatterPort createPort(int index, byte accessSide, boolean extract)
	{
		return new ItemPort(index, accessSide, extract ? EndpointMode.INPUT : EndpointMode.OUTPUT);
	}

	/** 单面单角色物品端口：只认本面相邻容器；对象本身长期复用，网络靠身份去重 */
	private final class ItemPort implements IItemEndpoint, IPortOwner, IMatterPort
	{
		private final int index;
		private final EndpointMode mode;
		/** 访问相邻容器时用它的哪一面：0..5 = ForgeDirection ordinal，由本端口独占，与同面另一角色互不影响 */
		private byte accessSide;

		private ItemPort(int index, byte accessSide, EndpointMode mode)
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
			return TileMatterInterfaceItem.this;
		}

		@Override
		public TransportType getTransportType()
		{
			return TileMatterInterfaceItem.this.getTransportType();
		}

		@Override
		public int getNetworkColor()
		{
			return TileMatterInterfaceItem.this.getNetworkColor();
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
			return TileMatterInterfaceItem.this.getPriority();
		}

		@Override
		public int insertItems(ItemStack stack, boolean simulate)
		{
			// 只有推入端口（OUTPUT）受限速 / 限频约束：抽出端口在 MatterNetwork.returnItems 的退回路径上
			// 也会调用本方法，那是守恒的兜底通道，被限速拦掉就会就地掉落到世界
			if (mode != EndpointMode.OUTPUT)
				return insertItemsInto(getSideTarget(index), accessSide, stack, simulate);

			// 推出方向只受【推出表】约束：网络投递前判过一次，直接调用本方法的路径同样必须过这一关
			if (stack == null || stack.stackSize <= 0 || !matchesOutputFilter(stack) || !isPushDue())
				return 0;

			// 网络每轮给的预算是 int（≤ 2^31），单次最多搬 2^31；更大的上限靠多轮累积令牌体现
			long allowance = availableTokens();
			int request = (int) Math.min((long) stack.stackSize, allowance);

			if (request <= 0)
				return 0;

			// 限速时按允许量做副本：调用方传入的 stack 一个字段都不改（simulate 同样零副作用）
			ItemStack offer = request == stack.stackSize ? stack : copyWithSize(stack, request);
			int moved = insertItemsInto(getSideTarget(index), accessSide, offer, simulate);

			if (!simulate && moved > 0)
				onPushed(moved);

			return moved;
		}

		@Override
		public ItemStack extractItems(int maxAmount, boolean simulate)
		{
			return extractItemsFrom(getSideTarget(index), accessSide, maxAmount, simulate);
		}

		@Override
		public boolean matchesFilter(ItemStack stack)
		{
			// 端口按自己的 mode 选表：抽出端口（INPUT）看抽入表，推入端口（OUTPUT）看推出表。
			// 同一方块双向时两个端口各持一张表，互不影响
			return mode == EndpointMode.OUTPUT
				? TileMatterInterfaceItem.this.matchesOutputFilter(stack)
				: TileMatterInterfaceItem.this.matchesFilter(stack);
		}
	}

	/** 把物品插进该面相邻容器（用端口自带的操作方向 accessSide）；只读 stack 的字段，从不写回调用方的 stack */
	int insertItemsInto(TileEntity target, byte accessSide, ItemStack stack, boolean simulate)
	{
		if (stack == null || stack.stackSize <= 0)
			return 0;

		if (!(target instanceof IInventory))
			return 0;

		IInventory inventory = (IInventory) target;
		int side = accessSide;
		int[] slots = getAccessibleSlots(inventory, side);

		if (slots == null || slots.length == 0)
			return 0;

		int limit = inventory.getInventoryStackLimit();
		int remaining = stack.stackSize;

		for (int i = 0; i < slots.length && remaining > 0; i++)
		{
			int slot = slots[i];

			if (!canInsert(inventory, slot, stack, side))
				continue;

			ItemStack current = inventory.getStackInSlot(slot);

			if (current == null)
			{
				int room = Math.min(remaining, Math.min(limit, stack.getMaxStackSize()));

				if (room <= 0)
					continue;

				if (!simulate)
				{
					ItemStack placed = stack.copy();
					placed.stackSize = room;
					inventory.setInventorySlotContents(slot, placed);
				}

				remaining -= room;
			}
			else if (ItemHelper.areItemStacksEqual(current, stack))
			{
				int capacity = Math.min(limit, current.getMaxStackSize());
				int room = Math.min(remaining, capacity - current.stackSize);

				if (room <= 0)
					continue;

				if (!simulate)
				{
					ItemStack grown = current.copy();
					grown.stackSize += room;
					inventory.setInventorySlotContents(slot, grown);
				}

				remaining -= room;
			}
		}

		int inserted = stack.stackSize - remaining;

		if (!simulate && inserted > 0)
			inventory.markDirty();

		return inserted;
	}

	/** 从该面相邻容器抽物品（用端口自带的操作方向 accessSide）；simulate 只读槽位内容，真抽取才 decrStackSize */
	ItemStack extractItemsFrom(TileEntity target, byte accessSide, int maxAmount, boolean simulate)
	{
		if (maxAmount <= 0)
			return null;

		if (!(target instanceof IInventory))
			return null;

		IInventory inventory = (IInventory) target;
		int side = accessSide;
		int[] slots = getAccessibleSlots(inventory, side);

		if (slots == null || slots.length == 0)
			return null;

		for (int i = 0; i < slots.length; i++)
		{
			int slot = slots[i];
			ItemStack current = inventory.getStackInSlot(slot);

			if (current == null || current.stackSize <= 0)
				continue;

			if (!canExtract(inventory, slot, current, side))
				continue;

			// 抽取侧同样受过滤表约束；simulate 与真抽取共用这一条判定，才能选中同一槽
			if (!matchesFilter(current))
				continue;

			int amount = Math.min(maxAmount, current.stackSize);

			if (simulate)
			{
				ItemStack peek = current.copy();
				peek.stackSize = amount;
				return peek;
			}

			ItemStack taken = inventory.decrStackSize(slot, amount);

			if (taken == null || taken.stackSize <= 0)
				continue;

			inventory.markDirty();
			return taken.copy();
		}

		return null;
	}

	/** 限速只搬一部分时造一个受控副本，调用方传入的 stack 保持原样 */
	private static ItemStack copyWithSize(ItemStack stack, int size)
	{
		ItemStack copy = stack.copy();
		copy.stackSize = size;
		return copy;
	}

	/** 抽入表判定：抽取时「该不该抽」（INPUT 端口与网络抽取路径用） */
	public boolean matchesFilter(ItemStack stack)
	{
		return matchesTable(filter, stack);
	}

	/** 推出表判定：投递时「该不该收」（OUTPUT 端口与网络 matchesFilter 用）；推出表全空 = 全通 */
	public boolean matchesOutputFilter(ItemStack stack)
	{
		return matchesTable(outputFilter, stack);
	}

	/** 单表判定：全空 → 通过，否则任一模板匹配 → 通过 */
	private static boolean matchesTable(ItemStack[] table, ItemStack stack)
	{
		if (stack == null)
			return true;

		boolean hasTemplate = false;

		for (int i = 0; i < table.length; i++)
		{
			ItemStack template = table[i];

			if (template == null)
				continue;

			hasTemplate = true;

			if (ItemHelper.areItemStacksEqual(template, stack))
				return true;
		}

		return !hasTemplate;
	}

	private int[] getAccessibleSlots(IInventory inventory, int side)
	{
		if (inventory instanceof ISidedInventory)
			return ((ISidedInventory) inventory).getAccessibleSlotsFromSide(side);

		int size = inventory.getSizeInventory();

		if (plainSlots == null || plainSlots.length != size)
		{
			plainSlots = new int[size];

			for (int i = 0; i < size; i++)
				plainSlots[i] = i;
		}

		return plainSlots;
	}

	private static boolean canInsert(IInventory inventory, int slot, ItemStack stack, int side)
	{
		return !(inventory instanceof ISidedInventory) || ((ISidedInventory) inventory).canInsertItem(slot, stack, side);
	}

	private static boolean canExtract(IInventory inventory, int slot, ItemStack stack, int side)
	{
		return !(inventory instanceof ISidedInventory) || ((ISidedInventory) inventory).canExtractItem(slot, stack, side);
	}
}
