package projectgtnh.gameObjs.container.slots.condenser;

import projectgtnh.gameObjs.container.CondenserContainer;
import projectgtnh.utils.EMCHelper;
import projectgtnh.utils.ItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotCondenserLock extends Slot 
{
	private CondenserContainer container;

	public SlotCondenserLock(CondenserContainer container, int slotIndex, int xPos, int yPos)
	{
		super(container.tile, slotIndex, xPos, yPos);
		this.container = container;
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		if (stack != null && EMCHelper.doesItemHaveEmc(stack) && !container.tile.getWorldObj().isRemote)
		{
			this.putStack(ItemHelper.getNormalizedStack(stack));
			container.tile.checkLockAndUpdate();
			container.detectAndSendChanges();
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
