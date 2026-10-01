package moze_intel.projecte.utils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.api.tile.IEmcProvider;
import moze_intel.projecte.api.tile.IEmcStorage;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;

public class PEGeneralPurposeUtils {
	private static final DecimalFormat FULL_FORMATTER = new DecimalFormat("#,###");

	public static final int[] MATTER_INDEXES = new int[]{12, 11, 13, 10, 14, 21, 15, 20, 16, 19, 17, 18};
	public static final int[] FUEL_INDEXES = new int[]{22, 23, 24, 25};

	private static final Map<TransmutationInventory, Integer> MATCHING_ITEM_COUNTS = new WeakHashMap<>();
	private static final Map<TransmutationInventory, Integer> TOTAL_PAGES = new WeakHashMap<>();

	private static final ConcurrentHashMap<UUID, Object> PLAYER_LOCKS = new ConcurrentHashMap<>();

	// --- GUI 反射获取 (GUI是客户端逻辑，保留极少量安全反射获取TextField) ---
	private static Field FIELD_GUI_TEXTBOX;
	private static Field FIELD_GUI_LEFT;
	private static Field FIELD_GUI_TOP;

	static {
		try {
			FIELD_GUI_LEFT = GuiContainer.class.getDeclaredField("guiLeft");
			FIELD_GUI_LEFT.setAccessible(true);
		} catch (Throwable t) {
			try {
				FIELD_GUI_LEFT = GuiContainer.class.getDeclaredField("field_147003_i");
				FIELD_GUI_LEFT.setAccessible(true);
			} catch (Throwable ignored) {}
		}
		try {
			FIELD_GUI_TOP = GuiContainer.class.getDeclaredField("guiTop");
			FIELD_GUI_TOP.setAccessible(true);
		} catch (Throwable t) {
			try {
				FIELD_GUI_TOP = GuiContainer.class.getDeclaredField("field_147009_r");
				FIELD_GUI_TOP.setAccessible(true);
			} catch (Throwable ignored) {}
		}
	}

	public static int getGuiLeft(GuiContainer gui) {
		if (gui == null) return 0;
		try { if (FIELD_GUI_LEFT != null) return FIELD_GUI_LEFT.getInt(gui); } catch (Throwable ignored) {}
		return (gui.width - 256) / 2;
	}

	public static int getGuiTop(GuiContainer gui) {
		if (gui == null) return 0;
		try { if (FIELD_GUI_TOP != null) return FIELD_GUI_TOP.getInt(gui); } catch (Throwable ignored) {}
		return (gui.height - 256) / 2;
	}

	// --- Native EMC Bridge ---

	public static double getEmcValueDouble(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return 0.0;
		return EMCHelper.getEmcValue(stack);
	}

	public static boolean doesItemHaveEmc(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return false;
		return EMCHelper.doesItemHaveEmc(stack);
	}

	public static double getPlayerEmcSafe(EntityPlayer player) {
		if (player == null) return 0.0;
		return Transmutation.getEmc(player);
	}

	public static void setPlayerEmcSafe(EntityPlayer player, double emc) {
		if (player == null) return;
		if (emc < 0.0 || Double.isNaN(emc)) emc = 0.0;
		if (Double.isInfinite(emc) || emc > Double.MAX_VALUE) emc = Double.MAX_VALUE;
		Transmutation.setEmc(player, emc);
	}

	// --- Transmutation Inventory Helpers ---

	public static double getInventoryEmc(TransmutationInventory inv) {
		return inv != null ? inv.emc : 0.0;
	}

	public static void setInventoryEmc(TransmutationInventory inv, double emc) {
		if (inv == null) return;
		if (emc < 0.0 || Double.isNaN(emc)) emc = 0.0;
		if (Double.isInfinite(emc) || emc > Double.MAX_VALUE) emc = Double.MAX_VALUE;
		inv.emc = emc;
	}

	public static void removeInventoryEmc(TransmutationInventory inv, double amount) {
		if (inv == null || amount <= 0.0) return;
		inv.removeEmc(amount);
		EntityPlayer player = inv.player;
		if (player != null) {
			setPlayerEmcSafe(player, inv.emc);
		}
	}

