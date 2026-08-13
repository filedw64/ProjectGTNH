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

	// 缓存对外暴露的槽位数组，避免高频访问时产生GC内存垃圾
	protected int[] accessibleSlots0;
	protected int[] accessibleSlots1;
	protected int[] accessibleSlotsSide;

	public RMFurnaceTile()
	{
		super(64);
	}

	@Override
	public void updateEntity()
	{
		// 失效检查
		if (this.isInvalid())
		{
			return;
		}

		boolean flag = furnaceBurnTime > 0;
		boolean flag1 = false;

		if (furnaceBurnTime > 0)
		{
			--furnaceBurnTime;
		}

		if (!this.worldObj.isRemote)
		{
			pullFromInventories();
			pushSmeltStack();
		}

		if (!worldObj.isRemote)
		{
			if (canSmelt() && inventory[0] != null && inventory[0].getItem() instanceof IItemEmc itemEmc)
			{
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

				if (furnaceCookTime >= ticksBeforeSmelt)
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
			pushToInventories();
		}
	}

	public boolean isBurning()
	{
		return furnaceBurnTime > 0;
	}

	protected boolean isRMFurnace()
	{
		return !(this instanceof DMFurnaceTile);
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
					stack = inventory[1]; // 更新引用
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
						inventory[i].stackSize += output.stackSize;
						return;
					}
					else
					{
						this.decrStackSize(outputSlot, remain);
						inventory[i].stackSize += remain;
					}
				}
			}
		}
	}

	// 计算能够装下多少该物品
	private int calculateAvailableOutputSpace(ItemStack template)
	{
		int space = 0;
		if (inventory[outputSlot] == null)
		{
			space += template.getMaxStackSize();
		}
		else if (ItemHelper.areItemStacksEqual(inventory[outputSlot], template))
		{
			space += inventory[outputSlot].getMaxStackSize() - inventory[outputSlot].stackSize;
		}

		for (int i = outputStorage[0]; i <= outputStorage[1]; i++)
		{
			if (inventory[i] == null)
			{
				space += template.getMaxStackSize();
			}
			else if (ItemHelper.areItemStacksEqual(inventory[i], template))
			{
				space += inventory[i].getMaxStackSize() - inventory[i].stackSize;
			}
		}
		return space;
	}

	// 将结果分配进输出槽位中
	private void addStackToOutputs(ItemStack result)
	{
		if (inventory[outputSlot] == null)
		{
			int toAdd = Math.min(result.stackSize, result.getMaxStackSize());
			inventory[outputSlot] = result.copy();
			inventory[outputSlot].stackSize = toAdd;
			result.stackSize -= toAdd;
		}
		else if (ItemHelper.areItemStacksEqual(inventory[outputSlot], result))
		{
			int toAdd = Math.min(result.stackSize, inventory[outputSlot].getMaxStackSize() - inventory[outputSlot].stackSize);
			inventory[outputSlot].stackSize += toAdd;
			result.stackSize -= toAdd;
		}

		for (int i = outputStorage[0]; i <= outputStorage[1] && result.stackSize > 0; i++)
		{
			if (inventory[i] == null)
			{
				int toAdd = Math.min(result.stackSize, result.getMaxStackSize());
				inventory[i] = result.copy();
				inventory[i].stackSize = toAdd;
				result.stackSize -= toAdd;
			}
			else if (ItemHelper.areItemStacksEqual(inventory[i], result))
			{
				int toAdd = Math.min(result.stackSize, inventory[i].getMaxStackSize() - inventory[i].stackSize);
				inventory[i].stackSize += toAdd;
				result.stackSize -= toAdd;
			}
		}
	}

	private void smeltItem()
	{
		boolean isRM = isRMFurnace();

		// 先处理主输入槽
		if (!smeltSlot(1) || !isRM)
		{
			// 如果是暗物质熔炉，或者输出已满，只处理这一个槽位即可
			return;
		}

		// 批量熔炼整个储备输入库存
		if (isRM)
		{
			for (int i = inputStorage[0]; i <= inputStorage[1]; i++)
			{
				if (!smeltSlot(i))
				{
					break; // 空间不足，停止熔炼
				}
			}
		}
	}

	private boolean smeltSlot(int slot)
	{
		ItemStack toSmelt = inventory[slot];
		if (toSmelt == null) return true;

		ItemStack smeltResultTemplate = FurnaceRecipes.smelting().getSmeltingResult(toSmelt);
		if (smeltResultTemplate == null) return true;

		boolean isOre = ItemHelper.getOreDictionaryName(toSmelt).startsWith("ore");
		int resultSizePerItem = smeltResultTemplate.stackSize * (isOre ? 2 : 1);

		int itemsToSmelt = toSmelt.stackSize; // 只要堆叠在此，就整组熔炼
		int maxCanFit = calculateAvailableOutputSpace(smeltResultTemplate) / resultSizePerItem;

		// 只消耗能放得下的量
		int actualSmelt = Math.min(itemsToSmelt, maxCanFit);

		if (actualSmelt > 0)
		{
			ItemStack result = smeltResultTemplate.copy();
			result.stackSize = actualSmelt * resultSizePerItem; // 也能安全分配

			addStackToOutputs(result);
			decrStackSize(slot, actualSmelt);
		}

		return actualSmelt == itemsToSmelt;
	}

	private boolean canSmelt()
	{
		if (canSmeltSlot(1)) return true;

		if (isRMFurnace())
		{
			for (int i = inputStorage[0]; i <= inputStorage[1]; i++)
			{
				if (canSmeltSlot(i)) return true;
			}
		}
		return false;
	}

	private boolean canSmeltSlot(int slot)
	{
		ItemStack toSmelt = inventory[slot];
		if (toSmelt == null) return false;

		ItemStack smeltResult = FurnaceRecipes.smelting().getSmeltingResult(toSmelt);
		if (smeltResult == null) return false;

		boolean isOre = ItemHelper.getOreDictionaryName(toSmelt).startsWith("ore");
		int resultSizePerItem = smeltResult.stackSize * (isOre ? 2 : 1);

		return calculateAvailableOutputSpace(smeltResult) >= resultSizePerItem;
	}

	private void pullFromInventories()
	{
		TileEntity tile = this.worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord);

		if (tile instanceof ISidedInventory inv)
		{
			final int side = 0;
			int[] slots = inv.getAccessibleSlotsFromSide(side);

			if (slots.length > 0)
			{
				for (int i : slots)
				{
					ItemStack stack = inv.getStackInSlot(i);
					if (stack == null) continue;

					if (inv.canExtractItem(i, stack, side))
					{
						if (TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc)
						{
							if (inventory[0] == null)
							{
								inventory[0] = stack;
								inv.setInventorySlotContents(i, null);
								break;
							}
							else if (ItemHelper.areItemStacksEqual(stack, inventory[0]))
							{
								int remain = inventory[0].getMaxStackSize() - inventory[0].stackSize;
								if (stack.stackSize <= remain)
								{
									inventory[0].stackSize += stack.stackSize;
									inv.setInventorySlotContents(i, null);
									break;
								}
								else
								{
									inventory[0].stackSize += remain;
									stack.stackSize -= remain;
								}
							}
							continue;
						}

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
				}
			}
		}
		else if (tile instanceof IInventory inv)
		{
			for (int i = 0; i < inv.getSizeInventory(); i++)
			{
				ItemStack stack = inv.getStackInSlot(i);
				if (stack == null) continue;

				if (TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc)
				{
					if (inventory[0] == null)
					{
						inventory[0] = stack;
						inv.setInventorySlotContents(i, null);
						break;
					}
					else if (ItemHelper.areItemStacksEqual(stack, inventory[0]))
					{
						int remain = inventory[0].getMaxStackSize() - inventory[0].stackSize;
						if (stack.stackSize <= remain)
						{
							inventory[0].stackSize += stack.stackSize;
							inv.setInventorySlotContents(i, null);
							break;
						}
						else
						{
							inventory[0].stackSize += remain;
							stack.stackSize -= remain;
						}
					}
					continue;
				}
				else if (FurnaceRecipes.smelting().getSmeltingResult(stack) == null)
				{
					continue;
				}

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
		}
	}

	private void pushToInventories()
	{
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
		{
			if (dir.offsetY > 0) continue;

			int x = this.xCoord + dir.offsetX;
			int y = this.yCoord + dir.offsetY;
			int z = this.zCoord + dir.offsetZ;

			TileEntity tile = this.worldObj.getTileEntity(x, y, z);
			if (tile == null) continue;

			if (tile instanceof ISidedInventory inv)
			{
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

			// 使用 & 255 转换为无符号整型
			int slot = subNBT.getByte("Slot") & 255;
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
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this && var1.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() { }

	@Override
	public void closeInventory() { }

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if (stack == null)
		{
			return false;
		}

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
		// 解决高频分配新数组导致的GC问题
		if (accessibleSlots0 == null)
		{
			accessibleSlots0 = new int[]{15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
			accessibleSlots1 = new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
			accessibleSlotsSide = new int[]{0, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
		}

		return switch (side) {
			case 0 -> accessibleSlots0;
			case 1 -> accessibleSlots1;
			case 2, 3, 4, 5 -> accessibleSlotsSide;
			default -> new int[]{};
		};
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side)
	{
		if (side == 0) return false;
		if (side == 1) return slot <= inputStorage[1] && slot >= inputStorage[0];
		return slot == 0;
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
