package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.item.ItemStack;
import moze_intel.projecte.utils.EMCHelper;

public class CondenserMK2Tile extends CondenserTile
{
	private static final int LOCK_SLOT = 0;
	private static final int INPUT_SLOTS_LOWER = 1;
	private static final int INPUT_SLOTS_UPPER = 42;
	private static final int OUTPUT_SLOTS_LOWER = 43;
	private static final int OUTPUT_SLOTS_UPPER = 84;

	public CondenserMK2Tile()
	{
		this.inventory = new ItemStack[85];
		this.loadChecks = false;
	}

	@Override
	protected void condense()
	{
		// 废弃了原版 while 循环单次生成 1 个物品的致命性能 Bug
		// 改为批量计算并整组生成
		if (requiredEmc > 0 && this.getStoredEmc() >= requiredEmc)
		{
			// 计算当前 EMC 可以生成多少个目标物品
			int itemsToProduce = (int) (this.getStoredEmc() / requiredEmc);
			int itemsProduced = 0;

			for (int i = OUTPUT_SLOTS_LOWER; i <= OUTPUT_SLOTS_UPPER; i++)
			{
				if (itemsProduced >= itemsToProduce)
				{
					break;
				}

				ItemStack stack = inventory[i];
				if (stack == null)
				{
					int toAdd = Math.min(itemsToProduce - itemsProduced, inventory[LOCK_SLOT].getMaxStackSize());
					inventory[i] = inventory[LOCK_SLOT].copy();
					inventory[i].stackSize = toAdd;
					itemsProduced += toAdd;
				}
				else if (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize())
				{
					int space = stack.getMaxStackSize() - stack.stackSize;
					int toAdd = Math.min(itemsToProduce - itemsProduced, space);
					stack.stackSize += toAdd;
					itemsProduced += toAdd;
				}
			}

			// 一次性扣除消耗的 EMC 并标记更新
			if (itemsProduced > 0)
			{
				this.removeEMC(itemsProduced * requiredEmc);
				this.markDirty();
			}
		}

		// 消耗输入槽位的物品
		if (this.hasSpace())
		{
			for (int i = INPUT_SLOTS_LOWER; i <= INPUT_SLOTS_UPPER; i++)
			{
				ItemStack stack = inventory[i];

				if (stack == null)
				{
					continue;
				}

				this.addEMC(EMCHelper.getEmcValue(stack) * stack.stackSize);
				inventory[i] = null;
				break;
			}
		}
	}

	@Override
	protected boolean hasSpace()
	{
		for (int i = OUTPUT_SLOTS_LOWER; i <= OUTPUT_SLOTS_UPPER; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null)
			{
				return true;
			}

			if (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize())
			{
				return true;
			}
		}

		return false;
	}

	@Override
	protected int getSlotForStack()
	{
		for (int i = OUTPUT_SLOTS_LOWER; i <= OUTPUT_SLOTS_UPPER; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null)
			{
				return i;
			}

			if (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize())
			{
				return i;
			}
		}

		return 0;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if (slot == LOCK_SLOT || slot >= OUTPUT_SLOTS_LOWER)
		{
			return false;
		}

		return !isStackEqualToLock(stack) && EMCHelper.doesItemHaveEmc(stack);
	}

	@Override
	public String getInventoryName()
	{
		return "tile.pe_condenser_mk2.name";
	}
}