	public static void handleInventoryAddEmc(TransmutationInventory inv, double value) {
		if (inv == null || value <= 0.0) return;
		inv.addEmc(value);
		EntityPlayer player = inv.player;
		if (player != null) {
			setPlayerEmcSafe(player, inv.emc);
		}
	}

	public static boolean handleInventoryHasMaxedEmc(TransmutationInventory inv) {
		return inv != null && inv.hasMaxedEmc();
	}

	public static Object getPlayerLock(UUID uuid) {
		if (uuid == null) return new Object();
		return PLAYER_LOCKS.computeIfAbsent(uuid, k -> new Object());
	}

	public static boolean areKnowledgeStacksEqual(ItemStack s1, ItemStack s2) {
		if (s1 == s2) return true;
		if (s1 == null || s2 == null) return false;
		return ItemHelper.areItemStacksEqual(s1, s2); // 替换为 GTNH 版本的比较
	}

	public static ItemStack normalizeKnowledgeStack(ItemStack stack) {
		if (stack == null) return null;
		ItemStack copy = stack.copy();
		copy.stackSize = 1;
		return copy;
	}


	public static void syncPlayerEMCAndKnowledge(EntityPlayer player, double newEmc, ItemStack newlyLearnedStack) {
		if (player == null) return;
		UUID uuid = player.getUniqueID();
		Object lock = getPlayerLock(uuid);

		synchronized (lock) {
			setPlayerEmcSafe(player, newEmc);

			if (newlyLearnedStack != null) {
				ItemStack singleLearned = normalizeKnowledgeStack(newlyLearnedStack);
				addKnowledgeSafe(singleLearned, player);
			}

			if (player.openContainer instanceof TransmutationContainer) {
				TransmutationContainer tc = (TransmutationContainer) player.openContainer;
				if (tc.transmutationInventory != null) {
					tc.transmutationInventory.emc = newEmc;
					handleUpdateOutputs(tc.transmutationInventory, true);
					tc.detectAndSendChanges();
				}
			}

			if (player instanceof EntityPlayerMP) {
				Transmutation.sync(player);
			}

			if (uuid != null && cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2")) {
				try {
					moze_intel.projecte.integration.ae2.AE2Integration.notifyHandlersForPlayer(uuid);
				} catch (Throwable ignored) {}
			}
		}
	}

	public static int getMatchingItemCount(TransmutationInventory inv) {
		return inv == null ? 0 : MATCHING_ITEM_COUNTS.getOrDefault(inv, 0);
	}

	public static int getTotalPages(TransmutationInventory inv) {
		return inv == null ? 1 : Math.max(1, TOTAL_PAGES.getOrDefault(inv, 1));
	}

