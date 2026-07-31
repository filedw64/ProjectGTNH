package moze_intel.projecte.gameObjs.container.inventory;

import com.google.common.collect.Lists;
import moze_intel.projecte.api.item.IItemCharge;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.items.GemEternalDensity;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.items.TimeWatch;
import moze_intel.projecte.gameObjs.items.rings.Arcana;
import moze_intel.projecte.integration.EtFuturum.EFRHelper;
import moze_intel.projecte.integration.Forestry.ForestryHelper;
import moze_intel.projecte.integration.GregTech.GTToolHelper;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.Comparators;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemSearchHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TransmutationInventory implements IInventory
{
	public double emc;
	private final EntityPlayer player;
	private static final int LOCK_INDEX = 8;
	private static final int[] MATTER_INDEXES = new int[] {12, 11, 13, 10, 14, 21, 15, 20, 16, 19, 17, 18};
	private static final int[] FUEL_INDEXES = new int[] {22, 23, 24, 25};
	private final ItemStack[] inventory = new ItemStack[27];
	public int learnFlag = 0;
	public int unlearnFlag = 0;
	public String filter = "";
	public int searchpage = 0;
	public List<ItemStack> knowledge = Lists.newArrayList();

	public TransmutationInventory(EntityPlayer player)
	{
		this.player = player;
	}

	public void handleKnowledge(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null) return;

		ItemStack is = stack.copy();
		is.stackSize = 1;

		if (!is.getHasSubtypes() && is.getMaxDamage() != 0)
			is.setItemDamage(0);

		processNBTTags(is);
		if (!Transmutation.hasKnowledgeForStack(is, player))
		{
			learnFlag = 300;
            unlearnFlag = 0;

			if (is.getItem() == ObjHandler.tome)
			{
				Transmutation.setFullKnowledge(player);
			}
			else
			{
				Transmutation.addKnowledge(is, player);
			}

			if (!player.worldObj.isRemote)
			{
				Transmutation.sync(player);
			}
		}

		updateOutputs();
	}

	public static void processNBTTags(ItemStack stack) {
		if (!EMCMapper.enableNBTprocess)
			stack.stackTagCompound = null;
		if (!stack.hasTagCompound()) return;
		if (stack.getItem() instanceof GemEternalDensity) {
			stack.stackTagCompound.removeTag("Target");
			stack.stackTagCompound.removeTag("teleportCooldown");
			stack.stackTagCompound.removeTag("Whitelist");
			stack.stackTagCompound.removeTag("Items");
			stack.stackTagCompound.removeTag("Consumed");
		}
		if (stack.getItem() instanceof ItemPE || stack.getItem() instanceof IItemEmc)
			stack.stackTagCompound.removeTag("StoredEMC");
		if (stack.getItem() instanceof IItemCharge)
			stack.stackTagCompound.removeTag("Charge");
		if (stack.getItem() instanceof IModeChanger)
			stack.stackTagCompound.removeTag("Mode");
		if (stack.getItem() instanceof TimeWatch)
			stack.stackTagCompound.removeTag("TimeMode");
		if (stack.getItem() instanceof Arcana)
			stack.stackTagCompound.removeTag("Active");
		if (EFRHelper.isShulkerBox(stack))
			stack.stackTagCompound.removeTag("Items");
		if (ForestryHelper.isForestryBag(stack)) {
			stack.stackTagCompound.removeTag("UID");
			stack.stackTagCompound.removeTag("Slots");
		}
		if (GTToolHelper.isGTtool(stack)) {
			stack.stackTagCompound.getCompoundTag("GT.ToolStats").setLong("Damage", 0L);
		}
		if (stack.stackTagCompound.hasNoTags())
			stack.stackTagCompound = null;
	}

	public void handleUnlearn(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null) return;

		ItemStack is = stack.copy();
		is.stackSize = 1;

		if (!is.getHasSubtypes() && is.getMaxDamage() != 0)
			is.setItemDamage(0);

		processNBTTags(is);

		if (Transmutation.hasKnowledgeForStack(is, player))
		{
			unlearnFlag = 300;
            learnFlag = 0;

			Transmutation.removeKnowledge(is, player);

			if (!player.worldObj.isRemote)
			{
				Transmutation.sync(player);
			}
		}

		updateOutputs();
	}

	public void checkForUpdates()
	{
        double matterEmc = EMCHelper.getEmcValue(inventory[MATTER_INDEXES[0]]);
        double fuelEmc = EMCHelper.getEmcValue(inventory[FUEL_INDEXES[0]]);

		if (matterEmc > emc || fuelEmc > emc)
		{
			updateOutputs();
		}
	}

	public void updateOutputs()
	{
		if (!player.worldObj.isRemote) {
			return;
		}

		knowledge = Lists.newArrayList(Transmutation.getKnowledge(player));
		knowledge.sort(Comparators.ITEMSTACK_EMC_DESCENDING);

		for (int i : MATTER_INDEXES)
		{
			inventory[i] = null;
		}

		for (int i : FUEL_INDEXES)
		{
			inventory[i] = null;
		}

		ItemSearchHelper searchHelper = ItemSearchHelper.create(filter);

		double reqEmc = 0;
		if (inventory[LOCK_INDEX] != null)
			reqEmc = EMCHelper.getEmcValue(inventory[LOCK_INDEX]);
		if (reqEmc > emc || reqEmc == 0)
			reqEmc = emc;

		Iterator<ItemStack> iter = knowledge.iterator();
		int pagecounter = 0;

		while (iter.hasNext())
		{
			ItemStack stack = iter.next();

			if (EMCHelper.getEmcValue(stack) > reqEmc) {
				iter.remove();
				continue;
			}

			if (!searchHelper.doesItemMatchFilter(stack)) {
				iter.remove();
				continue;
			}

			if (pagecounter < (searchpage * 12)) {
				pagecounter++;
				iter.remove();
			}
		}

		int matterCounter = 0, fuelCounter = 0;

		for (ItemStack stack : knowledge)
		{
			if (FuelMapper.isStackFuel(stack))
			{
				if (fuelCounter < 4)
				{
					inventory[FUEL_INDEXES[fuelCounter]] = stack;
					fuelCounter++;
				}
			}
			else
			{
				if (matterCounter < 12)
				{
					inventory[MATTER_INDEXES[matterCounter]] = stack;
					matterCounter++;
 				}
			}
		}
	}

	public void writeIntoOutputSlot(int slot, ItemStack item)
	{
		if (EMCHelper.doesItemHaveEmc(item) && EMCHelper.getEmcValue(item) <= this.emc && Transmutation.hasKnowledgeForStack(item, player))
		{
			inventory[slot] = item;
		}
		else
		{
			inventory[slot] = null;
		}
	}

	public List<ItemStack> getOutputSlots() {
		return Arrays.asList(inventory).subList(10,26);
	}

	@Override
	public int getSizeInventory()
	{
		return 26;
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
		{
			stack.stackSize = this.getInventoryStackLimit();
		}
	}

	@Override
	public String getInventoryName()
	{
		return "item.pe_transmutation_tablet.name";
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
		return true;
	}

	@Override
	public void openInventory()
	{
		emc = Transmutation.getEmc(player);
		ItemStack[] inputLocks = Transmutation.getInputsAndLock(player);
		System.arraycopy(inputLocks, 0, inventory, 0, 9);
		if (player.worldObj.isRemote)
		{
			updateOutputs();
		}
	}

	@Override
	public void closeInventory()
	{
		if (!player.worldObj.isRemote)
		{
			Transmutation.setEmc(player, emc);
			Transmutation.setInputsAndLocks(Arrays.copyOfRange(inventory, 0, 9), player);
			Transmutation.sync(player);
		}
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		return false;
	}

	@Override
	public void markDirty() {}

	public void addEmc(double value)
	{
		emc += value;

		if (emc > Constants.TILE_MAX_EMC)
		{
			emc = Constants.TILE_MAX_EMC;
		}
	}

	public void removeEmc(double value)
	{
		emc -= value;

		if (emc < 0)
		{
			emc = 0;
		}
	}

	public boolean hasMaxedEmc()
	{
		return emc >= Constants.TILE_MAX_EMC;
	}
}