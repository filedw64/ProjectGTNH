package moze_intel.projecte.integration.ae2;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PEGeneralPurposeUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.storage.IMEInventoryHandler", modid = "appliedenergistics2")
public class EMCInventoryHandler implements IMEInventoryHandler<IAEItemStack> {

	private final TileMEEMCLink tile;
	private UUID ownerUUID;
	private String ownerName;
	private AccessRestriction access = AccessRestriction.READ_WRITE;
	private int priority = 0;

	private EntityPlayer cachedPlayer = null;
	private long cachedPlayerTimestamp = 0L;
	private static final long PLAYER_CACHE_MS = 2000L;

	private final Map<StackKey, IAEItemStack> aeItemCache = new HashMap<>();

	public EMCInventoryHandler() { this(null); }
	public EMCInventoryHandler(TileMEEMCLink tile) { this.tile = tile; }

	public void setOwner(UUID uuid, String name) {
		if ((this.ownerUUID == null && uuid != null) || (this.ownerUUID != null && !this.ownerUUID.equals(uuid))) {
			this.cachedPlayer = null;
		}
		this.ownerUUID = uuid;
		this.ownerName = name;
	}

	public UUID getOwnerUUID() { return ownerUUID; }

	public EntityPlayer getPlayer() {
		long now = System.currentTimeMillis();
		if (cachedPlayer != null && (now - cachedPlayerTimestamp < PLAYER_CACHE_MS)) {
			if (!cachedPlayer.isDead) return cachedPlayer;
			cachedPlayer = null;
		}
		EntityPlayer found = resolvePlayer();
		cachedPlayer = found;
		cachedPlayerTimestamp = now;
		return found;
	}

