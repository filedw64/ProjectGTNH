package moze_intel.projecte.gameObjs.tiles;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.api.tile.IEmcProvider;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.RelaySyncPKT;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;

public class RelayMK1Tile extends TileEmc implements IInventory, ISidedInventory, IEmcAcceptor, IEmcProvider
{
	private ItemStack[] inventory;
	private final int invBufferSize;
	private final int chargeRate;
	public int displayEmc;
	public double displayChargingEmc;
	public double displayRawEmc;
	private int numUsing;

	// 缓存对外暴露的槽位数组
	protected int[] accessibleSlots;

	public RelayMK1Tile() {
		super(Constants.RELAY_MK1_MAX);
		chargeRate = Constants.RELAY_MK1_OUTPUT;
		inventory = new ItemStack[8];
		invBufferSize = 6;
	}

	public RelayMK1Tile(int sizeInv, int maxEmc, int chargeRate) {
		super(maxEmc);
		this.chargeRate = chargeRate;
		inventory = new ItemStack[sizeInv + 2];
		invBufferSize = sizeInv;
	}

	@Override
	public void updateEntity()
	{
		// 失效检查
		if (worldObj.isRemote || this.isInvalid())
			return;

		ticksExisted++;

		// 能量传输保持每Tick运行，保证吞吐量
		sendEmc();

		// 将每 Tick 排序降低为每 10 Tick (0.5秒) 排序一次，极大降低 CPU 占用
		if (ticksExisted % 10 == 0)
			sortInventory();

		ItemStack stack = inventory[0];

		if (stack != null)
		{
			if (stack.getItem() instanceof IItemEmc itemEmc)
			{
				double emcVal = itemEmc.getStoredEmc(stack);

				if (emcVal > chargeRate)
					emcVal = chargeRate;

				if (emcVal > 0 && this.getStoredEmc() + emcVal <= this.getMaximumEmc())
				{
					this.addEMC(emcVal);
					itemEmc.extractEmc(stack, emcVal);
				}
			}
			else
			{
				double emcVal = EMCHelper.getEmcValue(stack);

				if (emcVal > 0 && (this.getStoredEmc() + emcVal) <= this.getMaximumEmc())
				{
					this.addEMC(emcVal);
					decrStackSize(0, 1);
				}
			}
		}

		ItemStack chargeable = inventory[getSizeInventory() - 1];

		if (chargeable != null && this.getStoredEmc() > 0 && chargeable.getItem() instanceof IItemEmc)
			chargeItem(chargeable);

		displayEmc = (int) this.getStoredEmc();
		displayChargingEmc = getChargingEMC();
		displayRawEmc = getRawEmc();

		// 将 GUI 同步发包从每秒 20 次降低到每秒 4 次（每 5 Tick），解决打开界面时的网络拥堵
		if (numUsing > 0 && ticksExisted % 5 == 0) {
			PacketHandler.sendToAllAround(new RelaySyncPKT(displayEmc, displayChargingEmc, displayRawEmc, this.xCoord, this.yCoord, this.zCoord),
				new TargetPoint(this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 8));
		}
	}

	private void sendEmc()
	{
		if (this.getStoredEmc() == 0) return;

		if (this.getStoredEmc() <= chargeRate)
			this.sendToAllAcceptors(this.getStoredEmc());
		else this.sendToAllAcceptors(chargeRate);
	}

	private void sortInventory()
	{
		for (int i = 1; i <= invBufferSize; i++)
		{
			ItemStack current = getStackInSlot(i);

			if (current == null)
				continue;

			int nextIndex = i < invBufferSize ? i + 1 : 0;

			ItemStack following = inventory[nextIndex];

			if (following == null)
			{
				inventory[nextIndex] = current;
				decrStackSize(i, current.stackSize);
			}
			else if (ItemHelper.areItemStacksEqual(current, following) && following.stackSize < following.getMaxStackSize())
			{
				int missingForFullStack = following.getMaxStackSize() - following.stackSize;

				if (current.stackSize <= missingForFullStack)
				{
					inventory[nextIndex].stackSize += current.stackSize;
					inventory[i] = null;
				}
				else
				{
					inventory[nextIndex].stackSize += missingForFullStack;
					decrStackSize(i, missingForFullStack);
				}
			}
		}
	}

