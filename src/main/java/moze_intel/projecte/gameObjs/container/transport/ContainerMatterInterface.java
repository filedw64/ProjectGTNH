package moze_intel.projecte.gameObjs.container.transport;

import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.transport.MatterInterfaceButtonPKT;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * 物质接口 GUI 容器：18 格白名单过滤槽（抽入 9 + 推出 9）+ 玩家背包。
 *
 * 过滤槽是「虚拟标记」（ghost slot）：只存一份 size=1 的模板物品，设置标记不消耗玩家手里的物品，
 * 槽里的标记也拿不出来。点击在这里被整体接管，真正的写入走 {@link MatterInterfaceButtonPKT}（FILTER）。
 * 两套过滤表在**槽位索引**上是分开的（0..8 抽入 / 9..17 推出），而它们共用同一组 3x3 屏幕坐标：
 * 不活跃那一套由 GUI 挪到 {@link #FILTER_OFFSCREEN_Y} 屏外，所以同一时刻只有 9 格可点可看。
 */
public class ContainerMatterInterface extends Container
{
	/** 槽位 18 步进：三行背包与快捷栏用同一份数字，行距永远一致 */
	private static final int SLOT_PITCH = 18;
	/** 过滤槽 3x3：与 GUI 六面网格同一套 18 步进 */
	private static final int FILTER_SLOT_X = 62;
	private static final int FILTER_SLOT_Y = 18;
	/** 过滤表套数：抽入 + 推出 */
	private static final int FILTER_SET_COUNT = 2;
	/**
	 * 不活跃那一套过滤槽的屏幕外 Y：GUI 切换标签时把两套坐标互换，
	 * 只有 9 格落在窗口里 —— 命中判定（悬停 / 点击 / NEI 拖入）按坐标走，因此天然只认激活的那一套。
	 */
	public static final int FILTER_OFFSCREEN_Y = -2000;
	private static final int PLAYER_SLOT_X = 8;
	private static final int PLAYER_SLOT_Y = 164;
	private static final int HOTBAR_SLOT_Y = 218;

	private final TileMatterInterface tile;

	public ContainerMatterInterface(InventoryPlayer invPlayer, TileMatterInterface tile)
	{
		this.tile = tile;
		tile.openInventory();

		// 过滤槽：任意物品都可以当模板，但它只是标记，不接受搬运。
		// 两套都用同一组 3x3 坐标；第 2 套（推出，索引 9..17）先摆到屏幕外，等 GUI 按标签互换
		for (int set = 0; set < FILTER_SET_COUNT; set++)
			for (int row = 0; row < 3; row++)
				for (int col = 0; col < 3; col++)
					this.addSlotToContainer(new FilterSlot(tile, set * TileMatterInterface.FILTER_SET_SIZE + row * 3 + col,
						FILTER_SLOT_X + col * SLOT_PITCH,
						(set == 0 ? FILTER_SLOT_Y : FILTER_OFFSCREEN_Y) + row * SLOT_PITCH));

		// 玩家背包：三行 18 步进；整窗 176x244，快捷栏底边 234，下方留 10px
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				this.addSlotToContainer(new Slot(invPlayer, col + row * 9 + 9, PLAYER_SLOT_X + col * SLOT_PITCH, PLAYER_SLOT_Y + row * SLOT_PITCH));

		// 快捷栏
		for (int col = 0; col < 9; col++)
			this.addSlotToContainer(new Slot(invPlayer, col, PLAYER_SLOT_X + col * SLOT_PITCH, HOTBAR_SLOT_Y));
	}

	/**
	 * 过滤槽点击：
	 * 左键手持物品 → 设为该物品的 size=1 副本（NBT 保留），手上一件不少；
	 * 右键 / 空手左键 → 清除标记。
	 * shift-click（mode 1）既不搬入也不搬出，直接吞掉。
	 * 槽位索引自己带表（0..8 抽入 / 9..17 推出），所以这里**不需要**知道客户端正在看哪一套：
	 * 屏幕外那一套根本没有可点击的位置，点进来的索引必然属于激活的那一套。
	 */
	@Override
	public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player)
	{
		if (slotId >= 0 && slotId < TileMatterInterface.FILTER_SIZE)
		{
			if (mode == 1)
				return null;

			return clickFilterSlot(slotId, clickedButton, player);
		}

		return super.slotClick(slotId, clickedButton, mode, player);
	}

	private ItemStack clickFilterSlot(int slot, int clickedButton, EntityPlayer player)
	{
		ItemStack held = player.inventory.getItemStack();
		ItemStack template = clickedButton == 0 && held != null ? held : null;

		boolean remote = player.worldObj != null && player.worldObj.isRemote;

		// 客户端先就地写一份好立刻看到 ghost；服务端侧被原版点击包打到时也要把结果广播出去
		tile.setFilterTemplate(slot, template);

		if (remote)
			PacketHandler.sendToServer(MatterInterfaceButtonPKT.filter(tile.xCoord, tile.yCoord, tile.zCoord, slot, template));
		else
			tile.syncToClients();

		return null;
	}

	@Override
	public void onContainerClosed(EntityPlayer player)
	{
		super.onContainerClosed(player);
		tile.closeInventory();
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex)
	{
		Slot slot = this.getSlot(slotIndex);

		if (slot == null || !slot.getHasStack())
			return null;

		// 过滤槽是虚拟标记：shift-click 既不搬入（isItemValidForSlot = false）也不搬出
		if (slotIndex < TileMatterInterface.FILTER_SIZE)
			return null;

		ItemStack stack = slot.getStack();
		ItemStack copy = stack.copy();
		int mainStart = TileMatterInterface.FILTER_SIZE;
		int hotbarStart = mainStart + 27;

		// 只在背包三行与快捷栏之间倒腾，绝不进过滤槽；目标区间不含来源槽，避免自合并
		boolean moved = slotIndex < hotbarStart
			? this.mergeItemStack(stack, hotbarStart, this.inventorySlots.size(), true)
			: this.mergeItemStack(stack, mainStart, hotbarStart, false);

		if (!moved)
			return null;

		if (stack.stackSize == 0)
			slot.putStack(null);
		else
			slot.onSlotChanged();

		slot.onPickupFromSlot(player, copy);
		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player)
	{
		return tile.isUseableByPlayer(player);
	}

	/**
	 * 过滤槽本身：只把模板画出来（size=1，因此不显示数字），任何搬运路径都拿不到东西。
	 */
	private static final class FilterSlot extends Slot
	{
		private FilterSlot(IInventory inventory, int index, int x, int y)
		{
			super(inventory, index, x, y);
		}

		@Override
		public boolean isItemValid(ItemStack stack)
		{
			return false;
		}

		@Override
		public boolean canTakeStack(EntityPlayer player)
		{
			return false;
		}

		@Override
		public ItemStack decrStackSize(int amount)
		{
			return null;
		}

		/** 标记只认 FILTER 包，外部 putStack（含 NEI 作弊 / 快速搬运）一律丢弃 */
		@Override
		public void putStack(ItemStack stack) {}

		@Override
		public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {}
	}
}
