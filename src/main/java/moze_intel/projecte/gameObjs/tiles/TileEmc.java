package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.api.tile.IEmcProvider;
import moze_intel.projecte.api.tile.TileEmcBase;
import moze_intel.projecte.utils.Constants;

public abstract class TileEmc extends TileEmcBase
{
	public TileEmc() {
		setMaximumEMC(Constants.TILE_MAX_EMC);
	}

	public TileEmc(int maxAmount) {
		setMaximumEMC(maxAmount);
	}

	public boolean hasMaxedEmc() {
		return getStoredEmc() >= getMaximumEmc();
	}

	/**
	 * The amount provided will be divided and evenly distributed as best as possible between adjacent IEMCAcceptors
	 * Remainder or rejected EMC is added back to this provider
	 *
	 * @param emc The maximum combined emc to send to others
	 */
	public void sendToAllAcceptors(double emc)
	{
		if (!(this instanceof IEmcProvider provider))
			throw new UnsupportedOperationException("sending without being a provider");

		// 废弃了耗费性能的 WorldHelper.getAdjacentTileEntitiesMapped 以及 Guava 的 Maps.filterValues。
		// 采用双重循环策略
		int acceptorCount = 0;
		boolean isRelay = this instanceof RelayMK1Tile;

		// 计算周围有效的 EMC 接收者数量
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			TileEntity tile = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			if (tile instanceof IEmcAcceptor) {
				if (isRelay && tile instanceof RelayMK1Tile)
					continue; // 继电器之间不互相传输
				acceptorCount++;
			}
		}

		if (acceptorCount == 0)
			return;

		double emcPer = emc / acceptorCount;

		// 平均分配 EMC 并回收溢出部分
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
		{
			TileEntity tile = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);

			if (!(tile instanceof IEmcAcceptor acceptor)) continue;

			if (isRelay && tile instanceof RelayMK1Tile)
				continue;

			double provided = provider.provideEMC(dir.getOpposite(), emcPer);
			double accepted = acceptor.acceptEMC(dir, provided);
			double remain = provided - accepted;

			if (remain > 0)
				this.addEMC(remain);
		}
	}
}
