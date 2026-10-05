package moze_intel.projecte.gameObjs.container.inventory;

import moze_intel.projecte.math.ExactEMC;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.Comparators;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.ItemSearchHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransmutationInventory implements IInventory {
	private final EntityPlayer player;
	private static final int LOCK_INDEX = 8;
	private static final int[] MATTER_INDEXES = new int[] {12, 11, 13, 10, 14, 21, 15, 20, 16, 19, 17, 18};
	private static final int[] FUEL_INDEXES = new int[] {22, 23, 24, 25};

	private final ItemStack[] inventory = new ItemStack[27];
	public int learnFlag = 0, unlearnFlag = 0;
	public String filter = "";
	public int searchpage = 0;
	/** Compatibility field removed: all balance reads use the player property. */
    public ExactEMC getEmcExact() { return Transmutation.getEmcExact(player); }
    public void invalidateSearchCache() { knowledgeDirty = true; lastFilter = null; }

	// 双轨知识库缓存
	private List<ItemStack> cachedMatter = new ArrayList<>();
	private List<ItemStack> cachedFuel = new ArrayList<>();

	// 渐进搜索缓存
	private List<ItemStack> filteredMatter = new ArrayList<>();
	private List<ItemStack> filteredFuel = new ArrayList<>();
	private String lastFilter = null;
	private ExactEMC lastSearchBalance;
	private boolean knowledgeDirty = true; // 知识库缓存标记

	public TransmutationInventory(EntityPlayer player)
	{
		this.player = player;
	}

	public void handleKnowledge(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return;

		ItemStack is = stack.copy();
		is.stackSize = 1;

		if (!is.getHasSubtypes() && is.getMaxDamage() != 0)
			is.setItemDamage(0);

		processNBTTags(is);
		if (!Transmutation.hasKnowledgeForStack(is, player)) {
			learnFlag = 300;
			unlearnFlag = 0;
			knowledgeDirty = true;

			if (is.getItem() == ObjHandler.tome) {
				Transmutation.setFullKnowledge(player);
				if (!player.worldObj.isRemote)
					Transmutation.sync(player); // 只有在吃转化书这种全量修改时才发送完整包
			}
			else {
				Transmutation.addKnowledge(is, player);
				/*if (!player.worldObj.isRemote) {
					Transmutation.syncIncremental(player, is, false); // 发送增量更新
				}*/
			}
		}

		updateOutputs();
	}

	public static void processNBTTags(ItemStack stack) {
		if (stack == null || stack.stackTagCompound == null) return;

		if (!EMCMapper.enableNBTprocess || stack.stackTagCompound.hasNoTags()) {
			stack.stackTagCompound = null;
			return;
		}

		// 白名单过滤逻辑：只保留配置文件中允许的 NBT 键
		// 以及 ench, StoredEnchantments, RepairCost, display
		NBTTagCompound res = ItemHelper.filterNBT(stack);
		if (res == null)
			res = new NBTTagCompound();

		NBTBase tag;
		if ((tag = stack.stackTagCompound.getTag("ench")) != null)
			res.setTag("ench", tag.copy());
		if ((tag = stack.stackTagCompound.getTag("StoredEnchantments")) != null)
			res.setTag("StoredEnchantments", tag.copy());
		if ((tag = stack.stackTagCompound.getTag("RepairCost")) != null)
			res.setTag("RepairCost", tag.copy());
		if ((tag = stack.stackTagCompound.getTag("display")) != null)
			res.setTag("display", tag.copy());

		stack.stackTagCompound = res.hasNoTags() ? null : res;
	}

	public void handleUnlearn(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return;

		ItemStack is = stack.copy();
		is.stackSize = 1;

		if (!is.getHasSubtypes() && is.getMaxDamage() != 0)
			is.setItemDamage(0);

		processNBTTags(is);

		if (Transmutation.hasKnowledgeForStack(is, player)) {
			unlearnFlag = 300;
			learnFlag = 0;
			knowledgeDirty = true;

			Transmutation.removeKnowledge(is, player);
			/*if (!player.worldObj.isRemote) {
				Transmutation.syncIncremental(player, is, true); // 发送增量更新
			}*/
		}

		updateOutputs();
	}

	// 更新搜索缓存并分类整理知识库
	private void updateSearchCache() {
		if (filter == null) filter = "";

		// 如果知识库没变，且搜索词也没变，直接返回缓存
		if (!knowledgeDirty && filter.equals(lastFilter) && getEmcExact().equals(lastSearchBalance)) return;

		if (knowledgeDirty) {
			cachedMatter.clear();
			cachedFuel.clear();
			for (ItemStack stack : Transmutation.getKnowledge(player)) {
				if (FuelMapper.isStackFuel(stack)) cachedFuel.add(stack);
				else cachedMatter.add(stack);
			}
			// 双轨严格按照 EMC 降序排序
			cachedMatter.sort(Comparators.ITEMSTACK_EMC_DESCENDING);
			cachedFuel.sort(Comparators.ITEMSTACK_EMC_DESCENDING);
			knowledgeDirty = false;
			lastFilter = null; // 强制刷新搜索
		}

		if (filter.isEmpty()) {
			filteredMatter = cachedMatter;
			filteredFuel = cachedFuel;
		}
		else {
			ItemSearchHelper searchHelper = ItemSearchHelper.create(filter, getEmcExact());

			// 如果新搜索词是以旧词开头的，就在上次过滤的结果上继续搜
			List<ItemStack> sourceMatter = (lastFilter != null && filter.startsWith(lastFilter)) ? filteredMatter : cachedMatter;
			List<ItemStack> sourceFuel = (lastFilter != null && filter.startsWith(lastFilter)) ? filteredFuel : cachedFuel;

			filteredMatter = new ArrayList<>();
			for (ItemStack s : sourceMatter)
				if (searchHelper.doesItemMatchFilter(s))
					filteredMatter.add(s);

			filteredFuel = new ArrayList<>();
			for (ItemStack s : sourceFuel)
				if (searchHelper.doesItemMatchFilter(s))
					filteredFuel.add(s);
		}
		lastFilter = filter;
        lastSearchBalance = getEmcExact();
	}

	// 使用二分查找寻找第一个 EMC <= target 的物品索引
	private int findStartIndexByEmc(List<ItemStack> list, ExactEMC targetEmc) {
		int left = 0, right = list.size() - 1;
		int ans = -1;
		while (left <= right) {
			int mid = left + (right - left) / 2;
			ExactEMC midEmc = EMCHelper.getEmcValueExact(list.get(mid));
			if (midEmc.compareTo(targetEmc) <= 0) {
				ans = mid;
				right = mid - 1; // 尝试寻找更靠左的（同 EMC 的前置项）
			}
			else left = mid + 1; // 当前项 EMC 太大，往右找更小的
		}
		return ans;
	}

	private void fillOutputs(List<ItemStack> sourceList, int[] slots, ExactEMC reqEmc, int skipCount) {
		int startIndex = findStartIndexByEmc(sourceList, reqEmc);
		if (startIndex == -1) return;

		int filled = 0;
		// 结合 startIndex 与 分页 skipCount 实现偏移直接定位目标页面的物品
		for (int i = startIndex + skipCount, size = sourceList.size(); i < size && filled < slots.length; i++)
			inventory[slots[filled++]] = sourceList.get(i);
	}

	public void updateOutputs() {
		if (!player.worldObj.isRemote) return;

		updateSearchCache();

		for (int i : MATTER_INDEXES) inventory[i] = null;
		for (int i : FUEL_INDEXES) inventory[i] = null;

		ExactEMC reqEmc = getEmcExact();
		if (inventory[LOCK_INDEX] != null) {
			reqEmc = EMCHelper.getEmcValueExact(inventory[LOCK_INDEX]);
			if (reqEmc.isZero() || reqEmc.compareTo(getEmcExact()) > 0) reqEmc = getEmcExact();
		}

		// 极速填充输出槽
		fillOutputs(filteredMatter, MATTER_INDEXES, reqEmc, searchpage * 12);
		fillOutputs(filteredFuel, FUEL_INDEXES, reqEmc, searchpage * 4);
	}

	public boolean hasNextPage() {
		updateSearchCache();

		ExactEMC reqEmc = getEmcExact();
		if (inventory[LOCK_INDEX] != null) {
			reqEmc = EMCHelper.getEmcValueExact(inventory[LOCK_INDEX]);
			if (reqEmc.isZero() || reqEmc.compareTo(getEmcExact()) > 0) reqEmc = getEmcExact();
		}

		int startMatter = findStartIndexByEmc(filteredMatter, reqEmc);
		int startFuel = findStartIndexByEmc(filteredFuel, reqEmc);

		int matterAvailable = startMatter == -1 ? 0 : filteredMatter.size() - startMatter;
		int fuelAvailable = startFuel == -1 ? 0 : filteredFuel.size() - startFuel;

		// 只要可用的物品数量超出当前页面+1的容量，说明有下一页
		return matterAvailable > (searchpage + 1) * 12 || fuelAvailable > (searchpage + 1) * 4;
	}

	public void writeIntoOutputSlot(int slot, ItemStack item) {
        if (slot < 10 || slot > 25) return;
        if (item != null) { item = item.copy(); item.stackSize = 1; }
		if (EMCHelper.doesItemHaveEmc(item) && EMCHelper.getEmcValueExact(item).compareTo(getEmcExact()) <= 0 && Transmutation.hasKnowledgeForStack(item, player))
			inventory[slot] = item;
		else inventory[slot] = null;
	}

	public List<ItemStack> getOutputSlots() {
		return Arrays.asList(inventory).subList(10,26);
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
		ItemStack stack = inventory[slot];
		if (stack != null) {
			if (stack.stackSize <= qty) {
				inventory[slot] = null;
			}
			else {
				stack = stack.splitStack(qty);
				if (stack.stackSize == 0)
					inventory[slot] = null;
			}
		}
		return stack;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		if (inventory[slot] == null)
			return null;

		ItemStack stack = inventory[slot];
		inventory[slot] = null;
		return stack;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		inventory[slot] = stack;

		int maxStack;
		if (stack != null && stack.stackSize > (maxStack = this.getInventoryStackLimit()))
			stack.stackSize = maxStack;
	}

	@Override
	public String getInventoryName() {
		return "item.pe_transmutation_tablet.name";
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
		return true;
	}

	@Override
	public void openInventory() {

		ItemStack[] inputLocks = Transmutation.getInputsAndLock(player);
		System.arraycopy(inputLocks, 0, inventory, 0, 9);

		knowledgeDirty = true;
		if (player.worldObj.isRemote)
			updateOutputs();
	}

	@Override
	public void closeInventory() {
		if (player.worldObj.isRemote) return;
		Transmutation.setInputsAndLocks(Arrays.copyOfRange(inventory, 0, 9), player);
		//Transmutation.sync(player);
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public void markDirty() {}

    public void addEmc(ExactEMC value) {
        if (!player.worldObj.isRemote) Transmutation.addEmcExact(player, value);
    }
    public boolean removeEmc(ExactEMC value) {
        if (player.worldObj.isRemote) return getEmcExact().compareTo(value) >= 0;
        return Transmutation.tryRemoveEmcExact(player, value);
    }

	public boolean hasMaxedEmc() {
		return false; // Player balance has no machine-capacity cap.
	}
}
