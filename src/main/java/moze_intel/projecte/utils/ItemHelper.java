package moze_intel.projecte.utils;

import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.entity.EntityLootBall;
import moze_intel.projecte.integration.helpers.GTItemHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for Inventories, ItemStacks, Items, and the Ore Dictionary.
 * Notice: Please try to keep methods tidy and alphabetically ordered. Thanks!
 */
public final class ItemHelper {

	/**
	 * @return True if the only aspect these stacks differ by is stack size, false if item, meta, or nbt differ.
	 */
	public static boolean areItemStacksEqual(ItemStack stack1, ItemStack stack2) {
		if (stack1 == stack2) return true;
		if (stack1 == null || stack2 == null) return false;
		return stack1.isItemEqual(stack2) && ItemStack.areItemStackTagsEqual(stack1, stack2);
	}

	public static boolean basicAreStacksEqual(ItemStack stack1, ItemStack stack2) {
		if (stack1 == stack2) return true;
		if (stack1 == null || stack2 == null) return false;
		// Item 在 MC 中是单例，直接使用 == 比较地址更快
		return (stack1.getItem() == stack2.getItem()) && (stack1.getItemDamage() == stack2.getItemDamage());
	}

	public static void compactItemList(List<ItemStack> list) {
		for (int i = 0; i < list.size(); i++) {
			ItemStack s = list.get(i);
			if (s == null || s.stackSize >= s.getMaxStackSize()) continue; // 已经满堆则直接跳过内层循环

			for (int j = i + 1; j < list.size(); j++) {
				ItemStack s1 = list.get(j);
				if (s1 == null || s1.stackSize <= 0) continue;

				if (areItemStacksEqual(s, s1)) {
					int space = s.getMaxStackSize() - s.stackSize;
					if (s1.stackSize <= space) {
						s.stackSize += s1.stackSize;
						s1.stackSize = 0;
					} else {
						s1.stackSize -= space;
						s.stackSize = s.getMaxStackSize();
						break; // 当前物品已满堆，无需继续往后寻找
					}
				}
			}
		}

		list.sort(Comparators.ITEMSTACK_ASCENDING);
		trimItemList(list);
	}

	/**
	 * Compacts and sorts list of items, without regard for stack sizes
	 */
	public static void compactItemListIgnoreStacksize(List<ItemStack> list) {
		for (int i = 0; i < list.size(); i++) {
			ItemStack s = list.get(i);
			if (s == null || s.stackSize <= 0) continue; // 已经被合并清空的物品直接跳过

			for (int j = i + 1; j < list.size(); j++) {
				ItemStack s1 = list.get(j);
				if (s1 == null || s1.stackSize <= 0) continue;

				if (areItemStacksEqual(s, s1)) {
					s.stackSize += s1.stackSize;
					s1.stackSize = 0;
				}
			}
		}

		list.sort(Comparators.ITEMSTACK_ASCENDING);
		trimItemList(list);
	}

