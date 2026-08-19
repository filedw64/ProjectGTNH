package moze_intel.projecte.gameObjs.tiles;

import moze_intel.projecte.api.tile.IEmcAcceptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TileAEGU extends TileEntity
{
	private final List<ChunkCoordinates> boundCondensers = new ArrayList<>(8);
	private int ticksExisted = 0;
	private boolean isActive = false;

	private static final double AEGU_EMC_GEN = 3920.0;

	@Override
	public void updateEntity()
	{
		if (worldObj.isRemote) return;
		ticksExisted++;

		// 每 20 Tick (1秒) 进行一次自检
		if (ticksExisted % 20 == 0)
		{
			validateEnvironment();
		}

		// 仅当状态激活，且绑定了机器时，进行 EMC 注入
		if (isActive && !boundCondensers.isEmpty())
		{
			double emcPerCondenser = AEGU_EMC_GEN / boundCondensers.size();
			for (ChunkCoordinates coord : boundCondensers)
			{
				TileEntity te = worldObj.getTileEntity(coord.posX, coord.posY, coord.posZ);
				if (te instanceof IEmcAcceptor)
				{
					((IEmcAcceptor) te).acceptEMC(ForgeDirection.UNKNOWN, emcPerCondenser);
				}
			}
		}
	}

	public void validateEnvironment()
	{
		isActive = hasCollectorBelow();

		if (isActive)
		{
			// 校验绑定的聚能阵是否合法或被破坏
			Iterator<ChunkCoordinates> iterator = boundCondensers.iterator();
			while (iterator.hasNext())
			{
				ChunkCoordinates coord = iterator.next();
				TileEntity te = worldObj.getTileEntity(coord.posX, coord.posY, coord.posZ);
				if (!(te instanceof CondenserTile)) // 包括 CondenserMK2Tile
				{
					iterator.remove();
					this.markDirty();
				}
			}
		}

		// 刷新贴图状态
		int currentState = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
		int newState = 0;

		if (isActive)
		{
			if (boundCondensers.isEmpty()) {
				newState = 1; // 有收集器但没绑定机器
			} else {
				newState = 2; // 有收集器且已绑定机器，正在工作
			}
		}

		// 如果状态发生变化，通知世界更新 Meta，标识 3 会同步给客户端并触发区块重绘
		if (currentState != newState)
		{
			worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, newState, 3);
		}
	}

	public boolean hasCollectorBelow()
	{
		TileEntity tileDown = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		return tileDown instanceof CollectorMK1Tile;
	}

	public boolean bindCondenser(int cx, int cy, int cz)
	{
		if (boundCondensers.size() >= 8) return false; // 上限 8 个

		ChunkCoordinates newBind = new ChunkCoordinates(cx, cy, cz);
		if (boundCondensers.contains(newBind)) return false; // 防止重复绑定

		boundCondensers.add(newBind);
		this.markDirty();
		validateEnvironment(); // 绑定成功后立刻校验一次，让待机贴图瞬间变成开机贴图
		return true;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		boundCondensers.clear();
		NBTTagList list = nbt.getTagList("BoundCondensers", 10);
		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound subNBT = list.getCompoundTagAt(i);
			boundCondensers.add(new ChunkCoordinates(subNBT.getInteger("X"), subNBT.getInteger("Y"), subNBT.getInteger("Z")));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		NBTTagList list = new NBTTagList();
		for (ChunkCoordinates coord : boundCondensers)
		{
			NBTTagCompound subNBT = new NBTTagCompound();
			subNBT.setInteger("X", coord.posX);
			subNBT.setInteger("Y", coord.posY);
			subNBT.setInteger("Z", coord.posZ);
			list.appendTag(subNBT);
		}
		nbt.setTag("BoundCondensers", list);
	}
}
