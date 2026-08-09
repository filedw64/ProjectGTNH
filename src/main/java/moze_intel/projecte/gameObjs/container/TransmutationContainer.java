package moze_intel.projecte.gameObjs.container;

import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotInput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotLock;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotUnlearn;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.SearchUpdatePKT;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class TransmutationContainer extends Container
{
	public TransmutationInventory transmutationInventory;

	private final boolean portable;

	public TransmutationContainer(InventoryPlayer invPlayer, TransmutationInventory inventory, boolean portable)
	{
		this.portable = portable;
		this.transmutationInventory = inventory;

		// Transmutation Inventory
		this.addSlotToContainer(new SlotInput(transmutationInventory, 0, 43, 23));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 1, 34, 41));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 2, 52, 41));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 3, 16, 50));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 4, 70, 50));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 5, 34, 59));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 6, 52, 59));
		this.addSlotToContainer(new SlotInput(transmutationInventory, 7, 43, 77));
		this.addSlotToContainer(new SlotLock(transmutationInventory, 8, 158, 50));
		this.addSlotToContainer(new SlotConsume(transmutationInventory, 9, 107, 97));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 10, 123, 30));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 11, 140, 13));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 12, 158, 9));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 13, 176, 13));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 14, 193, 30));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 15, 199, 50));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 16, 193, 70));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 17, 176, 87));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 18, 158, 91));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 19, 140, 87));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 20, 123, 70));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 21, 116, 50));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 22, 158, 31));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 23, 139, 50));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 24, 177, 50));
		this.addSlotToContainer(new SlotOutput(transmutationInventory, 25, 158, 69));
		this.addSlotToContainer(new SlotUnlearn(transmutationInventory, 26, 89, 97));

		//Player Inventory
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 9; j++)
				this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 35 + j * 18, 117 + i * 18));

		//Player Hotbar
		for (int i = 0; i < 9; i++)
			this.addSlotToContainer(new Slot(invPlayer, i, 35 + i * 18, 175));

		transmutationInventory.openInventory();
	}

	@Override
	public boolean canInteractWith(EntityPlayer var1)
	{
		return true;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex)
	{
		Slot slot = this.getSlot(slotIndex);

		if (slot == null || !slot.getHasStack())
		{
			return null;
		}

		ItemStack stack = slot.getStack();
		ItemStack newStack = stack.copy();

		if (slotIndex <= 8 || slotIndex == 26) // Input Slots, Lock Slot, and Unlearn Slot
		{
            if (ItemHelper.hasSpace(player.inventory.mainInventory, stack)) {
                ItemHelper.pushStackInInv(player.inventory, ItemHelper.getNormalizedStack(stack));
                transmutationInventory.setInventorySlotContents(slotIndex, null);
            }
		}
		else if (slotIndex >= 10 && slotIndex <= 25) // Output Slots
		{
            double emc = EMCHelper.getEmcValue(stack);

            int maxStackSize = stack.getMaxStackSize();
			int count = (int) Math.min(maxStackSize, transmutationInventory.emc / emc);

			newStack.stackSize = count;
			if (ItemHelper.hasSpace(player.inventory.mainInventory, newStack)) {
				transmutationInventory.removeEmc(emc * count);
				ItemHelper.pushStackInInv(player.inventory, newStack);
			}
			transmutationInventory.updateOutputs();
		}
		else if (slotIndex >= 27) // Player Inventory
		{
            double emc = EMCHelper.getEmcValue(stack);

			if (emc == 0 && stack.getItem() != ObjHandler.tome)
				return null;

			transmutationInventory.addEmc(emc * stack.stackSize);
			transmutationInventory.handleKnowledge(stack);
			slot.putStack(null);
		}
		return null;
	}

	@Override
	public void onContainerClosed(EntityPlayer player)
	{
		super.onContainerClosed(player);
		transmutationInventory.closeInventory();
	}

	@Override
	public ItemStack slotClick(int slot, int button, int clickType, EntityPlayer player) {
		if (player.worldObj.isRemote && 10 <= slot && slot <= 25)
			PacketHandler.sendToServer(new SearchUpdatePKT(slot, getSlot(slot).getStack()));

		Slot theSlot = null;
		if (slot >= 0)
			theSlot = getSlot(slot); // 被点击的槽位对象

		if (clickType == 4 && theSlot instanceof SlotOutput)
			return null;// 禁止从输出槽位中丢弃物品（又来？）

		if (portable && theSlot != null) {
			// 如果这个页面是由便携式转化桌打开的 且 槽位有效
			ItemStack stack = theSlot.getStack();
			if (stack != null && stack.getItem() == ObjHandler.transmutationTablet && stack == player.getHeldItem()) {
				// 槽位内容物是便携式转化桌，且恰好是玩家手持的那一个
				if (clickType != 3)// 允许鼠标中键复制
					return null;// 禁止任何移动便携式转化桌的行为
			}
		}

		return super.slotClick(slot, button, clickType, player);
	}

	@Override
	public boolean canDragIntoSlot(Slot slot)
	{
		/* 不允许通过拖拽的方式将物品分到这些槽位中 */
		return !(slot instanceof SlotConsume) && !(slot instanceof SlotUnlearn) && !(slot instanceof SlotInput) && !(slot instanceof SlotLock) && !(slot instanceof SlotOutput);
	}
}
