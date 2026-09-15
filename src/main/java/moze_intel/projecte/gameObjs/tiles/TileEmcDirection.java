package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.OrientationSyncPKT;

public abstract class TileEmcDirection extends TileEmc
{
	private ForgeDirection orientation;

	// 静态方向映射表
	private static final int[] FACING_MAP = {
		ForgeDirection.NORTH.ordinal(),
		ForgeDirection.EAST.ordinal(),
		ForgeDirection.SOUTH.ordinal(),
		ForgeDirection.WEST.ordinal()
	};

	public TileEmcDirection() {
		this.orientation = ForgeDirection.SOUTH;
	}

	public ForgeDirection getOrientation() {
		return orientation;
	}

	public void setOrientation(ForgeDirection orientation) {
		this.orientation = orientation;
	}

	public void setOrientation(int orientation) {
		this.orientation = ForgeDirection.getOrientation(orientation);
	}

	public void setRelativeOrientation(EntityLivingBase ent, boolean sendPacket)
	{
		int facing = MathHelper.floor_double(ent.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		// 通过数组映射获取朝向
		int direction = FACING_MAP[facing];

		setOrientation(direction);

		if (sendPacket)
			PacketHandler.sendToAll(new OrientationSyncPKT(this, direction));
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound)
	{
		super.readFromNBT(nbtTagCompound);

		if (nbtTagCompound.hasKey("Direction")) {
			// 使用 & 255 转换为无符号整型
			this.orientation = ForgeDirection.getOrientation(nbtTagCompound.getByte("Direction") & 255);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound) {
		super.writeToNBT(nbtTagCompound);
		nbtTagCompound.setByte("Direction", (byte) orientation.ordinal());
	}

	@Override
	public Packet getDescriptionPacket()
	{
		NBTTagCompound tag = new NBTTagCompound();
		this.writeToNBT(tag);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		this.readFromNBT(packet.func_148857_g());
	}
}
