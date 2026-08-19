package moze_intel.projecte.gameObjs.tiles;

import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.SyncPedestalPKT;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.util.AxisAlignedBB;

public class DMPedestalTile extends TileEmc implements IInventory
{
	private boolean isActive = false;
	private ItemStack[] inventory = new ItemStack[1];
	private AxisAlignedBB effectBounds;
	private int particleCooldown = 10;
	private int activityCooldown = 0;
	public double centeredX, centeredY, centeredZ;

	private boolean boundsCalculated = false;

	public DMPedestalTile()
	{
		super();
	}

	@Override
	public void updateEntity()
	{
		if (worldObj.isRemote)
		{
			if (worldObj.getChunkFromBlockCoords(xCoord, zCoord).isEmpty())
			{
				// Handle condition where this method is called even after the clientside chunk has unloaded.
				// This will make IPedestalItems below crash with an NPE since the TE they get back is null
				// Don't you love vanilla???
				return;
			}
		}

		// 优化：坐标和边界框是固定的，只需计算一次，不必每Tick重复计算
		if (!boundsCalculated)
		{
			centeredX = xCoord + 0.5;
			centeredY = yCoord + 0.5;
			centeredZ = zCoord + 0.5;
			effectBounds = AxisAlignedBB.getBoundingBox(centeredX - 4.5, centeredY - 4.5, centeredZ - 4.5,
				centeredX + 4.5, centeredY + 4.5, centeredZ + 4.5);
			boundsCalculated = true;
		}

		if (!getActive()) return;

		if (getItemStack() != null)
		{
			ItemStack stack = getItemStack();
			// 不但要有物品，而且必须是合法的 ProjectE 饰品
			if (stack != null && stack.getItem() instanceof IPedestalItem iPedestalItem)
			{
				iPedestalItem.updateInPedestal(worldObj, xCoord, yCoord, zCoord);

				// 仅在客户端进行粒子运算，切断服务端的无效开销
				if (worldObj.isRemote)
				{
					if (particleCooldown <= 0)
					{
						spawnParticles();
						particleCooldown = 10;
					}
					else
					{
						particleCooldown--;
					}
				}
			}
			else
			{
				// 如果物品被拿走，或者被替换成了普通物品（作为展示台），自动静默关闭
				setActive(false);
			}
			else particleCooldown--;
		}
		else if (!worldObj.isRemote)
			setActive(false);
	}

	private void spawnParticles()
	{
		// 降低火焰粒子数量，原来是固定的8个，现在改为随机 1~2 个，位置随机选取
		final int flameCount = worldObj.rand.nextInt(2) + 1;
		for (int i = 0; i < flameCount; i++)
		{
			double d1 = (float)yCoord + worldObj.rand.nextFloat();
			double d3, d4, d5;
			int i1 = worldObj.rand.nextInt(2) * 2 - 1;
			int j1 = worldObj.rand.nextInt(2) * 2 - 1;
			d4 = ((double)worldObj.rand.nextFloat() - 0.5D) * 0.125D;
			double d2 = (double)zCoord + 0.5D + 0.25D * (double)j1;
			d5 = worldObj.rand.nextFloat() * 1.0F * (float)j1;
			double d0 = (double)xCoord + 0.5D + 0.25D * (double)i1;
			d3 = worldObj.rand.nextFloat() * 1.0F * (float)i1;
			worldObj.spawnParticle("portal", d0, d1, d2, d3, d4, d5);
		}

		// 将原本的3个传送门粒子减少为1个
		final int i1 = worldObj.rand.nextInt(2) * 2 - 1;
		final int j1 = worldObj.rand.nextInt(2) * 2 - 1;
		final double d0 = xCoord + 0.5D + 0.25D * i1;
		final double d1 = yCoord + worldObj.rand.nextFloat();
		final double d2 = zCoord + 0.5D + 0.25D * j1;
		final double d3 = worldObj.rand.nextFloat() * i1;
		final double d4 = (worldObj.rand.nextFloat() - 0.5D) * 0.125D;
		final double d5 = worldObj.rand.nextFloat() * j1;
		worldObj.spawnParticle("portal", d0, d1, d2, d3, d4, d5);
	}

	public int getActivityCooldown() {
		return activityCooldown;
	}

	public void setActivityCooldown(int i) {
		activityCooldown = i;
	}

	public void decrementActivityCooldown() {
		activityCooldown--;
	}

	public ItemStack getItemStack() {
		return getStackInSlot(0);
	}