	private void chargeItem(ItemStack chargeable)
	{
		IItemEmc itemEmc = ((IItemEmc) chargeable.getItem());
		double starEmc = itemEmc.getStoredEmc(chargeable);
		double maxStarEmc = itemEmc.getMaximumEmc(chargeable);
		double toSend = this.getStoredEmc() < chargeRate ? this.getStoredEmc() : chargeRate;

		if (starEmc + toSend > maxStarEmc) {
			toSend = maxStarEmc - starEmc;
		}
		itemEmc.addEmc(chargeable, toSend);
		this.removeEMC(toSend);
	}

	public int getEmcScaled(int i) {
		return (int) Math.round(displayEmc * i / this.getMaximumEmc());
	}

	private double getChargingEMC()
	{
		int index = getSizeInventory() - 1;
		if (inventory[index] != null && inventory[index].getItem() instanceof IItemEmc iItemEmc)
			return iItemEmc.getStoredEmc(inventory[index]);
		return 0;
	}

	public int getChargingEMCScaled(int i)
	{
		int index = getSizeInventory() - 1;
		if (inventory[index] != null && inventory[index].getItem() instanceof IItemEmc iItemEmc)
			return (int) Math.round(displayChargingEmc * i / iItemEmc.getMaximumEmc(inventory[index]));
		return 0;
	}

	private double getRawEmc()
	{
		if (inventory[0] == null)
			return 0;

		if (inventory[0].getItem() instanceof IItemEmc iItemEmc)
			return iItemEmc.getStoredEmc(inventory[0]);

		return EMCHelper.getEmcValue(inventory[0]) * inventory[0].stackSize;
	}

	public int getRawEmcScaled(int i)
	{
		if (inventory[0] == null)
			return 0;

		double emc = EMCHelper.getEmcValue(inventory[0]);
		if (emc <= 0) return 0; // 防范除零或异常

		double emc = EMCHelper.getEmcValue(inventory[0]);
		return MathHelper.floor_double(displayRawEmc * i / (emc * inventory[0].getMaxStackSize()));
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		NBTTagList list = nbt.getTagList("Items", 10);
		inventory = new ItemStack[getSizeInventory()];
		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound subNBT = list.getCompoundTagAt(i);

			// 使用 & 255 转换为无符号整型，防止越界异常
			int slot = subNBT.getByte("Slot") & 255;
			if (slot < getSizeInventory())
				inventory[slot] = ItemStack.loadItemStackFromNBT(subNBT);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);

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
	public int getSizeInventory() {
		return inventory.length;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inventory[slot];
	}

	@Override
	public ItemStack decrStackSize(int slot, int qty)
	{
		ItemStack stack = inventory[slot];
		if (stack == null)
			return stack;

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
	public ItemStack getStackInSlotOnClosing(int slot)
	{
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
		return "pe.relay.mk1";
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
	public boolean isUseableByPlayer(EntityPlayer var1)
	{
		// 优化：简化三元运算符
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this && var1.getDistanceSq((double)this.xCoord + 0.5D, (double)this.yCoord + 0.5D, (double)this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory() {
		numUsing++;
	}

	@Override
	public void closeInventory() {
		numUsing--;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		// 缓存计算结果
		if (accessibleSlots == null)
		{
			accessibleSlots = new int[inventory.length - 2];
			byte counter = 0;

			for (int i = 1; i < inventory.length - 1; i++)
			{
				accessibleSlots[counter] = i;
				counter++;
			}
		}

		return accessibleSlots;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return EMCHelper.doesItemHaveEmc(stack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return false;
	}

	@Override
	public double acceptEMC(ForgeDirection side, double toAccept)
	{
		if (worldObj.getTileEntity(xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ) instanceof RelayMK1Tile)
			return 0; // Do not accept from other relays - avoid infinite loop / thrashing

		double toAdd = Math.min(maximumEMC - currentEMC, toAccept);
		currentEMC += toAdd;
		return toAdd;
	}

	@Override
	public double provideEMC(ForgeDirection side, double toExtract)
	{
		double toRemove = Math.min(currentEMC, toExtract);
		currentEMC -= toRemove;
		return toRemove;
	}
}