	private EntityPlayer resolvePlayer() {
		if (ownerUUID == null && (ownerName == null || ownerName.isEmpty())) return null;
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null) return null;
		if (ownerUUID != null) {
			for (Object obj : server.getConfigurationManager().playerEntityList) {
				if (obj instanceof EntityPlayer) {
					EntityPlayer p = (EntityPlayer) obj;
					if (ownerUUID.equals(p.getUniqueID())) return p;
				}
			}
		}
		return null;
	}

	public void setAccess(AccessRestriction access) { this.access = access != null ? access : AccessRestriction.READ_WRITE; }
	public void setPriority(int priority) { this.priority = priority; }

	// --- 过滤逻辑 (仅方块有效) ---
	private int getFilterMode() { return tile != null ? tile.getFilterMode() : 0; }
	private int getFilterPrecision() { return tile != null ? tile.getFilterPrecision() : 0; }
	private ItemStack[] getFilterSlots() { return tile != null ? tile.getFilterSlots() : null; }

	public boolean filterMatches(ItemStack stack) {
		if (stack == null) return true;
		int mode = getFilterMode();
		if (mode == 0) return true; // All Items

		ItemStack[] filter = getFilterSlots();
		if (filter == null || filter.length == 0) return true;

		int precision = getFilterPrecision();
		boolean found = false;
		for (ItemStack f : filter) {
			if (f != null && matchesPrecision(f, stack, precision)) {
				found = true;
				break;
			}
		}
		return mode == 1 ? found : !found; // 1 = Whitelist, 2 = Blacklist
	}

	public static boolean matchesPrecision(ItemStack filterStack, ItemStack targetStack, int precision) {
		if (filterStack == null || targetStack == null) return false;
		if (precision == 1) { // Fuzzy
			return filterStack.getItem() == targetStack.getItem();
		} else if (precision == 2) { // OreDict
			if (PEGeneralPurposeUtils.areKnowledgeStacksEqual(filterStack, targetStack)) return true;
			int[] fIds = OreDictionary.getOreIDs(filterStack);
			int[] tIds = OreDictionary.getOreIDs(targetStack);
			for (int fId : fIds) {
				for (int tId : tIds) {
					if (fId == tId) return true;
				}
			}
			return false;
		} else { // Exact
			return PEGeneralPurposeUtils.areKnowledgeStacksEqual(filterStack, targetStack);
		}
	}

	@Override
	public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource src) {
		if (input == null || access == AccessRestriction.READ || access == AccessRestriction.NO_ACCESS) return input;
		EntityPlayer player = getPlayer();
		if (player == null) return input;

		ItemStack inStack = input.getItemStack();
		if (inStack == null || !filterMatches(inStack)) return input;

		double itemEmc = PEGeneralPurposeUtils.getEmcValueDouble(inStack);
		if (itemEmc <= 0.0) return input;

		if (mode == Actionable.MODULATE) {
			synchronized (PEGeneralPurposeUtils.getPlayerLock(player.getUniqueID())) {
				double totalAdd = itemEmc * (double) input.getStackSize();
				double currentEmc = PEGeneralPurposeUtils.getPlayerEmcSafe(player);
				double newEmc = currentEmc + totalAdd;
				if (newEmc < 0.0 || Double.isInfinite(newEmc) || Double.isNaN(newEmc)) newEmc = Double.MAX_VALUE;
				PEGeneralPurposeUtils.syncPlayerEMCAndKnowledge(player, newEmc, inStack.copy());
			}
		}
		return null;
	}

	@Override
	public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
		if (request == null || access == AccessRestriction.WRITE || access == AccessRestriction.NO_ACCESS) return null;
		EntityPlayer player = getPlayer();
		if (player == null) return null;

		ItemStack reqStack = request.getItemStack();
		if (reqStack == null || !filterMatches(reqStack)) return null;
		if (!Transmutation.hasKnowledgeForStack(reqStack, player)) return null;

		double cost = PEGeneralPurposeUtils.getEmcValueDouble(reqStack);
		if (cost <= 0.0) return null;

		synchronized (PEGeneralPurposeUtils.getPlayerLock(player.getUniqueID())) {
			double playerEmc = PEGeneralPurposeUtils.getPlayerEmcSafe(player);
			if (playerEmc < cost) return null;

			long affordable = (long) Math.floor(playerEmc / cost);
			long toExtract = Math.min(request.getStackSize(), affordable);
			if (toExtract <= 0) return null;

			if (mode == Actionable.MODULATE) {
				double newEmc = Math.max(0.0, playerEmc - (cost * toExtract));
				PEGeneralPurposeUtils.syncPlayerEMCAndKnowledge(player, newEmc, null);
			}
			IAEItemStack result = request.copy();
			result.setStackSize(toExtract);
			return result;
		}
	}

	@Override
	public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
		if (access == AccessRestriction.WRITE || access == AccessRestriction.NO_ACCESS) return out;
		EntityPlayer player = getPlayer();
		if (player == null) return out;

		double playerEmc = PEGeneralPurposeUtils.getPlayerEmcSafe(player);
		if (playerEmc <= 0.0) return out;

		int mode = getFilterMode();
		int precision = getFilterPrecision();

		// 优化：精确白名单模式下，直接按过滤槽读取，不遍历整个上万物品的知识库
		if (mode == 1 && precision == 0) {
			ItemStack[] filter = getFilterSlots();
			if (filter != null) {
				for (ItemStack f : filter) {
					if (f == null || f.getItem() == null || !Transmutation.hasKnowledgeForStack(f, player)) continue;
					double cost = PEGeneralPurposeUtils.getEmcValueDouble(f);
					if (cost > 0.0 && cost <= playerEmc) {
						long count = (long) Math.min((double) Integer.MAX_VALUE, Math.floor(playerEmc / cost));
						if (count > 0) {
							IAEItemStack aeStack = AEApi.instance().storage().createItemStack(f);
							if (aeStack != null) {
								aeStack.setStackSize(count);
								out.add(aeStack);
							}
						}
					}
				}
				return out;
			}
		}

		// 正常遍历知识库（已包含 aeItemCache 缓存优化）
		List<ItemStack> knowledge = Transmutation.getKnowledge(player);
		if (knowledge == null || knowledge.isEmpty()) return out;

		for (ItemStack stack : knowledge) {
			if (stack == null || stack.getItem() == null || !filterMatches(stack)) continue;
			double cost = PEGeneralPurposeUtils.getEmcValueDouble(stack);
			if (cost <= 0.0 || cost > playerEmc) continue;

			long count = (long) Math.min((double) Integer.MAX_VALUE, Math.floor(playerEmc / cost));
			if (count > 0) {
				StackKey key = new StackKey(stack);
				IAEItemStack aeStack = aeItemCache.get(key);
				if (aeStack == null) {
					aeStack = AEApi.instance().storage().createItemStack(stack);
					if (aeStack != null) aeItemCache.put(key, aeStack.copy());
				}
				if (aeStack != null) {
					IAEItemStack outStack = aeStack.copy();
					outStack.setStackSize(count);
					out.add(outStack);
				}
			}
		}
		return out;
	}

	@Override
	public StorageChannel getChannel() { return StorageChannel.ITEMS; }
	@Override
	public AccessRestriction getAccess() { return access; }
	@Override
	public boolean isPrioritized(IAEItemStack stack) { return false; }
	@Override
	public boolean canAccept(IAEItemStack stack) {
		if (access == AccessRestriction.READ || access == AccessRestriction.NO_ACCESS || stack == null) return false;
		ItemStack itemStack = stack.getItemStack();
		return itemStack != null && filterMatches(itemStack) && PEGeneralPurposeUtils.getEmcValueDouble(itemStack) > 0.0;
	}
	@Override
	public int getPriority() { return priority; }
	@Override
	public int getSlot() { return 0; }
	@Override
	public boolean validForPass(int i) { return true; }

	private static class StackKey {
		private final Item item;
		private final int damage;
		private final NBTTagCompound nbt;
		private final int hash;
		public StackKey(ItemStack stack) {
			this.item = stack.getItem();
			this.damage = stack.getItemDamage();
			this.nbt = stack.stackTagCompound;
			int h = Item.getIdFromItem(item);
			h = 31 * h + damage;
			if (nbt != null) h = 31 * h + nbt.hashCode();
			this.hash = h;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof StackKey)) return false;
			StackKey other = (StackKey) obj;
			return this.item == other.item && this.damage == other.damage && (this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt));
		}
		@Override
		public int hashCode() { return hash; }
	}
}