	public static boolean containsItemStack(List<ItemStack> list, ItemStack toSearch) {
		if (toSearch == null || toSearch.getItem() == null) return false;
		for (ItemStack stack : list) {
			if (stack == null || stack.getItem() == null) continue;

			if (stack.getItem() == toSearch.getItem()) { // 优化
				if (!stack.getHasSubtypes() || stack.getItemDamage() == toSearch.getItemDamage()) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean containsItemStack(ItemStack[] stacks, ItemStack toSearch) {
		if (toSearch == null || toSearch.getItem() == null) return false;
		for (ItemStack stack : stacks) {
			if (stack == null || stack.getItem() == null) continue;

			if (stack.getItem() == toSearch.getItem()) { // 优化
				if (!stack.getHasSubtypes() || stack.getItemDamage() == toSearch.getItemDamage()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Copy an NBTTagList that has inventory indices into the appropriate positions of provided array.
	 */
	public static ItemStack[] copyIndexedNBTToArray(NBTTagList list, ItemStack[] dest) {
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			dest[entry.getByte("index")] = ItemStack.loadItemStackFromNBT(entry);
		}
		return dest;
	}

	/**
	 * Filter nbt tags that truly differ items.
	 *
	 * @param stack The ItemStack needs to filter nbt for
	 * @return filtered nbt from stack.stackTagCompound
	 */
	public static NBTTagCompound filterNBT(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return null;
		NBTTagCompound original = stack.stackTagCompound; // 优化
		if (original == null || original.hasNoTags()) return null;

		NBTTagCompound result = new NBTTagCompound();

		// 白名单 NBT
		String itemName = Item.itemRegistry.getNameForObject(stack.getItem());
		// 使用一次 get 替代 containsKey + get 避免双重哈希查找
		List<String> nbtList = ProjectEConfig.nbtDistinctlist.get(itemName);
		if (nbtList != null) {
			for (String key : nbtList) {
				if (original.hasKey(key)) {
					result.setTag(key, original.getTag(key).copy());
				}
			}
		}

		// 整合 GT 工具核心 NBT
		if (GTItemHelper.isGTtool(stack) && original.hasKey("GT.ToolStats")) {
			NBTTagCompound toolStats = original.getCompoundTag("GT.ToolStats");
			NBTTagCompound newStats = new NBTTagCompound();

			// 主材料与副材料
			if (toolStats.hasKey("PrimaryMaterial")) {
				newStats.setTag("PrimaryMaterial", toolStats.getTag("PrimaryMaterial").copy());
			}
			if (toolStats.hasKey("SecondaryMaterial")) {
				newStats.setTag("SecondaryMaterial", toolStats.getTag("SecondaryMaterial").copy());
			}

			// MaxDamage
			if (toolStats.hasKey("MaxDamage")) {
				newStats.setTag("MaxDamage", toolStats.getTag("MaxDamage").copy());
			}

			if (!newStats.hasNoTags()) {
				result.setTag("GT.ToolStats", newStats);
			}
		}

		return result.hasNoTags() ? null : result;
	}

	/**
	 * Returns an ItemStack with stacksize = 1.
	 */
	public static ItemStack getNormalizedStack(ItemStack stack) {
		ItemStack result = stack.copy();
		result.stackSize = 1;
		return result;
	}

	/**
	 * Get a List of itemstacks from an OD name.<br>
	 * It also makes sure that no items with damage 32767 are included, to prevent errors.
	 */
	public static List<ItemStack> getODItems(String oreName) {
		List<ItemStack> result = new ArrayList<>();

		for (ItemStack stack : OreDictionary.getOres(oreName)) {
			if (stack == null) continue;

			if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
				List<ItemStack> list = new ArrayList<>();
				ItemStack copy = stack.copy();
				copy.setItemDamage(0);

				list.add(copy.copy());
				String startName = copy.getUnlocalizedName();

				for (int i = 1; i <= 128; i++) {
					try {
						copy.setItemDamage(i);
						if (copy.getUnlocalizedName() == null || copy.getUnlocalizedName().equals(startName)) {
							result.addAll(list);
							break;
						}
					} catch (Exception e) {
						PELogger.logFatal("Couldn't retrieve OD items for: " + oreName);
						PELogger.logFatal("Caused by: " + e);
						result.addAll(list);
						break;
					}

					list.add(copy.copy());

					if (i == 128) {
						copy.setItemDamage(0);
						result.add(copy);
					}
				}
			} else {
				result.add(stack.copy());
			}
		}

		return result;
	}

	public static String getOreDictionaryName(ItemStack stack) {
		int[] oreIds = OreDictionary.getOreIDs(stack);
		if (oreIds.length == 0) return "Unknown";
		return OreDictionary.getOreName(oreIds[0]);
	}

	public static ItemStack getStackFromInv(IInventory inv, ItemStack stack) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack s = inv.getStackInSlot(i);
			if (s != null && basicAreStacksEqual(stack, s)) {
				return s;
			}
		}
		return null;
	}

	public static ItemStack getStackFromInv(ItemStack[] inv, ItemStack stack) {
		for (ItemStack s : inv) {
			if (s != null && basicAreStacksEqual(stack, s)) {
				return s;
			}
		}
		return null;
	}

	public static ItemStack getStackFromString(String internal, int metaData) {
		Item item = (Item) Item.itemRegistry.getObject(internal);
		if (item == null) return null;
		return new ItemStack(item, 1, metaData);
	}

	@Deprecated
	public static boolean hasSpace(IInventory inv, ItemStack stack) {
		return hasSpaceForSingle(inv, stack);
	}

	@Deprecated
	public static boolean hasSpace(ItemStack[] inv, ItemStack stack) {
		return hasSpaceForSingle(inv, stack);
	}

	/**
	 * Ignore stack size.
	 * @return space in the inv for the stack
	 */
	public static int getSpaceFor(IInventory inv, ItemStack stack) {
		int stackable = 0;
		final int maxStack = stack.getMaxStackSize();
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack invStack = inv.getStackInSlot(i);
			// 原逻辑空槽固定 +64，如果是不可堆叠物品可能会导致超量吞件（可能吗？）
			if (invStack == null) {
				stackable += maxStack;
			} else if (areItemStacksEqual(stack, invStack) && invStack.stackSize < maxStack) {
				stackable += maxStack - invStack.stackSize;
			}
		}
		return stackable;
	}

	/**
	 * Ignore stack size.
	 * @return space in the inv for the stack
	 */
	public static int getSpaceFor(ItemStack[] inv, ItemStack stack) {
		int stackable = 0;
		final int maxStack = stack.getMaxStackSize();
		for (ItemStack invStack : inv) {
			// 修复 Bug: 同上
			if (invStack == null) {
				stackable += maxStack;
			} else if (areItemStacksEqual(stack, invStack) && invStack.stackSize < maxStack) {
				stackable += maxStack - invStack.stackSize;
			}
		}
		return stackable;
	}

	/**
	 * Ignore stack size.
	 * @return does inv have space for one item in stack
	 */
	public static boolean hasSpaceForSingle(IInventory inv, ItemStack stack) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack invStack = inv.getStackInSlot(i);
			if (invStack == null) return true;
			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Ignore stack size.
	 * @return does inv have space for one item in stack
	 */
	public static boolean hasSpaceForSingle(ItemStack[] inv, ItemStack stack) {
		for (ItemStack invStack : inv) {
			if (invStack == null) return true;
			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	public static boolean invContainsItem(IInventory inv, ItemStack toSearch) {
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (stack != null && basicAreStacksEqual(stack, toSearch)) return true;
		}
		return false;
	}

	public static boolean invContainsItem(ItemStack[] inv, ItemStack toSearch) {
		for (ItemStack stack : inv) {
			if (stack != null && basicAreStacksEqual(stack, toSearch)) return true;
		}
		return false;
	}

	public static boolean invContainsItem(ItemStack[] inv, Item toSearch) {
		for (ItemStack stack : inv) {
			if (stack != null && stack.getItem() == toSearch) return true; // 优化
		}
		return false;
	}

	public static boolean isOre(Block block, int meta) {
		if (block == Blocks.lit_redstone_ore) return true;
		String oreDictName = getOreDictionaryName(new ItemStack(block, 1, meta));
		return oreDictName.startsWith("ore") || oreDictName.startsWith("denseore");
	}

	public static ItemStack[] nbtToArray(NBTTagList list) {
		ItemStack[] stacks = new ItemStack[list.tagCount()];
		for (int i = 0; i < list.tagCount(); i++) {
			stacks[i] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
		}
		return stacks;
	}

	public static void pushLootBallInInv(IInventory inv, EntityLootBall ball) {
		List<ItemStack> results = new ArrayList<>();
		for (ItemStack s : ball.getItemList()) {
			ItemStack result = pushStackInInv(inv, s);
			if (result != null) {
				results.add(result);
			}
		}
		ball.setItemList(results);
	}

	/**
	 * Returns an itemstack if the stack passed could not entirely fit in the inventory, otherwise returns null.
	 */
	public static ItemStack pushStackInInv(IInventory inv, ItemStack stack) {
		int limit = (inv instanceof InventoryPlayer) ? ((InventoryPlayer) inv).mainInventory.length : inv.getSizeInventory();

		for (int i = 0; i < limit; i++) {
			ItemStack invStack = inv.getStackInSlot(i);

			if (invStack == null) {
				// 原版空槽塞入缺乏槽位类型验证，如果这是只能塞入特定物品的机器槽可能会出问题（？）
				if (inv.isItemValidForSlot(i, stack)) {
					inv.setInventorySlotContents(i, stack);
					return null;
				}
				continue;
			}

			if (inv.isItemValidForSlot(i, stack) && areItemStacksEqual(stack, invStack)
				&& invStack.stackSize < invStack.getMaxStackSize()) {
				int remaining = invStack.getMaxStackSize() - invStack.stackSize;

				if (remaining >= stack.stackSize) {
					invStack.stackSize += stack.stackSize;
					inv.setInventorySlotContents(i, invStack);
					return null;
				}

				invStack.stackSize += remaining;
				inv.setInventorySlotContents(i, invStack);
				stack.stackSize -= remaining;
			}
		}

		return stack.copy();
	}

	/**
	 * Returns an itemstack if the stack passed could not entirely fit in the inventory, otherwise returns null.
	 */
	public static ItemStack pushStackInInv(ItemStack[] inv, ItemStack stack) {
		for (int i = 0; i < inv.length; i++) {
			ItemStack invStack = inv[i];

			if (invStack == null) {
				inv[i] = stack;
				return null;
			}

			if (areItemStacksEqual(stack, invStack) && invStack.stackSize < invStack.getMaxStackSize()) {
				int remaining = invStack.getMaxStackSize() - invStack.stackSize;

				if (remaining >= stack.stackSize) {
					invStack.stackSize += stack.stackSize;
					inv[i] = invStack;
					return null;
				}

				invStack.stackSize += remaining;
				inv[i] = invStack;
				stack.stackSize -= remaining;
			}
		}

		return stack.copy();
	}

	/**
	 * Takes an array of ItemStacks and turns it into an NBTTaglist.
	 */
	public static NBTTagList toIndexedNBTList(ItemStack[] stacks) {
		NBTTagList list = new NBTTagList();
		for (int i = 0; i < stacks.length; i++) {
			if (stacks[i] != null) {
				NBTTagCompound entry = new NBTTagCompound();
				entry.setByte("index", ((byte) i));
				stacks[i].writeToNBT(entry);
				list.appendTag(entry);
			}
		}
		return list;
	}

	public static void trimItemList(List<ItemStack> list) {
		list.removeIf(s -> s == null || s.stackSize <= 0); // 加入非空校验
	}
}
