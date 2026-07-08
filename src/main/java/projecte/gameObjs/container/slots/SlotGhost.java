package projecte.gameObjs.container.slots;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import projecte.utils.EMCHelper;
import projecte.utils.ItemHelper;

public class SlotGhost extends Slot
{
	public SlotGhost(IInventory inv, int slotIndex, int xPos, int yPost)
	{
		super(inv, slotIndex, xPos, yPost);
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		if (EMCHelper.doesItemHaveEmc(stack))
		{
			this.putStack(ItemHelper.getNormalizedStack(stack));
		}

		return false;
	}

	@Override
	public boolean canTakeStack(EntityPlayer player)
	{
		return false;
	}

	@Override
	public int getSlotStackLimit()
	{
		return 1;
	}
}
