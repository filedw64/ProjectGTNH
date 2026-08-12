package moze_intel.projecte.gameObjs.container.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.playerData.AlchemicalBags;

public class AlchBagInventory implements IInventory
{
	private final ItemStack invItem;
	private ItemStack[] inventory;
	private EntityPlayer player;
	private final byte page; // 记录打开时的页数

	public AlchBagInventory(EntityPlayer player, ItemStack stack)
	{
		this.invItem = stack;
		this.player = player;

		// 从炼金袋读取页数
		this.page = stack.hasTagCompound() ? stack.getTagCompound().getByte("BagPage") : 0;

		this.inventory = AlchemicalBags.get(player, (byte) stack.getItemDamage(), this.page);
	}

	@Override
	public int getSizeInventory()
	{
		return 104;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int qty)
	{
		ItemStack stack = getStackInSlot(slot);

		if (stack != null)
		{
			if(stack.stackSize > qty)
			{
				stack = stack.splitStack(qty);
				markDirty();
			}
			else
			{
				setInventorySlotContents(slot, null);
			}
		}

		return stack;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		ItemStack stack = getStackInSlot(slot);

		if (stack != null)
		{
			setInventorySlotContents(slot, null);
		}

		return stack;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		inventory[slot] = stack;

		if (stack != null && stack.stackSize > getInventoryStackLimit())
		{
			stack.stackSize = this.getInventoryStackLimit();
		}

		markDirty();
	}

	@Override
	public String getInventoryName()
	{
		return "item.pe_alchemical_bag_white.name";
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public void markDirty()
	{
		for (int i = 0; i < 104; ++i)
		{
			if (getStackInSlot(i) != null && getStackInSlot(i).stackSize == 0)
			{
				inventory[i] = null;
			}
		}
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return true;
	}

	@Override
	public void openInventory()
	{
	}

	@Override
	public void closeInventory()
	{
		if (!player.worldObj.isRemote)
		{
			// 保存时指定正确的页数
			AlchemicalBags.set(player, (byte) invItem.getItemDamage(), this.page, inventory);
			AlchemicalBags.syncPartial(player, invItem.getItemDamage(), this.page);
		}
	}

	public ItemStack[] getInventory()
	{
		return inventory;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		return true;
	}
}
