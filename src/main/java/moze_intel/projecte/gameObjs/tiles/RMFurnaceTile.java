package moze_intel.projecte.gameObjs.tiles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.Facing;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.gameObjs.blocks.MatterFurnace;
import moze_intel.projecte.utils.ItemHelper;

public class RMFurnaceTile extends TileEmc implements IInventory, ISidedInventory, IEmcAcceptor
{
	private final float EMC_CONSUMPTION = 1.6f;
	public ItemStack[] inventory = new ItemStack[27];
	public int outputSlot = 14;
	public int[] inputStorage = new int[] {2, 13};
	public int[] outputStorage = new int[] {15, 26};
	public int ticksBeforeSmelt = 3;
	public int efficiencyBonus = 4;
	public int furnaceBurnTime;
	public int currentItemBurnTime;
	public int furnaceCookTime;

	private int ticksExisted = 0;

	public RMFurnaceTile()
	{
		super(64);
	}

	@Override
	public void updateEntity()
	{
		ticksExisted++;
		boolean flag = furnaceBurnTime > 0;
		boolean flag1 = false;

		if (furnaceBurnTime > 0)
		{
			--furnaceBurnTime;
		}

		if (!this.worldObj.isRemote)
		{
			// 外部容器吸取降低为每 10 Tick 一次
			if (ticksExisted % 10 == 0)
			{
				pullFromInventories();
			}
			pushSmeltStack();
		}

		if (!worldObj.isRemote)
		{
			if (canSmelt() && inventory[0] != null && inventory[0].getItem() instanceof IItemEmc)
			{
				IItemEmc itemEmc = ((IItemEmc) inventory[0].getItem());
				if (itemEmc.getStoredEmc(inventory[0]) >= EMC_CONSUMPTION)
				{
					itemEmc.extractEmc(inventory[0], EMC_CONSUMPTION);
					this.addEMC(EMC_CONSUMPTION);
				}
			}

			if (this.getStoredEmc() >= EMC_CONSUMPTION)
			{
				furnaceBurnTime = 1;
				this.removeEMC(EMC_CONSUMPTION);
			}

			if (furnaceBurnTime == 0 && canSmelt())
			{
				currentItemBurnTime = furnaceBurnTime = getItemBurnTime(inventory[0]);

				if (furnaceBurnTime > 0)
				{
					flag1 = true;

					if (inventory[0] != null)
					{
						--inventory[0].stackSize;

						if (inventory[0].stackSize == 0)
						{
							inventory[0] = inventory[0].getItem().getContainerItem(inventory[0]);
						}
					}
				}
			}

			if (furnaceBurnTime > 0 && canSmelt())
			{
				++furnaceCookTime;

				if (furnaceCookTime == ticksBeforeSmelt)
				{
					furnaceCookTime = 0;
					smeltItem();
					flag1 = true;
				}
			}

			if (flag != furnaceBurnTime > 0)
			{
				flag1 = true;
				Block block = worldObj.getBlock(xCoord, yCoord, zCoord);

				if (!this.worldObj.isRemote && block instanceof MatterFurnace)
				{
					((MatterFurnace) block).updateFurnaceBlockState(furnaceBurnTime > 0, worldObj, xCoord, yCoord, zCoord);
				}
			}
		}

		if (flag1)
		{
			markDirty();
		}

		if (!this.worldObj.isRemote)
		{
			pushOutput();
			// 向外部容器推送降低为每 10 Tick 一次
			if (ticksExisted % 10 == 0)
			{
				pushToInventories();
			}
		}
	}

	public boolean isBurning()
	{
		return furnaceBurnTime > 0;
	}

	private void pushSmeltStack()
	{
		ItemStack stack = inventory[1];

		for (int i = inputStorage[0]; i <= inputStorage[1]; i++)
		{
			ItemStack slotStack = inventory[i];

			if (slotStack != null && (stack == null || ItemHelper.areItemStacksEqual(slotStack, stack)))
			{
				if (stack == null)
				{
					inventory[1] = slotStack.copy();
					inventory[i] = null;
					stack = inventory[1]; // 继续合并其他槽位的同类物品，而不是直接 break
				}
				else
				{
					int remain = stack.getMaxStackSize() - stack.stackSize;

					if (remain == 0)
					{
						break;
					}
					if (slotStack.stackSize <= remain)
					{
						inventory[i] = null;
						stack.stackSize += slotStack.stackSize;
					}
					else
					{
						this.decrStackSize(i, remain);
						stack.stackSize += remain;
					}
				}
			}
		}
	}

