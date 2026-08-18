package moze_intel.projecte.gameObjs.tiles;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.gameObjs.blocks.MatterFurnace;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityXPOrb;
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

public class RMFurnaceTile extends TileEmc implements IInventory, ISidedInventory, IEmcAcceptor
{
	private static final float EMC_CONSUMPTION = 1.6F;

	private final ItemStack[] inventory;
	protected final int ticksBeforeSmelt;
	private final int efficiencyBonus;
	private final int outputSlot;
	private final int[] inputStorage;
	private final int[] outputStorage;
	private final float oreDoubleChance;

	public int furnaceBurnTime;
	public int currentItemBurnTime;
	public int furnaceCookTime;
	private int ticksExisted = 0;
	private ItemStack cachedResult;
	private float storedXP = 0;

	private static final int[] inputStorageRM = new int[] {2, 13};
	private static final int[] outputStorageRM = new int[] {15, 26};
	private static final int[] inputStorageDM = new int[] {2, 9};
	private static final int[] outputStorageDM = new int[] {11, 18};

	private static final FurnaceRecipes furnaceRecipes = FurnaceRecipes.smelting();

	public RMFurnaceTile() {
		super(64);
		this.inventory = new ItemStack[27];
		this.ticksBeforeSmelt = 3;
		this.efficiencyBonus = 4;
		this.outputSlot = 14;
		this.inputStorage = inputStorageRM;
		this.outputStorage = outputStorageRM;
		this.oreDoubleChance = 1F;
	}

	// only for DMFurnaceTile
	RMFurnaceTile(boolean ignored) {
		super(64);
		this.inventory = new ItemStack[19];
		this.ticksBeforeSmelt = 10;
		this.efficiencyBonus = 3;
		this.outputSlot = 10;
		this.inputStorage = inputStorageDM;
		this.outputStorage = outputStorageDM;
		this.oreDoubleChance = 0.5F;
	}

	@Override
	public void updateEntity()
	{
		if (this.isInvalid())
			return; // 失效检查

		ticksExisted++;

		final boolean flag = furnaceBurnTime > 0;
		boolean changed = false;

		if (flag)
			--furnaceBurnTime;

		if (worldObj.isRemote)
			return;

		if (ticksExisted % 10 == 0) {
			pullFromInventory(); // 外部容器吸取频率降低为每 10 tick 一次
			pushSmeltStack(); // 每 10 tick 将物品推送到熔炼槽
			pushOutputStack(); // 每 10 tick 将物品推送到熔炼产物槽
			pushToInventory(); // 推送至外部容器的频率降低为每 10 tick 一次
		}

		if (ticksExisted % 20 == 0 && inventory[0] != null && inventory[0].getItem() instanceof IItemEmc itemEmc
			&& itemEmc.getStoredEmc(inventory[0]) > 0)
		{
			final double remain = maximumEMC - currentEMC; // 每 20 tick 提取一次 emc
			if (remain > 0)
				this.addEMC(itemEmc.extractEmc(inventory[0], remain)); // 一次吸取尽可能多的 emc
		}

		final boolean flag2 = canSmelt();

		if (!flag2) return;

		if (furnaceBurnTime == 0) {
			if (currentEMC >= EMC_CONSUMPTION) {
				currentItemBurnTime = 0;
				furnaceBurnTime = 1; // 当燃烧时间耗尽时再尝试消耗 emc 燃烧
				currentEMC -= EMC_CONSUMPTION;
			}
			else {
				currentItemBurnTime = furnaceBurnTime = getItemBurnTime(inventory[0]);
				if (currentItemBurnTime > 0) {
					changed = true;
					if (inventory[0] != null) {
						--inventory[0].stackSize;
						if (inventory[0].stackSize == 0)
							inventory[0] = inventory[0].getItem().getContainerItem(inventory[0]);
					}
				}
			}
		}

		if (furnaceBurnTime > 0) {
			++furnaceCookTime;
			if (furnaceCookTime >= ticksBeforeSmelt) {
				furnaceCookTime = 0;
				smeltItem();
				changed = true;
			}
		}
		else furnaceCookTime = 0; // 到达这里，说明燃料没有供给上，烧炼进度清零

		if (flag != (furnaceBurnTime > 0)) {
			changed = true; // 熔炉的状态发生了改变
			Block block = worldObj.getBlock(xCoord, yCoord, zCoord);
			if (block instanceof MatterFurnace matterFurnace)
				matterFurnace.updateFurnaceBlockState(furnaceBurnTime > 0, worldObj, xCoord, yCoord, zCoord);
		}

		if (changed)
			markDirty();
	}

