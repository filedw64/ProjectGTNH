package moze_intel.projecte.gameObjs.container;

import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotInput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotLock;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotUnlearn;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

public class ArcaneTransmutationContainer extends TransmutationContainer {
	public InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
	public IInventory craftResult = new InventoryCraftResult();
	private EntityPlayer player;

	private static final int[] ROTATION_SLOTS = {0, 1, 2, 5, 8, 7, 6, 3};

	public ArcaneTransmutationContainer(InventoryPlayer invPlayer, EntityPlayer player) {
		super(invPlayer, new TransmutationInventory(player), true);
		this.player = player;

		for (Object obj : this.inventorySlots) {
			Slot slot = (Slot) obj;
			if (slot instanceof SlotInput) {
				int id = slot.getSlotIndex();
				if (id == 0) { slot.xDisplayPosition = 106; slot.yDisplayPosition = 21; }
				else if (id == 1) { slot.xDisplayPosition = 206; slot.yDisplayPosition = 21; }
				else if (id == 2) { slot.xDisplayPosition = 84; slot.yDisplayPosition = 43; }
				else if (id == 3) { slot.xDisplayPosition = 228; slot.yDisplayPosition = 43; }
				else if (id == 4) { slot.xDisplayPosition = 84; slot.yDisplayPosition = 94; }
				else if (id == 5) { slot.xDisplayPosition = 228; slot.yDisplayPosition = 94; }
				else if (id == 6) { slot.xDisplayPosition = 106; slot.yDisplayPosition = 115; }
				else if (id == 7) { slot.xDisplayPosition = 206; slot.yDisplayPosition = 115; }
			} else if (slot instanceof SlotOutput) {
				int id = slot.getSlotIndex();
				if (id == 10) { slot.xDisplayPosition = 156; slot.yDisplayPosition = 20; }
				else if (id == 11) { slot.xDisplayPosition = 181; slot.yDisplayPosition = 26; }
				else if (id == 12) { slot.xDisplayPosition = 131; slot.yDisplayPosition = 26; }
				else if (id == 13) { slot.xDisplayPosition = 199; slot.yDisplayPosition = 44; }
				else if (id == 14) { slot.xDisplayPosition = 113; slot.yDisplayPosition = 44; }
				else if (id == 15) { slot.xDisplayPosition = 204; slot.yDisplayPosition = 68; }
				else if (id == 16) { slot.xDisplayPosition = 108; slot.yDisplayPosition = 68; }
				else if (id == 17) { slot.xDisplayPosition = 199; slot.yDisplayPosition = 92; }
				else if (id == 18) { slot.xDisplayPosition = 113; slot.yDisplayPosition = 92; }
				else if (id == 19) { slot.xDisplayPosition = 181; slot.yDisplayPosition = 110; }
				else if (id == 20) { slot.xDisplayPosition = 131; slot.yDisplayPosition = 110; }
				else if (id == 21) { slot.xDisplayPosition = 156; slot.yDisplayPosition = 116; }
				else if (id == 22) { slot.xDisplayPosition = 136; slot.yDisplayPosition = 48; }
				else if (id == 23) { slot.xDisplayPosition = 176; slot.yDisplayPosition = 48; }
				else if (id == 24) { slot.xDisplayPosition = 136; slot.yDisplayPosition = 88; }
				else if (id == 25) { slot.xDisplayPosition = 176; slot.yDisplayPosition = 88; }
			} else if (slot instanceof SlotLock) {
				slot.xDisplayPosition = 156; slot.yDisplayPosition = 68;
			} else if (slot instanceof SlotConsume) {
				slot.xDisplayPosition = 228; slot.yDisplayPosition = 115;
			} else if (slot instanceof SlotUnlearn) {
				slot.xDisplayPosition = 84; slot.yDisplayPosition = 115;
			} else if (slot.inventory == invPlayer) {
				int id = slot.getSlotIndex();
				if (id < 9) {
					slot.xDisplayPosition = 84 + id * 18;
					slot.yDisplayPosition = 193;
				} else {
					int x = (id - 9) % 9;
					int y = (id - 9) / 9;
					slot.xDisplayPosition = 84 + x * 18;
					slot.yDisplayPosition = 135 + y * 18;
				}
			}
		}

		// 添加合成输出槽 (ID 63)
		this.addSlotToContainer(new SlotCrafting(player, this.craftMatrix, this.craftResult, 0, 53, 75));

		// 添加合成网格 (ID 64-72)
		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 3; ++j) {
				this.addSlotToContainer(new Slot(this.craftMatrix, j + i * 3, 17 + j * 18, 17 + i * 18));
			}
		}

		this.onCraftMatrixChanged(this.craftMatrix);
	}

	@Override
	public void onCraftMatrixChanged(IInventory inventory) {
		this.craftResult.setInventorySlotContents(0, CraftingManager.getInstance().findMatchingRecipe(this.craftMatrix, this.player.worldObj));
	}

	@Override
	public void onContainerClosed(EntityPlayer player) {
		super.onContainerClosed(player);
		if (!player.worldObj.isRemote) {
			for (int i = 0; i < 9; ++i) {
				ItemStack itemstack = this.craftMatrix.getStackInSlotOnClosing(i);
				if (itemstack != null) {
					player.dropPlayerItemWithRandomChoice(itemstack, false);
				}
			}
		}
	}

	// 修复普通的鼠标点击导致不刷新的问题，并添加合成槽的自动学习
	@Override
	public ItemStack slotClick(int slot, int button, int clickType, EntityPlayer player) {
		ItemStack result = super.slotClick(slot, button, clickType, player);

		// 63 是合成输出槽。玩家拿起了物品后触发学习
		if (slot == 63 && result != null) {
			ItemStack copy = result.copy();
			copy.stackSize = 1; // 规范化数量为 1
			if (EMCHelper.doesItemHaveEmc(copy) && !Transmutation.hasKnowledgeForStack(copy, player)) {
				this.transmutationInventory.handleKnowledge(copy);
				this.transmutationInventory.updateOutputs();
			}
		}

		// 保险机制：只要和原版的输入/消耗槽互动过，强制刷新右侧列表
		if (slot >= 0 && slot <= 9) {
			this.transmutationInventory.updateOutputs();
		}

		return result;
	}

	// 修复 Shift 快捷点击的自动学习和刷新
	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
		if (slotIndex >= 63) {
			Slot slot = (Slot) this.inventorySlots.get(slotIndex);
			if (slot != null && slot.getHasStack()) {
				ItemStack itemstack1 = slot.getStack();
				ItemStack itemstack = itemstack1.copy();

				if (slotIndex == 63) {
					itemstack1.getItem().onCreated(itemstack1, player.worldObj, player);

					// Shift 点击时的自动学习逻辑
					ItemStack copy = itemstack1.copy();
					copy.stackSize = 1;
					if (EMCHelper.doesItemHaveEmc(copy) && !Transmutation.hasKnowledgeForStack(copy, player)) {
						this.transmutationInventory.handleKnowledge(copy);
						this.transmutationInventory.updateOutputs();
					}

					if (!this.mergeItemStack(itemstack1, 27, 63, true)) {
						return null;
					}
					slot.onSlotChange(itemstack1, itemstack);
				} else {
					if (!this.mergeItemStack(itemstack1, 27, 63, true)) {
						return null;
					}
				}

				if (itemstack1.stackSize == 0) slot.putStack(null);
				else slot.onSlotChanged();

				if (itemstack1.stackSize == itemstack.stackSize) return null;
				slot.onPickupFromSlot(player, itemstack1);
				return itemstack;
			}
			return null;
		}

		// 原版的 Shift 点击逻辑（玩家背包 -> 转化桌）
		ItemStack ret = super.transferStackInSlot(player, slotIndex);
		// 强制刷新输出列表，防止假死
		if (slotIndex >= 27 && slotIndex <= 62) {
			this.transmutationInventory.updateOutputs();
		}
		return ret;
	}

	public void clearCrafting(EntityPlayer player) {
		boolean emcUpdate = false;
		for (int i = 0; i < this.craftMatrix.getSizeInventory(); i++) {
			ItemStack stack = this.craftMatrix.getStackInSlot(i);
			if (stack == null) continue;
			if (EMCHelper.doesItemHaveEmc(stack) && Transmutation.hasKnowledgeForStack(stack, player)) {
				double emcValue = EMCHelper.getEmcValue(stack) * stack.stackSize;
				Transmutation.setEmc(player, Transmutation.getEmc(player) + emcValue);
				emcUpdate = true;
				this.craftMatrix.setInventorySlotContents(i, null);
			} else {
				ItemHelper.pushStackInInv(player.inventory, stack);
				if (stack.stackSize == 0) this.craftMatrix.setInventorySlotContents(i, null);
			}
		}
		if (emcUpdate) Transmutation.sync(player);
		this.onCraftMatrixChanged(this.craftMatrix);
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
			while (remainder > 0) {
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

	// 这下面是借助了AI，因为当时不想写了，但是下面的这些疑似有致命bug，可以先删去，尚在排查
	// --- 将这个方法加在 ArcaneTransmutationContainer.java 的最下面 ---

	public void fillRecipe(EntityPlayer player, ItemStack[] recipe) {
		// 1. 先把当前网格里的东西退回去（变成 EMC 或塞回背包）
		this.clearCrafting(player);
		boolean emcUpdated = false;

		// 2. 遍历配方的 9 个格子
		for (int i = 0; i < 9; i++) {
			ItemStack target = recipe[i];
			if (target == null) continue;

			boolean foundInInv = false;
			// 2.1 优先尝试从玩家背包里扣除
			for (int j = 0; j < player.inventory.mainInventory.length; j++) {
				ItemStack invStack = player.inventory.mainInventory[j];
				// 模糊匹配：物品 ID 和 损伤值 相同即可
				if (invStack != null && invStack.getItem() == target.getItem() && invStack.getItemDamage() == target.getItemDamage()) {
					ItemStack copy = invStack.copy();
					copy.stackSize = 1;
					this.craftMatrix.setInventorySlotContents(i, copy);
					player.inventory.decrStackSize(j, 1);
					foundInInv = true;
					break;
				}
			}

			if (foundInInv) continue;

			// 2.2 如果背包里没有，尝试从 EMC 知识库里凭空变出来！
			ItemStack cleanTarget = target.copy();
			cleanTarget.stackSize = 1; // 只生成 1 个

			if (EMCHelper.doesItemHaveEmc(cleanTarget) && Transmutation.hasKnowledgeForStack(cleanTarget, player)) {
				double emcCost = EMCHelper.getEmcValue(cleanTarget);
				if (Transmutation.getEmc(player) >= emcCost) {
					// 扣钱，变物！
					Transmutation.setEmc(player, Transmutation.getEmc(player) - emcCost);
					emcUpdated = true;
					this.craftMatrix.setInventorySlotContents(i, cleanTarget);
				}
			}
		}

		// 3. 如果扣了 EMC，同步给客户端，并刷新合成输出
		if (emcUpdated) {
			Transmutation.sync(player);
		}
		this.onCraftMatrixChanged(this.craftMatrix);
	}
}
