package moze_intel.projecte.gameObjs.tiles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;
import moze_intel.projecte.api.item.IItemEmc;

public class DMFurnaceTile extends RMFurnaceTile implements IInventory, ISidedInventory
{
	public DMFurnaceTile()
	{
		this.inventory = new ItemStack[19];
		this.ticksBeforeSmelt = 10;
		this.efficiencyBonus = 3;
		this.outputSlot = 10;
		this.inputStorage = new int[] {2, 9};
		this.outputStorage = new int[] {11, 18};
	}

	@Override
	public int getSizeInventory() {
		return 19;
	}

	@SideOnly(Side.CLIENT)
	public int getCookProgressScaled(int value) {
		return furnaceCookTime * value / ticksBeforeSmelt;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if (stack == null)
			return false;

		if (slot == 0)
			return TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc;
		else if (slot >= 1 && slot <= 9)
			return FurnaceRecipes.smelting().getSmeltingResult(stack) != null;

		return false;
	}

	// 提前构建对外暴露的槽位数组，避免高频访问时产生 GC 内存垃圾
	private final static int[] dmAccessibleSlots0 = new int[]{11, 12, 13, 14, 15, 16, 17, 18};
	private final static int[] dmAccessibleSlots1 = new int[]{2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18};
	private final static int[] dmAccessibleSlotsSide = new int[]{0, 11, 12, 13, 14, 15, 16, 17, 18};

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return switch (side) {
			case 0 -> dmAccessibleSlots0;
			case 1 -> dmAccessibleSlots1;
			case 2, 3, 4, 5 -> dmAccessibleSlotsSide;
			default -> invalidAccessibleSlots;
		};
	}

	@Override
	public String getInventoryName() {
		return "pe.dmfurnace.shortname";
	}
}
