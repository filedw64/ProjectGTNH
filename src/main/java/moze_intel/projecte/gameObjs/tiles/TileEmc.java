package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.api.tile.IEmcProvider;
import moze_intel.projecte.api.tile.TileEmcBase;
import moze_intel.projecte.utils.Constants;

public abstract class TileEmc extends TileEmcBase
{
	public TileEmc()
	{
		setMaximumEMC(Constants.TILE_MAX_EMC);
	}

	public TileEmc(int maxAmount)
	{
		setMaximumEMC(maxAmount);
	}

	public boolean hasMaxedEmc()
	{
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
		if (!(this instanceof IEmcProvider))
		{
			throw new UnsupportedOperationException("sending without being a provider");
		}

		if (emc <= 0)
		{
			return;
		}

		// 废弃高内存开销的 Map 包装和 Predicate 过滤
		// 采用零对象分配（Zero-Allocation）的数组缓存机制
		TileEntity[] acceptors = new TileEntity[6];
		ForgeDirection[] directions = new ForgeDirection[6];
		int validCount = 0;

		// 第一次遍历找出周围有效的接收器并计数，避免除以0，避免 Map.size() 的开销
		for (int i = 0; i < 6; i++)
		{
			ForgeDirection dir = ForgeDirection.getOrientation(i);
			TileEntity tile = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);

			if (tile instanceof IEmcAcceptor)
			{
				if (this instanceof RelayMK1Tile && tile instanceof RelayMK1Tile)
				{
					continue;
				}

				acceptors[validCount] = tile;
				directions[validCount] = dir;
				validCount++;
			}
		}

		// 如果周围没有任何接收器，直接终止
		if (validCount == 0)
		{
			return;
		}

		// 平分 EMC
		double emcPer = emc / validCount;

		// 第二次遍历发送能量并回收多余的能量
		for (int i = 0; i < validCount; i++)
		{
			TileEntity tile = acceptors[i];
			ForgeDirection dir = directions[i];

			double provide = ((IEmcProvider) this).provideEMC(dir.getOpposite(), emcPer);
			double remain = provide - ((IEmcAcceptor) tile).acceptEMC(dir, provide);
			this.addEMC(remain);
		}
	}
}