	private void pushOutput()
	{
		ItemStack output = inventory[outputSlot];

		if (output == null)
		{
			return;
		}

		for (int i = outputStorage[0]; i <= outputStorage[1]; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null)
			{
				inventory[i] = output;
				inventory[outputSlot] = null;
				return;
			}
			else
			{
				if (ItemHelper.areItemStacksEqual(output, stack) && stack.stackSize < stack.getMaxStackSize())
				{
					int remain = stack.getMaxStackSize() - stack.stackSize;

					if (output.stackSize <= remain)
					{
						inventory[outputSlot] = null;
						stack.stackSize += output.stackSize;
						return;
					}
					else
					{
						this.decrStackSize(outputSlot, remain);
						stack.stackSize += remain;
					}
				}
			}
		}
	}

	private void pullFromInventories()
	{
		TileEntity tile = this.worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord);
		if (tile == null) return;

		if (tile instanceof ISidedInventory)
		{
			final int side = 0;
			ISidedInventory inv = (ISidedInventory) tile;
			int[] slots = inv.getAccessibleSlotsFromSide(side);

			if (slots.length > 0)
			{
				for (int i : slots)
				{
					ItemStack stack = inv.getStackInSlot(i);
					if (stack == null) continue;

					if (inv.canExtractItem(i, stack, side))
					{
						pullStackToInput(inv, i, stack);
					}
				}
			}
		}
		else if (tile instanceof IInventory)
		{
			IInventory inv = (IInventory) tile;
			for (int i = 0; i < inv.getSizeInventory(); i++)
			{
				ItemStack stack = inv.getStackInSlot(i);
				if (stack == null) continue;

				pullStackToInput(inv, i, stack);
			}
		}
	}

	private void pullStackToInput(IInventory inv, int i, ItemStack stack)
	{
		if (TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc)
		{
			if (inventory[0] == null)
			{
				inventory[0] = stack;
				inv.setInventorySlotContents(i, null);
			}
			else if (ItemHelper.areItemStacksEqual(stack, inventory[0]))
			{
				int remain = inventory[0].getMaxStackSize() - inventory[0].stackSize;
				if (stack.stackSize <= remain)
				{
					inventory[0].stackSize += stack.stackSize;
					inv.setInventorySlotContents(i, null);
				}
				else
				{
					inventory[0].stackSize += remain;
					stack.stackSize -= remain;
				}
			}
			return;
		}

		if (FurnaceRecipes.smelting().getSmeltingResult(stack) == null) return;

		for (int j = inputStorage[0]; j < inputStorage[1]; j++)
		{
			ItemStack otherStack = inventory[j];

			if (otherStack == null)
			{
				inventory[j] = stack;
				inv.setInventorySlotContents(i, null);
				break;
			}
			else if (ItemHelper.areItemStacksEqual(stack, otherStack))
			{
				int remain = otherStack.getMaxStackSize() - otherStack.stackSize;

				if (stack.stackSize <= remain)
				{
					inventory[j].stackSize += stack.stackSize;
					inv.setInventorySlotContents(i, null);
					break;
				}
				else
				{
					inventory[j].stackSize += remain;
					stack.stackSize -= remain;
				}
			}
		}
	}

	private void pushToInventories()
	{
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
		{
			if (dir.offsetY > 0) continue;

			TileEntity tile = this.worldObj.getTileEntity(this.xCoord + dir.offsetX, this.yCoord + dir.offsetY, this.zCoord + dir.offsetZ);
			if (tile == null) continue;

			if (tile instanceof ISidedInventory)
			{
				ISidedInventory inv = (ISidedInventory) tile;
				int[] slots = inv.getAccessibleSlotsFromSide(ForgeDirection.OPPOSITES[dir.ordinal()]);

				if (slots.length > 0)
				{
					for (int j = outputStorage[0]; j < outputStorage[1]; j++)
					{
						ItemStack stack = inventory[j];
						if (stack == null) continue;

						for (int k : slots)
						{
							if (inv.canInsertItem(k, stack, Facing.oppositeSide[dir.ordinal()]))
							{
								ItemStack otherStack = inv.getStackInSlot(k);

								if (otherStack == null)
								{
									inv.setInventorySlotContents(k, stack);
									inventory[j] = null;
									break;
								}
								else if (ItemHelper.areItemStacksEqual(stack, otherStack))
								{
									int remain = otherStack.getMaxStackSize() - otherStack.stackSize;
									if (stack.stackSize <= remain)
									{
										otherStack.stackSize += stack.stackSize;
										inventory[j] = null;
										break;
									}
									else
									{
										otherStack.stackSize += remain;
										inventory[j].stackSize -= remain;
									}
								}
							}
						}
					}
				}
			}
			else if (tile instanceof IInventory)
			{
				for (int j = outputStorage[0]; j <= outputStorage[1]; j++)
				{
					ItemStack stack = inventory[j];
					if (stack != null)
					{
						ItemStack result = ItemHelper.pushStackInInv((IInventory) tile, stack);
						if (result == null)
						{
							inventory[j] = null;
							break;
						}
						else
						{
							inventory[j].stackSize = result.stackSize;
						}
					}
				}
			}
		}
	}

	// 重构的批量熔炼逻辑
	private void smeltItem()
	{
		// 判断当前是暗物质熔炉还是红物质熔炉
		if (this.getSizeInventory() == 19)
		{
			// 暗物质：一次熔炼一组
			if (inventory[1] != null)
			{
				smeltSingleSlot(1, inventory[1].getMaxStackSize());
			}
		}
		else
		{
			// 红物质：一次熔炼整个输入区
			if (inventory[1] != null)
			{
				smeltSingleSlot(1, Integer.MAX_VALUE);
			}
			for (int i = inputStorage[0]; i <= inputStorage[1]; i++)
			{
				if (inventory[i] != null)
				{
					smeltSingleSlot(i, Integer.MAX_VALUE);
				}
			}
		}
	}

	private void smeltSingleSlot(int slot, int maxAmount)
	{
		ItemStack toSmelt = inventory[slot];
		if (toSmelt == null) return;

		ItemStack smeltResultTemplate = FurnaceRecipes.smelting().getSmeltingResult(toSmelt);
		if (smeltResultTemplate == null) return;

		boolean isOre = ItemHelper.getOreDictionaryName(toSmelt).startsWith("ore");
		int multiplier = isOre ? 2 : 1;

		int amountToSmelt = Math.min(toSmelt.stackSize, maxAmount);

		// 提前计算输出槽位的剩余空间，防止“吃掉”多余的矿物
		long availableSpace = 0;
		for (int i = outputSlot; i <= outputSlot; i++)
		{
			ItemStack out = inventory[i];
			if (out == null) availableSpace += smeltResultTemplate.getMaxStackSize();
			else if (out.isItemEqual(smeltResultTemplate)) availableSpace += (out.getMaxStackSize() - out.stackSize);
		}
		for (int i = outputStorage[0]; i <= outputStorage[1]; i++)
		{
			ItemStack out = inventory[i];
			if (out == null) availableSpace += smeltResultTemplate.getMaxStackSize();
			else if (out.isItemEqual(smeltResultTemplate)) availableSpace += (out.getMaxStackSize() - out.stackSize);
		}

		long maxWeCanSmeltBasedOnSpace = availableSpace / multiplier;
		amountToSmelt = (int) Math.min(amountToSmelt, maxWeCanSmeltBasedOnSpace);

		if (amountToSmelt <= 0) return;

		long amountToProduce = (long) amountToSmelt * multiplier;

		// 真正执行产物插入
		amountToProduce = insertIntoOutputSlots(smeltResultTemplate, amountToProduce, outputSlot, outputSlot);
		insertIntoOutputSlots(smeltResultTemplate, amountToProduce, outputStorage[0], outputStorage[1]);

		this.decrStackSize(slot, amountToSmelt);
	}

	private long insertIntoOutputSlots(ItemStack resultTemp, long amount, int startSlot, int endSlot)
	{
		if (amount <= 0) return 0;
		for (int i = startSlot; i <= endSlot; i++)
		{
			ItemStack out = inventory[i];
			if (out == null)
			{
				int toAdd = (int) Math.min(amount, resultTemp.getMaxStackSize());
				ItemStack newStack = resultTemp.copy();
				newStack.stackSize = toAdd;
				inventory[i] = newStack;
				amount -= toAdd;
			}
			else if (out.isItemEqual(resultTemp))
			{
				int space = out.getMaxStackSize() - out.stackSize;
				if (space > 0)
				{
					int toAdd = (int) Math.min(amount, space);
					out.stackSize += toAdd;
					amount -= toAdd;
				}
			}
			if (amount <= 0) break;
		}
		return amount;
	}

	private boolean canSmelt()
	{
		if (inventory[1] == null) return false;

		ItemStack smeltResult = FurnaceRecipes.smelting().getSmeltingResult(inventory[1]);
		if (smeltResult == null) return false;

		// 检查输出槽或输出缓存区是否还有空间
		for (int i = outputSlot; i <= outputSlot; i++)
		{
			ItemStack out = inventory[i];
			if (out == null || (out.isItemEqual(smeltResult) && out.stackSize < out.getMaxStackSize())) return true;
		}
		for (int i = outputStorage[0]; i <= outputStorage[1]; i++)
		{
			ItemStack out = inventory[i];
			if (out == null || (out.isItemEqual(smeltResult) && out.stackSize < out.getMaxStackSize())) return true;
		}

		return false;
	}

	private int getItemBurnTime(ItemStack stack)
	{
		int val = TileEntityFurnace.getItemBurnTime(stack);
		return (val * ticksBeforeSmelt) / 200 * efficiencyBonus;
	}

	@SideOnly(Side.CLIENT)
	public int getCookProgressScaled(int value)
	{
		return (furnaceCookTime + (isBurning() && canSmelt() ? 1 : 0)) * value / ticksBeforeSmelt;
	}

	@SideOnly(Side.CLIENT)
	public int getBurnTimeRemainingScaled(int value)
	{
		if (this.currentItemBurnTime == 0)
			this.currentItemBurnTime = ticksBeforeSmelt;

		return furnaceBurnTime * value / currentItemBurnTime;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		furnaceBurnTime = nbt.getShort("BurnTime");
		furnaceCookTime = nbt.getShort("CookTime");
		currentItemBurnTime = getItemBurnTime(inventory[0]);

		NBTTagList list = nbt.getTagList("Items", 10);
		inventory = new ItemStack[getSizeInventory()];
		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound subNBT = list.getCompoundTagAt(i);
			byte slot = subNBT.getByte("Slot");
			if (slot >= 0 && slot < getSizeInventory())
				inventory[slot] = ItemStack.loadItemStackFromNBT(subNBT);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setShort("BurnTime", (short) furnaceBurnTime);
		nbt.setShort("CookTime", (short) furnaceCookTime);

		NBTTagList list = new NBTTagList();
		for (int i = 0; i < getSizeInventory(); i++)
		{
			if (inventory[i] == null) continue;
			NBTTagCompound subNBT = new NBTTagCompound();
			subNBT.setByte("Slot", (byte) i);
			inventory[i].writeToNBT(subNBT);
			list.appendTag(subNBT);
		}
		nbt.setTag("Items", list);
	}

	@Override
	public int getSizeInventory()
	{
		return 27;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int qty)
	{
		ItemStack stack = inventory[slot];

		if (stack != null)
		{
			if (stack.stackSize <= qty)
			{
				inventory[slot] = null;
			}
			else
			{
				stack = stack.splitStack(qty);

				if (stack.stackSize == 0)
				{
					inventory[slot] = null;
				}
			}
		}
		return stack;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		if (inventory[slot] != null)
		{
			ItemStack stack = inventory[slot];
			inventory[slot] = null;
			return stack;
		}
		return null;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		inventory[slot] = stack;
		if (stack != null && stack.stackSize > this.getInventoryStackLimit())
			stack.stackSize = this.getInventoryStackLimit();
		this.markDirty();
	}

	@Override
	public String getInventoryName()
	{
		return "pe.rmfurnace.shortname";
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
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false : var1.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() {}

	@Override
	public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if (stack == null) return false;

		if (slot == 0)
		{
			return TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc;
		}
		else if (slot >= 1 && slot <= 13)
		{
			return FurnaceRecipes.smelting().getSmeltingResult(stack) != null;
		}

		return false;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		switch(side)
		{
			case 0: return new int[] {15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
			case 1: return new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 , 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
			case 2:
			case 3:
			case 4:
			case 5: return new int[] {0, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
			default: return new int[] {};
		}
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side)
	{
		if (side == 0) return false;

		if (side == 1)
		{
			return slot <= inputStorage[1] && slot >= inputStorage[0];
		}
		else
		{
			return slot == 0;
		}
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side)
	{
		return slot >= outputStorage[0];
	}

	@Override
	public double acceptEMC(ForgeDirection side, double toAccept)
	{
		if (this.getStoredEmc() < EMC_CONSUMPTION)
		{
			double needed = EMC_CONSUMPTION - this.getStoredEmc();
			double accept = Math.min(needed, toAccept);
			this.addEMC(accept);
			return accept;
		}
		return 0;
	}
}
