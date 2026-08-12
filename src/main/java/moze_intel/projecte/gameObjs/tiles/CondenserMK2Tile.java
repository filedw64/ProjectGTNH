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
		// 批量销毁输入槽的物品
		for (int i = INPUT_SLOTS_LOWER; i <= INPUT_SLOTS_UPPER; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null)
			{
				continue;
			}

			this.addEMC(EMCHelper.getEmcValue(stack) * stack.stackSize);
			inventory[i] = null;
		}

		// 批量生成输出槽的物品
		if (this.requiredEmc > 0)
		{
			// 计算当前 EMC 一次性可以生成多少个物品
			long maxProduce = (long) (this.getStoredEmc() / this.requiredEmc);

			if (maxProduce > 0 && this.lock != null)
			{
				for (int i = OUTPUT_SLOTS_LOWER; i <= OUTPUT_SLOTS_UPPER && maxProduce > 0; i++)
				{
					ItemStack stack = inventory[i];

					if (stack == null)
					{
						int toAdd = (int) Math.min(maxProduce, lock.getMaxStackSize());
						ItemStack newStack = lock.copy();
						newStack.stackSize = toAdd;
						inventory[i] = newStack;

						this.removeEMC(toAdd * this.requiredEmc);
						maxProduce -= toAdd;
					}
					else if (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize())
					{
						int space = stack.getMaxStackSize() - stack.stackSize;
						int toAdd = (int) Math.min(maxProduce, space);
						stack.stackSize += toAdd;

						this.removeEMC(toAdd * this.requiredEmc);
						maxProduce -= toAdd;
					}
				}
			}
		}
	}

	@Override
	protected boolean hasSpace()
	{
		for (int i = OUTPUT_SLOTS_LOWER; i <= OUTPUT_SLOTS_UPPER; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null || (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize()))
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

			if (stack == null || (isStackEqualToLock(stack) && stack.stackSize < stack.getMaxStackSize()))
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