	public AxisAlignedBB getEffectBounds()
	{
		// Chunk is still loading weirdness, return an empty box just for this tick.
		return effectBounds == null ? AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0) : effectBounds;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag)
	{
		super.readFromNBT(tag);

		inventory = new ItemStack[getSizeInventory()];
		NBTTagList tagList = tag.getTagList("Items", 10);
		for (int i = 0; i < tagList.tagCount(); ++i)
		{
			NBTTagCompound compound = tagList.getCompoundTagAt(i);
			// NBT槽位安全读取
			int slot = compound.getByte("Slot") & 255;
			if (slot < inventory.length)
				inventory[slot] = ItemStack.loadItemStackFromNBT(compound);
		}
		setActive(tag.getBoolean("isActive"));
		activityCooldown = tag.getInteger("activityCooldown");
	}

	@Override
	public void writeToNBT(NBTTagCompound tag)
	{
		super.writeToNBT(tag);

		NBTTagList tagList = new NBTTagList();

		for (int i = 0; i < this.inventory.length; ++i)
		{
			if (this.inventory[i] == null) continue;

			NBTTagCompound compound = new NBTTagCompound();
			compound.setByte("Slot", (byte)i);
			this.inventory[i].writeToNBT(compound);
			tagList.appendTag(compound);
		}

		tag.setTag("Items", tagList);
		tag.setBoolean("isActive", getActive());
		tag.setInteger("activityCooldown", activityCooldown);
	}

	@Override
	public int getSizeInventory() {
		return inventory.length;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int amt)
	{
		ItemStack result = inventory[slot];
		if (inventory[slot] != null)
		{
			if (amt > inventory[slot].stackSize)
				setInventorySlotContents(slot, null);
			else {
				result = inventory[slot].splitStack(amt);
				if (inventory[slot].stackSize <= 0)
					setInventorySlotContents(slot, null);
			}
		}
		return result;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		return inventory[slot];
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack itemStack)
	{
		inventory[slot] = itemStack;

		if (itemStack != null && itemStack.stackSize > this.getInventoryStackLimit())
			itemStack.stackSize = this.getInventoryStackLimit();

		this.markDirty();
	}

	@Override
	public String getInventoryName() {
		return "pe.pedestal.shortname";
	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer var1) {
		// 简化三元运算符
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
			&& var1.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() {}

	@Override
	public void closeInventory() {
		this.markDirty();
	}

	@Override
	public boolean isItemValidForSlot(int p_94041_1_, ItemStack p_94041_2_)
	{
		return true; // 允许放入任何物品作为展示台
	}

	@Override
	public Packet getDescriptionPacket() {
		return PacketHandler.getMCPacket(new SyncPedestalPKT(this));
	}

	public boolean getActive() {
		return isActive;
	}

	private final static double MULTIPLIER = 0.12999999523162842D;

	public void setActive(boolean newState)
	{
		// 如果尝试开启，但里面不是 ProjectE 饰品，则拒绝开启
		if (newState)
		{
			ItemStack stack = getItemStack();
			if (stack == null || !(stack.getItem() instanceof IPedestalItem))
			{
				newState = false;
			}
		}

		if (newState != isActive && worldObj != null)
		{
			if (newState)
			{
				worldObj.playSoundEffect(centeredX, centeredY, centeredZ, "projecte:item.pecharge", 1.0F, 1.0F);

				// 优化：切断服务端的无效粒子计算，仅客户端渲染
				if (worldObj.isRemote)
				{
					for (int i = 0; i < worldObj.rand.nextInt(15) + 10; ++i)
					{
						this.worldObj.spawnParticle("witchMagic", centeredX + worldObj.rand.nextGaussian() * 0.13D,
							yCoord + 1 + worldObj.rand.nextGaussian() * 0.13D,
							centeredZ + worldObj.rand.nextGaussian() * 0.13D,
							0.0D, 0.0D, 0.0D);
					}
				}
			}
			else
			{
				worldObj.playSoundEffect(centeredX, centeredY, centeredZ, "projecte:item.peuncharge", 1.0F, 1.0F);

				if (worldObj.isRemote)
				{
					for (int i = 0; i < worldObj.rand.nextInt(15) + 10; ++i)
					{
						this.worldObj.spawnParticle("smoke", centeredX + worldObj.rand.nextGaussian() * 0.13D,
							yCoord + 1 + worldObj.rand.nextGaussian() * 0.13D,
							centeredZ + worldObj.rand.nextGaussian() * 0.13D,
							0.0D, 0.0D, 0.0D);
					}
				}
			}
		}
		this.isActive = newState;
	}
}
