package moze_intel.projecte.gameObjs.tiles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class DMFurnaceTile extends RMFurnaceTile
{
	public DMFurnaceTile() {
		super(true);
	}

	@Override
	public int getSizeInventory() {
		return 19;
	}

	@SideOnly(Side.CLIENT)
	public int getCookProgressScaled(int value) {
		return furnaceCookTime * value / ticksBeforeSmelt;
	}

	// 提前构建对外暴露的槽位数组，避免高频访问时产生 GC 内存垃圾
	private static final int[] dmAccessibleSlots0 = new int[]{11, 12, 13, 14, 15, 16, 17, 18};
	private static final int[] dmAccessibleSlots1 = new int[]{2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18};
	private static final int[] dmAccessibleSlotsSide = new int[]{0, 11, 12, 13, 14, 15, 16, 17, 18};

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
