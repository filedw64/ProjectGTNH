package moze_intel.projecte.gameObjs.tiles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.util.AxisAlignedBB;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.SyncPedestalPKT;

public class DMPedestalTile extends TileEmc implements IInventory
{
	private boolean isActive = false;
	private ItemStack[] inventory = new ItemStack[1];
	private AxisAlignedBB effectBounds;
	private int particleCooldown = 10;
	private int activityCooldown = 0;
	public double centeredX, centeredY, centeredZ;

	public DMPedestalTile()
	{
		super();
	}

	@Override
	public void updateEntity()
	{
		// 失效检查
		if (this.isInvalid())
		{
			return;
		}

		centeredX = xCoord + 0.5;
		centeredY = yCoord + 0.5;
		centeredZ = zCoord + 0.5;

		if (effectBounds == null)
		{
			effectBounds = AxisAlignedBB.getBoundingBox(centeredX - 4.5, centeredY - 4.5, centeredZ - 4.5,
				centeredX + 4.5, centeredY + 4.5, centeredZ + 4.5);
		}

		if (getActive())
		{
			if (getItemStack() != null)
			{
				Item item = getItemStack().getItem();
				if (item instanceof IPedestalItem)
				{
					((IPedestalItem) item).updateInPedestal(worldObj, xCoord, yCoord, zCoord);
				}

				// 粒子只在客户端生成，防止服务端空转
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
				if (!worldObj.isRemote)
				{
					setActive(false);
				}
			}
		}
	}

	private void spawnParticles()
	{
		// 降低火焰粒子数量，原来是固定的8个，现在改为随机1~2个，位置随机选取
		int flameCount = worldObj.rand.nextInt(2) + 1;
		for (int i = 0; i < flameCount; i++)
		{
			double xOff = (worldObj.rand.nextInt(3) * 0.3) + 0.2; // 0.2, 0.5, 或 0.8
			double zOff = (worldObj.rand.nextInt(3) * 0.3) + 0.2;
			worldObj.spawnParticle("flame", xCoord + xOff, yCoord + 0.3, zCoord + zOff, 0, 0, 0);
		}

		// 将原本的3个传送门粒子减少为1个
		double d1 = (double)((float)yCoord + worldObj.rand.nextFloat());
		int i1 = worldObj.rand.nextInt(2) * 2 - 1;
		int j1 = worldObj.rand.nextInt(2) * 2 - 1;
		double d4 = ((double)worldObj.rand.nextFloat() - 0.5D) * 0.125D;
		double d2 = (double)zCoord + 0.5D + 0.25D * (double)j1;
		double d5 = (double)(worldObj.rand.nextFloat() * 1.0F * (float)j1);
		double d0 = (double)xCoord + 0.5D + 0.25D * (double)i1;
		double d3 = (double)(worldObj.rand.nextFloat() * 1.0F * (float)i1);
		worldObj.spawnParticle("portal", d0, d1, d2, d3, d4, d5);
	}

	public int getActivityCooldown()
	{
		return activityCooldown;
	}

	public void setActivityCooldown(int i)
	{
		activityCooldown = i;
	}

	public void decrementActivityCooldown()
	{
		activityCooldown--;
	}

	public ItemStack getItemStack()
	{
		return getStackInSlot(0);
	}

	public AxisAlignedBB getEffectBounds()
	{
		if (effectBounds == null)
		{
			// Chunk is still loading weirdness, return an empty box just for this tick.
			return AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
		}
		return effectBounds;
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
			if (slot >= 0 && slot < inventory.length)
			{
				inventory[slot] = ItemStack.loadItemStackFromNBT(compound);
			}
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
			if (this.inventory[i] != null)
			{
				NBTTagCompound compound = new NBTTagCompound();
				compound.setByte("Slot", (byte)i);
				this.inventory[i].writeToNBT(compound);
				tagList.appendTag(compound);
			}
		}

		tag.setTag("Items", tagList);
		tag.setBoolean("isActive", getActive());
		tag.setInteger("activityCooldown", activityCooldown);
	}

	@Override
	public int getSizeInventory()
	{
		return inventory.length;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int amt)
	{
		ItemStack result = inventory[slot];
		if (inventory[slot] != null)
		{
			if (amt > inventory[slot].stackSize)
			{
				setInventorySlotContents(slot, null);
			}
			else
			{
				result = inventory[slot].splitStack(amt);
				if (inventory[slot].stackSize <= 0)
				{
					setInventorySlotContents(slot, null);
				}
			}
		}
		return result;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		return inventory[slot];
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack itemStack)
	{
		inventory[slot] = itemStack;

		if (itemStack != null && itemStack.stackSize > this.getInventoryStackLimit())
		{
			itemStack.stackSize = this.getInventoryStackLimit();
		}

		this.markDirty();
	}

	@Override
	public String getInventoryName()
	{
		return "pe.pedestal.shortname";
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer var1)
	{
		// 简化三元运算符
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this && var1.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() { }

	@Override
	public void closeInventory()
	{
		this.markDirty();
	}

	@Override
	public boolean isItemValidForSlot(int p_94041_1_, ItemStack p_94041_2_)
	{
		return true;
	}

	@Override
	public Packet getDescriptionPacket()
	{
		return PacketHandler.getMCPacket(new SyncPedestalPKT(this));
	}

	public boolean getActive()
	{
		return isActive;
	}

	public void setActive(boolean newState)
	{
		if (newState != this.getActive() && worldObj != null)
		{
			if (newState)
			{
				worldObj.playSoundEffect(centeredX, centeredY, centeredZ, "projecte:item.pecharge", 1.0F, 1.0F);
				// 将魔法粒子数量减半 (从10~44减少为 5~14)
				for (int i = 0; i < worldObj.rand.nextInt(10) + 5; ++i)
				{
					this.worldObj.spawnParticle("witchMagic", centeredX + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						yCoord + 1 + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						centeredZ + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						0.0D, 0.0D, 0.0D);
				}
			}
			else
			{
				worldObj.playSoundEffect(centeredX, centeredY, centeredZ, "projecte:item.peuncharge", 1.0F, 1.0F);
				// 将烟雾粒子数量减半 (从10~44减少为 5~14)
				for (int i = 0; i < worldObj.rand.nextInt(10) + 5; ++i)
				{
					this.worldObj.spawnParticle("smoke", centeredX + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						yCoord + 1 + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						centeredZ + worldObj.rand.nextGaussian() * 0.12999999523162842D,
						0.0D, 0.0D, 0.0D);
				}
			}
		}
		this.isActive = newState;
	}
}