	public static void handleUpdateOutputs(TransmutationInventory inv, boolean isSearchPage) {
		if (inv == null) return;
		EntityPlayer player = inv.player;

		List<ItemStack> knowledge = new ArrayList<>();

		if (player != null) {
			double playerEmc = getPlayerEmcSafe(player);
			if (playerEmc > inv.emc || inv.emc == 0.0) {
				inv.emc = playerEmc;
			}
			knowledge = new ArrayList<>(Transmutation.getKnowledge(player));
		}

		// 去重与规范化
		List<ItemStack> cleanKnowledge = new ArrayList<>();
		for (ItemStack k : knowledge) {
			if (k == null || k.getItem() == null) continue;
			k.stackSize = 1;
			boolean alreadyPresent = false;
			for (ItemStack existing : cleanKnowledge) {
				if (areKnowledgeStacksEqual(existing, k)) {
					alreadyPresent = true;
					break;
				}
			}
			if (!alreadyPresent) {
				cleanKnowledge.add(k);
			}
		}
		knowledge = cleanKnowledge;

		// 清空输出槽
		for (int idx : MATTER_INDEXES) if (idx < inv.inventory.length) inv.inventory[idx] = null;
		for (int idx : FUEL_INDEXES) if (idx < inv.inventory.length) inv.inventory[idx] = null;

		// 排序
		knowledge.sort((s1, s2) -> Double.compare(getEmcValueDouble(s2), getEmcValueDouble(s1)));

		// 接入 GTNH 版的搜索引擎！
		ItemSearchHelper searchHelper = ItemSearchHelper.create(inv.filter, inv.emc);

		ItemStack lock = inv.inventory.length > 8 ? inv.inventory[8] : null;
		List<ItemStack> matching = new ArrayList<>();

		for (ItemStack stack : knowledge) {
			if (stack == null) continue;
			double stackEmc = getEmcValueDouble(stack);
			if (stackEmc > inv.emc) continue;
			if (lock != null && stackEmc > getEmcValueDouble(lock)) continue;

			// 使用 ItemSearchHelper 进行测试
			if (!searchHelper.doesItemMatchFilter(stack)) continue;
			matching.add(stack);
		}

		int totalMatching = matching.size();
		int totalPages = totalMatching > 0 ? (int) Math.ceil((double) totalMatching / 12.0) : 1;
		if (inv.searchpage < 0) inv.searchpage = 0;
		if (inv.searchpage >= totalPages && totalPages > 0) inv.searchpage = totalPages - 1;

		MATCHING_ITEM_COUNTS.put(inv, totalMatching);
		TOTAL_PAGES.put(inv, totalPages);

		int skip = inv.searchpage * 12;
		int matterIdx = 0, fuelIdx = 0;

		for (int i = skip; i < matching.size(); i++) {
			ItemStack stack = matching.get(i).copy();
			stack.stackSize = 1;

			if (isStackFuelSafe(stack)) {
				if (fuelIdx < FUEL_INDEXES.length && FUEL_INDEXES[fuelIdx] < inv.inventory.length) {
					inv.inventory[FUEL_INDEXES[fuelIdx++]] = stack;
				}
			} else {
				if (matterIdx < MATTER_INDEXES.length && MATTER_INDEXES[matterIdx] < inv.inventory.length) {
					inv.inventory[MATTER_INDEXES[matterIdx++]] = stack;
				}
			}

			if (matterIdx >= MATTER_INDEXES.length && fuelIdx >= FUEL_INDEXES.length) break;
		}
	}

	// --- GUI 逻辑 ---

	@SideOnly(Side.CLIENT)
	private static GuiTextField getTextBoxFilter(GuiContainer gui) {
		if (gui == null) return null;
		try {
			if (FIELD_GUI_TEXTBOX == null) {
				FIELD_GUI_TEXTBOX = gui.getClass().getDeclaredField("textBoxFilter");
				FIELD_GUI_TEXTBOX.setAccessible(true);
			}
			return (GuiTextField) FIELD_GUI_TEXTBOX.get(gui);
		} catch (Throwable ignored) {
			return null;
		}
	}

	@SideOnly(Side.CLIENT)
	public static void handleTransmutationInitGui(GuiContainer gui) {
		if (gui == null) return;
		GuiTextField textBox = getTextBoxFilter(gui);
		if (textBox != null) {
			textBox.setMaxStringLength(128);
		}
	}

	@SideOnly(Side.CLIENT)
	public static boolean handleTransmutationKeyTyped(GuiContainer gui, char typedChar, int keyCode) {
		if (gui == null) return false;
		GuiTextField textBox = getTextBoxFilter(gui);
		if (textBox == null || !textBox.isFocused()) return false;

		TransmutationInventory inv = null;
		if (gui.inventorySlots instanceof TransmutationContainer) {
			inv = ((TransmutationContainer) gui.inventorySlots).transmutationInventory;
		}
		if (inv == null) return false;

		if (keyCode == Keyboard.KEY_UP) {
			String prev = SearchHistoryManager.navigateUp(textBox.getText());
			textBox.setText(prev);
			inv.filter = prev.toLowerCase(Locale.ROOT);
			inv.searchpage = 0;
			inv.updateOutputs();
			return true;
		}

		if (keyCode == Keyboard.KEY_DOWN) {
			String next = SearchHistoryManager.navigateDown(textBox.getText());
			textBox.setText(next);
			inv.filter = next.toLowerCase(Locale.ROOT);
			inv.searchpage = 0;
			inv.updateOutputs();
			return true;
		}

		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			String text = textBox.getText();
			if (text != null && !text.trim().isEmpty()) {
				SearchHistoryManager.addHistory(text);
			}
			textBox.setFocused(false);
			return true;
		}

