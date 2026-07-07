package projecte.gameObjs.container.slots.mercurial;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import projecte.gameObjs.items.KleinStar;

public class SlotMercurialKlein extends Slot
{
	public SlotMercurialKlein(IInventory par1iInventory, int par2, int par3, int par4)
	{
		super(par1iInventory, par2, par3, par4);
	}

	@Override
	public boolean isItemValid(ItemStack stack)
	{
		return stack.getItem() instanceof KleinStar;
	}
}
