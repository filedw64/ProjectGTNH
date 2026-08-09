package moze_intel.projecte.gameObjs.container.slots.condenser;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.container.CondenserContainer;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;

public class SlotCondenserLock extends Slot
{
	private final CondenserContainer container;

	public SlotCondenserLock(CondenserContainer container, int slotIndex, int xPos, int yPos)
	{
		super(container.tile, slotIndex, xPos, yPos);
		this.container = container;
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		if (EMCHelper.doesItemHaveEmc(stack) && !container.tile.getWorldObj().isRemote)
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