		return false;
	}

	@SideOnly(Side.CLIENT)
	public static void handleTransmutationGuiClosed(GuiContainer gui) {
		if (gui == null) return;
		GuiTextField textBox = getTextBoxFilter(gui);
		if (textBox != null) {
			String text = textBox.getText();
			if (text != null && !text.trim().isEmpty()) {
				SearchHistoryManager.addHistory(text);
			}
		}
		SearchHistoryManager.resetCursor();
	}

	// --- Output / Consume Logic ---

	public static ItemStack handleOutputTake(Slot slot, int amount, TransmutationInventory inv) {
		if (slot == null || inv == null || !slot.getHasStack()) return null;
		ItemStack copy = slot.getStack().copy();
		copy.stackSize = amount;
		double cost = getEmcValueDouble(copy) * amount;

		if (cost > inv.emc) {
			copy.stackSize = 0;
			return copy;
		}

		EntityPlayer player = inv.player;
		UUID uuid = player != null ? player.getUniqueID() : null;

		synchronized (getPlayerLock(uuid)) {
			removeInventoryEmc(inv, cost);

			// 替换原有的 inv.checkForUpdates() 为你们的 updateOutputs()
			inv.updateOutputs();

			if (player != null) {
				syncPlayerEMCAndKnowledge(player, inv.emc, null);
			} else {
				handleUpdateOutputs(inv, true);
			}
		}
		return copy;
	}

	public static void handleConsume(Slot slot, ItemStack stack, TransmutationInventory inv) {
		if (stack == null || inv == null) return;
		ItemStack copy = normalizeKnowledgeStack(stack);
		EntityPlayer player = inv.player;

		synchronized (getPlayerLock(player != null ? player.getUniqueID() : null)) {
			while (!handleInventoryHasMaxedEmc(inv) && stack.stackSize > 0) {
				double itemEmc = getEmcValueDouble(stack);
				if (itemEmc <= 0.0 && !isTome(stack.getItem())) break;
				handleInventoryAddEmc(inv, itemEmc);
				stack.stackSize--;
			}

			if (stack.stackSize <= 0 && slot != null) {
				slot.putStack(null);
			} else if (slot != null) {
				slot.onSlotChanged();
			}

			if (player != null) {
				addKnowledgeSafe(copy, player);
				syncPlayerEMCAndKnowledge(player, inv.emc, copy);
			} else {
				handleUpdateOutputs(inv, true);
			}
		}
	}

	// --- TileEntity and Item EMC Interfaces (Direct Casts) ---

	public static double getTileStoredEmc(Object tile) {
		return tile instanceof IEmcStorage ? ((IEmcStorage) tile).getStoredEmc() : 0.0;
	}

	public static double getTileMaximumEmc(Object tile) {
		return tile instanceof IEmcStorage ? ((IEmcStorage) tile).getMaximumEmc() : Double.MAX_VALUE;
	}

	public static double acceptTileEmc(Object tile, ForgeDirection side, double amount) {
		return tile instanceof IEmcAcceptor ? ((IEmcAcceptor) tile).acceptEMC(side, amount) : 0.0;
	}

	public static double provideTileEmc(Object tile, ForgeDirection side, double amount) {
		return tile instanceof IEmcProvider ? ((IEmcProvider) tile).provideEMC(side, amount) : 0.0;
	}

	public static double getItemStoredEmc(IItemEmc item, ItemStack stack) {
		return item != null ? item.getStoredEmc(stack) : 0.0;
	}

	public static double extractItemEmc(IItemEmc item, ItemStack stack, double amount) {
		return item != null ? item.extractEmc(stack, amount) : 0.0;
	}

	// --- Utils ---

	public static boolean isStackFuelSafe(ItemStack stack) {
		return stack != null && stack.getItem() != null && FuelMapper.isStackFuel(stack);
	}

	public static boolean isItemEmc(ItemStack stack) {
		return stack != null && stack.getItem() instanceof IItemEmc;
	}

	public static boolean isTome(Item item) {
		return item == ObjHandler.tome;
	}

	public static void addKnowledgeSafe(ItemStack stack, EntityPlayer player) {
		if (stack == null || player == null) return;
		ItemStack single = normalizeKnowledgeStack(stack);
		if (!Transmutation.hasKnowledgeForStack(single, player)) {
			Transmutation.addKnowledge(single, player);
		}
	}

	public static String formatEmc(double emc) {
		return FULL_FORMATTER.format(emc);
	}
}
