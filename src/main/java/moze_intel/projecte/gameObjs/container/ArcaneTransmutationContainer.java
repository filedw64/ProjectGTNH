package moze_intel.projecte.gameObjs.container;

import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.ArcaneCraftingSlot;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotInput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotLock;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotUnlearn;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ArcaneTransmutationEmcPKT;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArcaneTransmutationContainer extends TransmutationContainer {
	public InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
	public IInventory craftResult = new InventoryCraftResult();
	private final EntityPlayer player;

	private static final int[] ROTATION_SLOTS = {0, 1, 2, 5, 8, 7, 6, 3};

	public ArcaneTransmutationContainer(InventoryPlayer invPlayer, EntityPlayer player) {
		super(invPlayer, new TransmutationInventory(player), true);
		this.player = player;

		for (Slot slot : this.inventorySlots) {
			if (slot instanceof SlotInput) {
				int id = slot.getSlotIndex();
				switch (id) {
					case 0 -> {
						slot.xDisplayPosition = 106;
						slot.yDisplayPosition = 21;
					}
					case 1 -> {
						slot.xDisplayPosition = 206;
						slot.yDisplayPosition = 21;
					}
					case 2 -> {
						slot.xDisplayPosition = 84;
						slot.yDisplayPosition = 43;
					}
					case 3 -> {
						slot.xDisplayPosition = 228;
						slot.yDisplayPosition = 43;
					}
					case 4 -> {
						slot.xDisplayPosition = 84;
						slot.yDisplayPosition = 94;
					}
					case 5 -> {
						slot.xDisplayPosition = 228;
						slot.yDisplayPosition = 94;
					}
					case 6 -> {
						slot.xDisplayPosition = 106;
						slot.yDisplayPosition = 115;
					}
					case 7 -> {
						slot.xDisplayPosition = 206;
						slot.yDisplayPosition = 115;
					}
				}
			}
			else if (slot instanceof SlotOutput) {
				int id = slot.getSlotIndex();
				switch (id) {
					case 10 -> {
						slot.xDisplayPosition = 156;
						slot.yDisplayPosition = 20;
					}
					case 11 -> {
						slot.xDisplayPosition = 181;
						slot.yDisplayPosition = 26;
					}
					case 12 -> {
						slot.xDisplayPosition = 131;
						slot.yDisplayPosition = 26;
					}
					case 13 -> {
						slot.xDisplayPosition = 199;
						slot.yDisplayPosition = 44;
					}
					case 14 -> {
						slot.xDisplayPosition = 113;
						slot.yDisplayPosition = 44;
					}
					case 15 -> {
						slot.xDisplayPosition = 204;
						slot.yDisplayPosition = 68;
					}
					case 16 -> {
						slot.xDisplayPosition = 108;
						slot.yDisplayPosition = 68;
					}
					case 17 -> {
						slot.xDisplayPosition = 199;
						slot.yDisplayPosition = 92;
					}
					case 18 -> {
						slot.xDisplayPosition = 113;
						slot.yDisplayPosition = 92;
					}
					case 19 -> {
						slot.xDisplayPosition = 181;
						slot.yDisplayPosition = 110;
					}
					case 20 -> {
						slot.xDisplayPosition = 131;
						slot.yDisplayPosition = 110;
					}
					case 21 -> {
						slot.xDisplayPosition = 156;
						slot.yDisplayPosition = 116;
					}
					case 22 -> {
						slot.xDisplayPosition = 136;
						slot.yDisplayPosition = 48;
					}
					case 23 -> {
						slot.xDisplayPosition = 176;
						slot.yDisplayPosition = 48;
					}
					case 24 -> {
						slot.xDisplayPosition = 136;
						slot.yDisplayPosition = 88;
					}
					case 25 -> {
						slot.xDisplayPosition = 176;
						slot.yDisplayPosition = 88;
					}
				}
			}
			else if (slot instanceof SlotLock) {
				slot.xDisplayPosition = 156;
				slot.yDisplayPosition = 68;
			}
			else if (slot instanceof SlotConsume) {
				slot.xDisplayPosition = 228;
				slot.yDisplayPosition = 115;
			}
			else if (slot instanceof SlotUnlearn) {
				slot.xDisplayPosition = 84;
				slot.yDisplayPosition = 115;
			}
			else if (slot.inventory == invPlayer) {
				int id = slot.getSlotIndex();
				if (id < 9) {
					slot.xDisplayPosition = 84 + id * 18;
					slot.yDisplayPosition = 193;
				}
				else {
					int x = (id - 9) % 9;
					int y = (id - 9) / 9;
					slot.xDisplayPosition = 84 + x * 18;
					slot.yDisplayPosition = 135 + y * 18;
				}
			}
		}

		// 添加合成输出槽 (ID 63)
		this.addSlotToContainer(new ArcaneCraftingSlot(this.transmutationInventory, player, this.craftMatrix, this.craftResult, 0, 53, 75));

		// 添加合成网格 (ID 64-72)
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++)
				this.addSlotToContainer(new Slot(this.craftMatrix, j + i * 3, 17 + j * 18, 17 + i * 18));

		this.onCraftMatrixChanged(this.craftMatrix);
	}

	@Override
	public void onCraftMatrixChanged(IInventory inventory) {
		this.craftResult.setInventorySlotContents(0, CraftingManager.getInstance().findMatchingRecipe(this.craftMatrix, this.player.worldObj));
	}

	@Override
	public void onContainerClosed(EntityPlayer player) {
		super.onContainerClosed(player);
		if (player.worldObj.isRemote) return;
		for (int i = 0; i < 9; i++) {
			ItemStack itemstack = this.craftMatrix.getStackInSlotOnClosing(i);
			if (itemstack != null)
				player.dropPlayerItemWithRandomChoice(itemstack, false);
		}
	}

	// 修复普通的鼠标点击导致不刷新的问题（合成输出槽的学习由 ArcaneCraftingSlot 负责）
	@Override
	public ItemStack slotClick(int slot, int button, int clickType, EntityPlayer player) {
		ItemStack result = super.slotClick(slot, button, clickType, player);

		// 保险机制：只要和原版的输入/消耗槽互动过，强制刷新右侧列表
		if (slot >= 0 && slot <= 9)
			this.transmutationInventory.updateOutputs();

		return result;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
		if (slotIndex >= 63) {
			Slot slot = this.inventorySlots.get(slotIndex);
			if (slot == null || !slot.getHasStack()) return null;

			ItemStack itemstack1 = slot.getStack();
			ItemStack itemstack = itemstack1.copy();

			// 与 ContainerWorkbench 同序：先合并，再清槽/通知，最后才触发拾取；onCreated 由 SlotCrafting 内部触发，不能在这里补
			if (!this.mergeItemStack(itemstack1, 27, 63, true))
				return null;

			if (itemstack1.stackSize == 0) slot.putStack(null);
			else slot.onSlotChanged();

			if (itemstack1.stackSize == itemstack.stackSize)
				return null;
			slot.onPickupFromSlot(player, itemstack1);
			return itemstack;
		}

		// 原版的 Shift 点击逻辑（玩家背包 -> 转化桌）
		ItemStack ret = super.transferStackInSlot(player, slotIndex);
		// 强制刷新输出列表，防止假死
		if (slotIndex >= 27)
			this.transmutationInventory.updateOutputs();
		return ret;
	}

	public void clearCrafting(EntityPlayer player) {
		clearCrafting(player, true);
	}

	/**
	 * syncEmc = false 时由调用方统一发包：fillRecipe 的包处理会在结算完再发一次，
	 * 重复发会让一次 NEI 摆料推两个包。
	 */
	private void clearCrafting(EntityPlayer player, boolean syncEmc) {
		boolean emcChanged = false;
		for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
			ItemStack stack = this.craftMatrix.getStackInSlot(i);
			if (stack == null) continue;
			if (isCreatableFromEmc(player, stack)) {
				// 打开界面期间 EMC 的权威值是 transmutationInventory.emc，写 Transmutation 会被关界面时覆写
				this.transmutationInventory.addEmc(emcValueOf(stack) * stack.stackSize);
				this.craftMatrix.setInventorySlotContents(i, null);
				emcChanged = true;
			} else {
				// 背包满时剩余必须留在格子里，不能丢
				ItemStack remainder = ItemHelper.pushStackInInv(player.inventory, stack);
				this.craftMatrix.setInventorySlotContents(i, remainder);
			}
		}
		this.onCraftMatrixChanged(this.craftMatrix);
		if (emcChanged && syncEmc && player instanceof EntityPlayerMP)
			PacketHandler.sendTo(new ArcaneTransmutationEmcPKT(this.transmutationInventory.emc), (EntityPlayerMP) player);
	}

	public void rotateCrafting(boolean clockwise) {
		ItemStack[] stacks = new ItemStack[ROTATION_SLOTS.length];
		if (clockwise) {
			for (int i = 0; i < ROTATION_SLOTS.length; i++) {
				int j = i - 1;
				if (j < 0) j = ROTATION_SLOTS.length - 1;
				stacks[i] = craftMatrix.getStackInSlot(ROTATION_SLOTS[j % ROTATION_SLOTS.length]);
			}
		} else {
			for (int i = 0; i < ROTATION_SLOTS.length; i++) stacks[i] = craftMatrix.getStackInSlot(ROTATION_SLOTS[(i + 1) % ROTATION_SLOTS.length]);
		}
		for (int i = 0; i < ROTATION_SLOTS.length; i++) craftMatrix.setInventorySlotContents(ROTATION_SLOTS[i], stacks[i]);
		this.onCraftMatrixChanged(this.craftMatrix);
	}

	public void balanceCrafting() {
		java.util.List<java.util.List<Integer>> groups = new java.util.ArrayList<java.util.List<Integer>>();
		for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
			ItemStack stack = this.craftMatrix.getStackInSlot(i);
			if (stack != null && stack.getMaxStackSize() > 1) {
				boolean foundGroup = false;
				for (java.util.List<Integer> group : groups) {
					ItemStack firstItem = this.craftMatrix.getStackInSlot(group.get(0));
					if (firstItem.getItem() == stack.getItem() && firstItem.getItemDamage() == stack.getItemDamage() && ItemStack.areItemStackTagsEqual(firstItem, stack)) {
						group.add(i); foundGroup = true; break;
					}
				}
				if (!foundGroup) {
					java.util.List<Integer> newGroup = new java.util.ArrayList<Integer>();
					newGroup.add(i); groups.add(newGroup);
				}
			}
		}
		for (java.util.List<Integer> group : groups) {
			int totalCount = 0;
			for (int slotIndex : group) totalCount += this.craftMatrix.getStackInSlot(slotIndex).stackSize;
			int countPerSlot = totalCount / group.size();
			int remainder = totalCount % group.size();
			for (int slotIndex : group) this.craftMatrix.getStackInSlot(slotIndex).stackSize = countPerSlot;
			int idx = 0;
			// 有界保护：stackSize 超过槽上限时可能没有任何槽能再加，不能死循环
			for (int attempt = 0; attempt < 9 && remainder > 0; attempt++) {
				ItemStack stack = this.craftMatrix.getStackInSlot(group.get(idx));
				if (stack.stackSize < stack.getMaxStackSize()) { stack.stackSize++; remainder--; }
				idx++; if (idx >= group.size()) idx = 0;
			}
		}
		this.onCraftMatrixChanged(this.craftMatrix);
	}

	public void spreadCrafting() {
		while (true) {
			int biggestSlot = -1, biggestSize = 1;
			for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
				ItemStack stack = this.craftMatrix.getStackInSlot(i);
				if (stack != null && stack.stackSize > biggestSize) { biggestSize = stack.stackSize; biggestSlot = i; }
			}
			if (biggestSlot == -1) break;
			ItemStack biggestStack = this.craftMatrix.getStackInSlot(biggestSlot);
			boolean emptySlotFilled = false;
			for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
				if (this.craftMatrix.getStackInSlot(i) == null) {
					if (biggestStack.stackSize > 1) {
						ItemStack newStack = biggestStack.copy(); newStack.stackSize = 1;
						this.craftMatrix.setInventorySlotContents(i, newStack);
						biggestStack.stackSize--; emptySlotFilled = true;
					} else break;
				}
			}
			if (!emptySlotFilled) break;
		}
		this.balanceCrafting();
	}

	// --- NEI 一键填装配方 ---

	/**
	 * target 的通配符损伤匹配任意损伤；NBT 是否参与比较跟随 EMCMapper.enableNBTprocess。
	 */
	public static boolean matchesStack(ItemStack target, ItemStack other) {
		if (target == null || other == null || target.getItem() == null || other.getItem() == null) return false;
		if (target.getItem() != other.getItem()) return false;

		boolean wildcard = target.getItemDamage() == OreDictionary.WILDCARD_VALUE;
		if (!wildcard && target.getItemDamage() != other.getItemDamage()) return false;

		if (!EMCMapper.enableNBTprocess) return true;
		return wildcard ? ItemStack.areItemStackTagsEqual(target, other) : ItemHelper.areItemStacksEqual(target, other);
	}

	/**
	 * 查 EMC 前的归一化：只处理通配符损伤。耐久必须保持原值，否则会绕过
	 * EMCHelper.getEmcValue 里按 (maxDamage - damage) / maxDamage 的折算分支，
	 * 让残血装备按全新价折算成 EMC。
	 */
	public static ItemStack cleanForEmc(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return null;
		ItemStack clean = stack.copy();
		clean.stackSize = 1;
		if (clean.getItemDamage() == OreDictionary.WILDCARD_VALUE) clean.setItemDamage(0);
		return clean;
	}

	/**
	 * 与知识库比对的归一化：知识条目本身已把「无子类型、有耐久」的物品归到 damage 0。
	 */
	public static ItemStack cleanForKnowledge(ItemStack stack) {
		ItemStack clean = cleanForEmc(stack);
		if (clean != null && !clean.getHasSubtypes() && clean.getMaxDamage() != 0) clean.setItemDamage(0);
		return clean;
	}

	public static double emcValueOf(ItemStack stack) {
		ItemStack clean = cleanForEmc(stack);
		return clean == null ? 0 : EMCHelper.getEmcValue(clean);
	}

	public static boolean isCreatableFromEmc(EntityPlayer player, ItemStack stack) {
		ItemStack knowledgeStack = cleanForKnowledge(stack);
		ItemStack emcStack = cleanForEmc(stack);
		return knowledgeStack != null && emcStack != null && EMCHelper.getEmcValue(emcStack) > 0
			&& Transmutation.hasKnowledgeForStack(knowledgeStack, player);
	}

	/**
	 * 与 Transmutation.hasKnowledgeForStack 相同的相等语义（NBT 是否参与比较跟随 EMCMapper.enableNBTprocess）。
	 */
	public static String knowledgeKey(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return null;
		String key = Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage();
		if (!EMCMapper.enableNBTprocess || stack.stackTagCompound == null) return key;
		return key + ":" + stack.stackTagCompound;
	}

	/**
	 * 把玩家知识库建成 key 集合。知识库有几千条时，逐个候选做线性扫描会把
	 * NEI 配方预览（每帧一次）和 NEI 一键摆料拖出可见卡顿。
	 */
	public static Set<String> knowledgeIndexOf(EntityPlayer player) {
		List<ItemStack> knowledge = Transmutation.getKnowledge(player);
		Set<String> index = new HashSet<>((int) (knowledge.size() / 0.75F) + 16);
		for (ItemStack stack : knowledge) {
			String key = knowledgeKey(stack);
			if (key != null) index.add(key);
		}
		return index;
	}

	public static boolean isCreatableFromEmc(Set<String> knowledgeIndex, ItemStack stack) {
		ItemStack knowledgeStack = cleanForKnowledge(stack);
		ItemStack emcStack = cleanForEmc(stack);
		if (knowledgeStack == null || emcStack == null || EMCHelper.getEmcValue(emcStack) <= 0) return false;
		String key = knowledgeKey(knowledgeStack);
		return key != null && knowledgeIndex.contains(key);
	}

	public void fillRecipe(EntityPlayer player, List<List<ItemStack>> recipe) {
		if (player.worldObj.isRemote) return;

		clearCrafting(player, false);

		boolean[] done = new boolean[9];
		// 索引只建一次：9 格 × 候选的「是否已解锁」判断不再逐次线性扫知识库
		Set<String> knowledgeIndex = knowledgeIndexOf(player);

		// 第一遍：全部格子，只用背包
		for (int slot = 0; slot < 9; slot++) {
			if (this.craftMatrix.getStackInSlot(slot) != null) {
				// clearCrafting 在背包满时会把剩余留在格子里，那是玩家的东西，不能被摆料覆盖
				done[slot] = true;
				continue;
			}

			List<ItemStack> candidates = candidatesAt(recipe, slot);
			if (candidates.isEmpty()) continue;
			int required = requiredOf(candidates);
			if (required <= 0) continue;

			for (ItemStack candidate : candidates) {
				if (candidate == null || asStack(candidate, required) == null) continue;

				List<ItemStack> taken = new ArrayList<>();
				if (takeFromInventory(player, candidate, required, taken)) {
					// 落格必须用真正扣下来的那件的身份（damage/NBT 都要保真）
					ItemStack placed = taken.isEmpty() ? asStack(candidate, required) : taken.get(0).copy();
					placed.stackSize = Math.min(required, placed.getMaxStackSize());
					this.craftMatrix.setInventorySlotContents(slot, placed);
					done[slot] = true;
					break;
				}

				// 取不满：已取出的放回背包
				ItemStack leftover = refundToInventory(player, taken);
				if (leftover != null) {
					// 背包满时的降级：宁可少填也不丢料。这一格数量不足、配方匹配不上，也不会再由第二遍补足
					this.craftMatrix.setInventorySlotContents(slot, leftover);
					done[slot] = true;
					break;
				}
			}
		}

		// 第二遍：第一遍没填成的格子，用已解锁知识 + EMC 补，候选按 EMC 升序取最便宜
		for (int slot = 0; slot < 9; slot++) {
			if (done[slot] || this.craftMatrix.getStackInSlot(slot) != null) continue;

			List<ItemStack> candidates = candidatesAt(recipe, slot);
			if (candidates.isEmpty()) continue;
			int required = requiredOf(candidates);
			if (required <= 0) continue;

			List<ItemStack> creatable = new ArrayList<>();
			for (ItemStack candidate : candidates) {
				if (candidate != null && isCreatableFromEmc(knowledgeIndex, candidate) && emcValueOf(candidate) > 0)
					creatable.add(candidate);
			}
			creatable.sort((a, b) -> Double.compare(emcValueOf(a), emcValueOf(b)));

			for (ItemStack candidate : creatable) {
				ItemStack placed = asStack(candidate, required);
				if (placed == null) continue;

				double cost = emcValueOf(candidate) * required;
				if (cost > this.transmutationInventory.emc) continue; // 缺料就留空

				this.transmutationInventory.removeEmc(cost);
				this.craftMatrix.setInventorySlotContents(slot, placed);
				break;
			}
		}

		this.onCraftMatrixChanged(this.craftMatrix);
		this.transmutationInventory.updateOutputs();
	}

	private static List<ItemStack> candidatesAt(List<List<ItemStack>> recipe, int slot) {
		if (recipe == null || slot >= recipe.size() || recipe.get(slot) == null) return new ArrayList<>();
		return recipe.get(slot);
	}

	private static int requiredOf(List<ItemStack> candidates) {
		for (ItemStack stack : candidates) {
			// 数量来自客户端（ArcaneRecipeTransferPKT），必须夹到该物品自身的堆叠上限
			if (stack != null) return Math.max(1, Math.min(stack.stackSize, stack.getMaxStackSize()));
		}
		return 0;
	}

	private static ItemStack asStack(ItemStack stack, int size) {
		ItemStack clean = cleanForEmc(stack);
		if (clean != null) clean.stackSize = Math.max(1, Math.min(size, clean.getMaxStackSize()));
		return clean;
	}

	private static boolean takeFromInventory(EntityPlayer player, ItemStack target, int required, List<ItemStack> taken) {
		int collected = 0;
		ItemStack identity = null;
		for (int i = 0; i < player.inventory.mainInventory.length && collected < required; i++) {
			ItemStack invStack = player.inventory.mainInventory[i];
			if (invStack == null || invStack.stackSize <= 0 || !matchesStack(target, invStack)) continue;
			// 只从同一种身份的堆里取，避免把 damage 不同的物品混着填进一格（通配符候选时才会出现）
			if (identity != null && !ItemHelper.areItemStacksEqual(identity, invStack)) continue;

			ItemStack removed = player.inventory.decrStackSize(i, Math.min(required - collected, invStack.stackSize));
			if (removed == null) continue;
			if (identity == null) identity = removed.copy();
			taken.add(removed);
			collected += removed.stackSize;
		}
		return collected >= required;
	}

	private static ItemStack refundToInventory(EntityPlayer player, List<ItemStack> taken) {
		ItemStack leftover = null;
		for (ItemStack stack : taken) {
			ItemStack remainder = ItemHelper.pushStackInInv(player.inventory, stack);
			if (remainder == null) continue;
			if (leftover == null) leftover = remainder;
			else if (ItemHelper.areItemStacksEqual(leftover, remainder)) leftover.stackSize += remainder.stackSize;
		}
		taken.clear();
		return leftover;
	}
}