	public boolean isBurning() {
		return furnaceBurnTime > 0;
	}

	// 将输入槽的物品推送到熔炼槽中
	private void pushSmeltStack() {
		ItemStack stack = inventory[1]; // 熔炼槽中的当前物品堆

		for (int i = inputStorage[0]; i <= inputStorage[1]; i++)
		{
			ItemStack slotStack = inventory[i];

			if (slotStack == null)
				continue;

			if (furnaceRecipes.getSmeltingResult(slotStack) == null)
				continue; // 这个物品不是熔炉的原料

			if (stack == null) {
				inventory[1] = slotStack;
				inventory[i] = null; // 清空这个输入槽
				if (slotStack.getMaxStackSize() <= slotStack.stackSize)
					break; // 熔炼槽满了，跳出循环
				stack = inventory[1]; // 更新引用
				continue; // 这个输入槽已经空了
			}

			final int remain = stack.getMaxStackSize() - stack.stackSize;
			if (remain <= 0) break; // 熔炼槽满了，跳出循环

			if (!ItemHelper.areItemStacksEqual(slotStack, stack))
				continue;

			if (slotStack.stackSize <= remain) {
				inventory[i] = null;
				stack.stackSize += slotStack.stackSize;
				if (slotStack.stackSize == remain)
					break; // 熔炼槽满了，跳出循环
			}
			else {
				this.decrStackSize(i, remain);
				stack.stackSize += remain;
				break; // 熔炼槽满了，跳出循环
			}
		}
	}

	// 将熔炼产物推送到输出槽中
	private void pushOutputStack() {
		ItemStack output = inventory[outputSlot];

		if (output == null) {
			if (cachedResult != null) {
				inventory[outputSlot] = output = cachedResult;
				cachedResult = null;
			}
			else return;
		}

		for (int i = outputStorage[0]; i <= outputStorage[1]; i++)
		{
			ItemStack stack = inventory[i];

			if (stack == null) {
				inventory[i] = output;
				inventory[outputSlot] = null;
				return;
			}

			if (ItemHelper.areItemStacksEqual(output, stack) && stack.stackSize < stack.getMaxStackSize())
			{
				int remain = stack.getMaxStackSize() - stack.stackSize;

				if (output.stackSize <= remain) {
					inventory[outputSlot] = null;
					inventory[i].stackSize += output.stackSize;
					return;
				}
				this.decrStackSize(outputSlot, remain);
				inventory[i].stackSize += remain;
			}
		}
	}

	// 烧炼熔炼槽中的物品
	private void smeltItem() {
		ItemStack toSmelt = inventory[1];
		if (toSmelt == null) return;
		ItemStack smeltResult = furnaceRecipes.getSmeltingResult(toSmelt);
		if (smeltResult == null) return;
		final boolean isOre = ItemHelper.isOre(toSmelt);
		final boolean multiple = isOre && worldObj.rand.nextFloat() <= oreDoubleChance;
		ItemStack resultStack = smeltResult.copy();
		if (multiple)
			resultStack.stackSize *= 2;

		ItemStack currentSmelted = getStackInSlot(outputSlot);
		if (currentSmelted == null)
			setInventorySlotContents(outputSlot, resultStack);
		else {
			currentSmelted.stackSize += smeltResult.stackSize; // canSmelt() 保证了烧炼后能存下一倍的产物
			if (multiple) {
				if (currentSmelted.getMaxStackSize() - currentSmelted.stackSize < smeltResult.stackSize) // 翻倍的那份放不下了
					cachedResult = smeltResult.copy(); // 缓存一份烧炼结果，等有空位了再取出
				else currentSmelted.stackSize += smeltResult.stackSize;
			}
		}
		decrStackSize(1, 1);
		storedXP += furnaceRecipes.func_151398_b(smeltResult) * (multiple ? 2 : 1);
	}

