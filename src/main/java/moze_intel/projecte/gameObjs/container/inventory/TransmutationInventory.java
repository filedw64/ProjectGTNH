package moze_intel.projecte.gameObjs.container.inventory;

import com.google.common.collect.Lists;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.Comparators;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemSearchHelper;
import moze_intel.projecte.config.ProjectEConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;
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

	// 知识库缓存标记
	private List<ItemStack> cachedSortedKnowledge = Lists.newArrayList();
	private boolean knowledgeDirty = true;

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
			knowledgeDirty = true; // 标记缓存失效

			if (is.getItem() == ObjHandler.tome)
			{
				Transmutation.setFullKnowledge(player);
				if (!player.worldObj.isRemote) {
					Transmutation.sync(player); // 只有在吃转化书这种全量修改时才发送完整包
				}
			}
			else
			{
				Transmutation.addKnowledge(is, player);
				if (!player.worldObj.isRemote) {
					Transmutation.syncIncremental(player, is, false); // 发送增量更新
				}
			}
		}

		updateOutputs();
	}

	public static void processNBTTags(ItemStack stack) {
		if (!EMCMapper.enableNBTprocess) {
			stack.stackTagCompound = null;
			return;
		}
		if (stack == null || !stack.hasTagCompound()) return;

		// 白名单过滤逻辑：只保留配置文件中允许的 NBT 键
		NBTTagCompound filtered = ProjectEConfig.getFilteredNBT(stack);
		stack.setTagCompound(filtered);
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
			knowledgeDirty = true; // 标记缓存失效

			Transmutation.removeKnowledge(is, player);

			if (!player.worldObj.isRemote)
			{
				Transmutation.syncIncremental(player, is, true); // 发送增量更新
			}
		}

		updateOutputs();
	}

	public void updateOutputs()
	{
		if (!player.worldObj.isRemote) {
			return;
		}

		// 监听式拉取并排序
		if (knowledgeDirty) {
			cachedSortedKnowledge = Lists.newArrayList(Transmutation.getKnowledge(player));
			cachedSortedKnowledge.sort(Comparators.ITEMSTACK_EMC_DESCENDING);
			knowledge = cachedSortedKnowledge; // 向后兼容
			knowledgeDirty = false;
		}

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

		int matterCounter = 0, fuelCounter = 0;
		int matterPagecounter = 0, fuelPagecounter = 0;
		int matterStartIndex = searchpage * 12;
		int fuelStartIndex = searchpage * 4;

		// 遍历缓存
		for (ItemStack stack : cachedSortedKnowledge)
		{
			// 如果两类输出槽都已填满，直接终止遍历
			if (matterCounter >= 12 && fuelCounter >= 4) {
				break;
			}

			if (EMCHelper.getEmcValue(stack) > reqEmc) {
				continue;
			}

			if (!searchHelper.doesItemMatchFilter(stack)) {
				continue;
			}

			// 分配槽位
			if (FuelMapper.isStackFuel(stack))
			{
				if (fuelPagecounter < fuelStartIndex) {
					fuelPagecounter++;
					continue;
				}
				if (fuelCounter < 4)
				{
					inventory[FUEL_INDEXES[fuelCounter]] = stack;
					fuelCounter++;
				}
			}
			else
			{
				if (matterPagecounter < matterStartIndex) {
					matterPagecounter++;
					continue;
				}
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
		return 27; // 修复：原本为26，但实际上包含了索引26（第27个槽）的Unlearn槽
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

		// 每次打开GUI时强制拉取一次最新知识，避免外部修改未同步
		knowledgeDirty = true;

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
