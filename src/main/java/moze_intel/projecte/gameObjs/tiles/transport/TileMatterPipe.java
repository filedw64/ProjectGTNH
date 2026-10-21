package moze_intel.projecte.gameObjs.tiles.transport;

import moze_intel.projecte.gameObjs.blocks.transport.BlockMatterPipe;
import moze_intel.projecte.transport.MatterNetworkManager;
import moze_intel.projecte.transport.TransportType;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

/**
 * 管道方块实体：只负责在网络管理器上登记自己，自身不 tick。
 */
public class TileMatterPipe extends TileEntity
{
	private TransportType transportType = TransportType.ITEM;
	private int tier;
	private int color;

	@Override
	public void validate()
	{
		super.validate();
		refresh();
		MatterNetworkManager.INSTANCE.registerPipe(this);
	}

	@Override
	public void invalidate()
	{
		MatterNetworkManager.INSTANCE.unregisterPipe(this);
		super.invalidate();
	}

	@Override
	public void onChunkUnload()
	{
		MatterNetworkManager.INSTANCE.unregisterPipe(this);
		super.onChunkUnload();
	}

	/** 由方块实例与 metadata 重新推导 type / tier / color（染色后由方块调用） */
	public void refresh()
	{
		if (worldObj == null)
			return;

		Block block = worldObj.getBlock(xCoord, yCoord, zCoord);

		if (!(block instanceof BlockMatterPipe))
			return;

		BlockMatterPipe pipe = (BlockMatterPipe) block;
		transportType = pipe.getTransportType();
		tier = pipe.getTier();
		color = worldObj.getBlockMetadata(xCoord, yCoord, zCoord) & 15;
	}

	public TransportType getTransportType()
	{
		return transportType;
	}

	public int getTier()
	{
		return tier;
	}

	public int getColor()
	{
		return color;
	}

	/** 单根管道每秒可搬运的数量，用于网络吞吐求和 */
	public int getThroughputPerSecond()
	{
		return transportType.getPipeThroughputPerSecond(tier);
	}

	/** 同类型 + 同颜色才算同一张网 */
	public boolean isSameChannel(TileMatterPipe other)
	{
		return other != null && other != this && other.transportType == transportType && other.color == color;
	}
}
