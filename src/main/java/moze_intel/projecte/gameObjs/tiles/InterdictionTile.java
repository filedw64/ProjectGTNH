package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import moze_intel.projecte.utils.WorldHelper;

public class InterdictionTile extends TileEntity
{
	private AxisAlignedBB effectBounds = null;

	private int tickOffset = 0;

	public void updateEntity() {
		if (effectBounds == null) {
			effectBounds = AxisAlignedBB.getBoundingBox(xCoord - 8, yCoord - 8, zCoord - 8,
				xCoord + 8, yCoord + 8, zCoord + 8);
			tickOffset = Math.abs((xCoord * 31 + yCoord * 7 + zCoord) % 2);
		}
		if ((worldObj.getTotalWorldTime() + tickOffset) % 2 != 0) return;
		WorldHelper.repelEntitiesInAABBFromPoint(worldObj, effectBounds,
			xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, false);
	}
}