	// 判断能否执行熔炼操作(缓存的烧炼结果被输出，熔炼槽中有物品，能烧炼，产物能堆进输出槽)
	private boolean canSmelt() {
		if (cachedResult != null)
			return false;

		ItemStack toSmelt = inventory[1];
		if (toSmelt == null)
			return false;

		ItemStack smeltResult = furnaceRecipes.getSmeltingResult(toSmelt);
		if (smeltResult == null)
			return false;

		ItemStack currentSmelted = getStackInSlot(outputSlot);

		if (currentSmelted == null)
			return true;
		if (!ItemHelper.areItemStacksEqual(currentSmelted, smeltResult))
			return false;

		final int result = currentSmelted.stackSize + smeltResult.stackSize;
		return result <= currentSmelted.getMaxStackSize();
	}

	// 从上方的 tile 中获取所有可用的物品
	private void pullFromInventory() {
		final TileEntity tile = this.worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord);
		if (tile instanceof ISidedInventory inv)
		{
			final int[] accessibleSlots = inv.getAccessibleSlotsFromSide(0);
			// 0 = The bottom side of the tile pulling from (ForgeDirection.DOWN)

			for (int i = 0, length = accessibleSlots.length; i < length; i++) {
				final int slotId = accessibleSlots[i];
				ItemStack stack = inv.getStackInSlot(slotId);
				if (stack == null) continue;
				if (!inv.canExtractItem(slotId, stack, 0)) continue;
				pullStackFromInventorySlot(inv, slotId, stack);
			}
		}
		else if (tile instanceof IInventory inv)
		{
			for (int slotId = 0, length = inv.getSizeInventory(); slotId < length; slotId++) {
				ItemStack stack = inv.getStackInSlot(slotId);
				if (stack == null) continue;
				pullStackFromInventorySlot(inv, slotId, stack);
			}
		}
	}

	// 从 inv 的 slotId 号槽位取出 stack
	private void pullStackFromInventorySlot(IInventory inv, int slotId, ItemStack stack) {
		if (stack == null) return;
		if (TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc) {
			ItemStack fuelStack = inventory[0];
			if (fuelStack == null) {
				inventory[0] = stack;
				inv.setInventorySlotContents(slotId, null);
			}
			else if (ItemHelper.areItemStacksEqual(stack, fuelStack)) {
				final int remain = fuelStack.getMaxStackSize() - fuelStack.stackSize;
				if (remain > 0) {
					if (stack.stackSize <= remain) {
						fuelStack.stackSize += stack.stackSize;
						inv.setInventorySlotContents(slotId, null);
					}
					else {
						fuelStack.stackSize += remain;
						stack.stackSize -= remain;
					}
				}
			}
			return; // inv 的这一格装的是燃料，不再执行后续逻辑
		}

		if (furnaceRecipes.getSmeltingResult(stack) == null)
			return; // 这个物品没有烧炼配方

		for (int j = inputStorage[0]; j <= inputStorage[1]; j++) // 2~13 槽全部枚举一遍
		{
			ItemStack inputStack = inventory[j];
			if (inputStack == null) {
				inventory[j] = stack;
				inv.setInventorySlotContents(slotId, null);
				return; // inv 的这一格已经空了
			}
			else if (ItemHelper.areItemStacksEqual(stack, inputStack)) {
				final int remain = inputStack.getMaxStackSize() - inputStack.stackSize;
				if (remain <= 0) continue; // 这个输入槽已经满了
				if (stack.stackSize <= remain) {
					inputStack.stackSize += stack.stackSize;
					inv.setInventorySlotContents(slotId, null);
					return; // inv 的这一格已经空了
				}
				else {
					inputStack.stackSize += remain;
					stack.stackSize -= remain;
				}
			}
		}
	}

	private void pushToInventory()
	{
		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
		{
			if (dir.offsetY > 0) continue;

			final int x = this.xCoord + dir.offsetX;
			final int y = this.yCoord + dir.offsetY;
			final int z = this.zCoord + dir.offsetZ;

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

	private int getItemBurnTime(ItemStack stack) {
		final int val = TileEntityFurnace.getItemBurnTime(stack);
		return (val * ticksBeforeSmelt) / 200 * efficiencyBonus;
	}

	// 将存储的经验生成为经验球
	public void spawnXPOrbs(EntityPlayer player) {
		int total = (int) storedXP, toSpawn;
		while (total > 0) {
			toSpawn = EntityXPOrb.getXPSplit(total);
			total -= toSpawn;
			player.worldObj.spawnEntityInWorld(new EntityXPOrb(player.worldObj, player.posX,
				player.posY + 0.5D, player.posZ + 0.5D, toSpawn));
		}
		storedXP = 0;
	}

	@SideOnly(Side.CLIENT)
	public int getCookProgressScaled(int value) {
		return (furnaceCookTime + (isBurning() && canSmelt() ? 1 : 0)) * value / ticksBeforeSmelt;
	}

	@SideOnly(Side.CLIENT)
	public int getBurnTimeRemainingScaled(int value) {
		if (currentItemBurnTime == 0)
			currentItemBurnTime = ticksBeforeSmelt;
		return furnaceBurnTime * value / currentItemBurnTime;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		furnaceBurnTime = nbt.getShort("BurnTime");
		furnaceCookTime = nbt.getShort("CookTime");

		NBTTagList list = nbt.getTagList("Items", 10);
		//inventory = new ItemStack[getSizeInventory()]; // 这行应该是不需要的，因为在创建方块实体实例的时候就已经初始化过了
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound subNBT = list.getCompoundTagAt(i);
			// 使用 & 255 转换为无符号整型
			int slot = subNBT.getByte("Slot") & 255;
			if (slot < getSizeInventory())
				inventory[slot] = ItemStack.loadItemStackFromNBT(subNBT);
		}
		currentItemBurnTime = getItemBurnTime(inventory[0]);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setShort("BurnTime", (short) furnaceBurnTime);
		nbt.setShort("CookTime", (short) furnaceCookTime);

		NBTTagList list = new NBTTagList();
		for (int i = 0; i < getSizeInventory(); i++) {
			if (inventory[i] == null) continue;
			NBTTagCompound subNBT = new NBTTagCompound();
			subNBT.setByte("Slot", (byte) i);
			inventory[i].writeToNBT(subNBT);
			list.appendTag(subNBT);
		}
		nbt.setTag("Items", list);
	}

	@Override
	public int getSizeInventory() {
		return 27;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int qty) {
		if (inventory[slot] == null) return null;
		ItemStack stack = inventory[slot];
		if (stack.stackSize <= qty)
			inventory[slot] = null;
		else {
			stack = stack.splitStack(qty);
			if (stack.stackSize == 0)
				inventory[slot] = null;
		}
		return stack;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		if (inventory[slot] == null) return null;
		ItemStack stack = inventory[slot];
		inventory[slot] = null;
		return stack;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		inventory[slot] = stack;
		if (stack != null && stack.stackSize > this.getInventoryStackLimit())
			stack.stackSize = this.getInventoryStackLimit();
		this.markDirty();
	}

	@Override
	public String getInventoryName() {
		return "pe.rmfurnace.shortname";
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
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
			&& var1.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() {}

	@Override
	public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if (stack == null)
			return false;

		if (slot == 0)
			return TileEntityFurnace.isItemFuel(stack) || stack.getItem() instanceof IItemEmc;
		else if (slot >= 1 && slot <= inputStorage[1])
			return furnaceRecipes.getSmeltingResult(stack) != null;

		return false;
	}

	// 提前构建对外暴露的槽位数组，避免高频访问时产生 GC 内存垃圾
	private final static int[] rmAccessibleSlots0 = new int[]{15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
	private final static int[] rmAccessibleSlots1 = new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
	private final static int[] rmAccessibleSlotsSide = new int[]{0, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
	protected final static int[] invalidAccessibleSlots = new int[0];

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		// 解决高频分配新数组导致的 GC 问题
		return switch (side) {
			case 0 -> rmAccessibleSlots0;
			case 1 -> rmAccessibleSlots1;
			case 2, 3, 4, 5 -> rmAccessibleSlotsSide;
			default -> invalidAccessibleSlots;
		};
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		if (side == 0)
			return false;
		if (side == 1)
			return inputStorage[0] <= slot && slot <= inputStorage[1];
		return slot == 0;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot >= outputSlot;
	}

	@Override
	public double acceptEMC(ForgeDirection side, double toAccept) {
		final double needed = maximumEMC - currentEMC;
		if (needed > 0) {
			final double accept = Math.min(needed, toAccept);
			this.addEMC(accept);
			return accept;
		}
		return 0;
	}
}
