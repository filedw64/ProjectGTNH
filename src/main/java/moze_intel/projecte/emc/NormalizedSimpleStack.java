package moze_intel.projecte.emc;

import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class NormalizedSimpleStack {

	public static <V extends Comparable<V>> void addMappings(IMappingCollector<NormalizedSimpleStack, V> mapper) {
		Map<String, NSSItem> idToWildcard = new HashMap<>();
		for (NSSItem item : itemMap.values()) {
			if (item.damage == OreDictionary.WILDCARD_VALUE) continue;
			NSSItem wildcard = idToWildcard.computeIfAbsent(item.itemName, id -> new NSSItem(id, OreDictionary.WILDCARD_VALUE));
			mapper.addConversion(1, wildcard, Collections.singletonList(item));
		}

		oreDictMap.forEach((odName, nssOre) -> {
			List<ItemStack> list = ItemHelper.getODItems(odName);
			for (ItemStack is: list) {
				NormalizedSimpleStack nssItem = NormalizedSimpleStack.forItem(is);
				if (nssItem == null) continue;
				mapper.addConversion(1, nssOre, Collections.singletonList(nssItem));
				mapper.addConversion(1, nssItem, Collections.singletonList(nssOre));
			}
		});
	}

	public static void clearMap() {
		itemMap.clear(); // 清理新增的 item 缓存
		fakeMap.clear();
		fluidMap.clear();
		oreDictMap.clear();
	}

	@Override
	public abstract boolean equals(Object o);

	@Override
	public abstract String toString();

	@Override
	public abstract int hashCode();

	public abstract String json();

	private static final Map<NSSItem, NSSItem> itemMap = new HashMap<>(); // 引入全局对象池

	public static NSSItem forItem(String itemName, int damage, NBTTagCompound nbt) {
		if (nbt == null) return forItem(itemName, damage);
		if (Item.itemRegistry.getObject(itemName) == null) {
			PELogger.logError("Could not create NSSItem: %s", itemName);
			return null;
		}

		NSSItem temp = new NBTNSSItem(itemName, damage, nbt);
		return itemMap.computeIfAbsent(temp, nss -> nss); // 复用已存在的相同 NSSItem
	}

	public static NSSItem forItem(String itemName, int damage) {
		if (Item.itemRegistry.getObject(itemName) == null) {
			PELogger.logError("Could not create NSSItem: %s", itemName);
			return null;
		}

		NSSItem temp = new NSSItem(itemName, damage);
		return itemMap.computeIfAbsent(temp, nss -> nss); // 复用已存在的相同 NSSItem
	}

	public static NSSItem forItem(Block block) {
		return forItem(block, 0);
	}

	public static NSSItem forItem(Block block, int meta) {
		String id = Block.blockRegistry.getNameForObject(block);
		if (id == null) return null;
		return forItem(id, meta);
	}

	public static NSSItem forItem(Item item) {
		return forItem(item, 0);
	}

	public static NSSItem forItem(Item item, int meta) {
		String id = Item.itemRegistry.getNameForObject(item);
		if (id == null) return null;
		return forItem(id, meta);
	}

	public static NSSItem forItem(ItemStack stack) {
		if (stack == null || stack.getItem() == null) return null;
		String id = Item.itemRegistry.getNameForObject(stack.getItem());
		if (id == null) return null;
		return forItem(id, stack.getItemDamage(), ItemHelper.filterNBT(stack));
	}

	public static class NBTNSSItem extends NSSItem {
		/**
		 * Never try to change key-value pairs in it, or it will cause severe problems!
		 */
		public final NBTTagCompound nbt;

		/**
		 * {@code nbt} must be filtered by {@code ItemHelper.filterNBT(stack)}
		 */
		protected NBTNSSItem(String itemName, int damage, NBTTagCompound nbt) {
			super(itemName, damage, nbt);
			this.nbt = nbt;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true; // 快速引用比对
			if (obj instanceof NBTNSSItem other)
				return this.damage == other.damage && this.itemName.equals(other.itemName) && this.nbt.equals(other.nbt);
			return false;
		}

		@Override
		public String json() {
			return String.format("%s|%s|%s", itemName, damage == OreDictionary.WILDCARD_VALUE ? "*" : damage, nbt);
		}

		@Override
		public String toString() {
			Object obj = Item.itemRegistry.getObject(itemName);
			return String.format("%s(%s:%s)%s", itemName, Item.itemRegistry.getIDForObject(obj),
				damage == OreDictionary.WILDCARD_VALUE ? "*" : damage, nbt);
		}
	}

	public static class NSSItem extends NormalizedSimpleStack {
		public final String itemName;
		public final int damage;
		private final int cachedHash; // 预先计算并缓存 HashCode

		protected NSSItem(String itemName, int damage) {
			this.itemName = itemName;
			this.damage = damage;
			this.cachedHash = itemName.hashCode() ^ damage;
		}

		// Only for NBTNSSItem
		NSSItem(String itemName, int damage, NBTTagCompound nbt) {
			this.itemName = itemName;
			this.damage = damage;
			this.cachedHash = (itemName.hashCode() ^ damage) * 31 + nbt.hashCode();
		}

		@Override
		public int hashCode() {
			return cachedHash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true; // 快速引用比对
			if (obj instanceof NSSItem other)
				return this.damage == other.damage && this.itemName.equals(other.itemName);
			return false;
		}

		@Override
		public String json() {
			return String.format("%s|%s", itemName, damage == OreDictionary.WILDCARD_VALUE ? "*" : damage);
		}

		@Override
		public String toString() {
			Object obj = Item.itemRegistry.getObject(itemName);
			return String.format("%s(%s:%s)", itemName, Item.itemRegistry.getIDForObject(obj),
				damage == OreDictionary.WILDCARD_VALUE ? "*" : damage);
		}
	}

	private static final Map<String, NSSFake> fakeMap = new HashMap<>();

	public static NSSFake forFake(String desc) {
		return fakeMap.computeIfAbsent(desc, NSSFake::new); // 使用 computeIfAbsent
	}

	public static class NSSFake extends NormalizedSimpleStack {
		private static int fakeItemCounter = 0;
		public final String desc;
		public final int counter;

		public NSSFake(String desc) {
			this.desc = desc;
			this.counter = (++fakeItemCounter);
		}

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		@Override
		public String json() {
			return "FAKE|" + this.counter + " " + this.desc;
		}

		@Override
		public String toString() {
			return "NSSFAKE" + counter + ": " + desc;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
			// NSSFake 也是注册单例的，直接用 Object 的默认 hashCode
		}
	}

	private static final Map<Fluid, NSSFluid> fluidMap = new HashMap<>(); // Fluid 是单例的，比用 String 好

	public static NSSFluid forFluid(Fluid fluid) {
		if (fluid == null) return null;
		return fluidMap.computeIfAbsent(fluid, NSSFluid::new); // 使用 computeIfAbsent
	}

	public static NSSFluid forFluid(FluidStack stack) {
		if (stack == null || stack.getFluid() == null) return null;
		return forFluid(stack.getFluid());
	}

	public static NSSFluid forFluid(String fluidName) {
		if (fluidName == null || fluidName.isEmpty()) return null;
		return forFluid(FluidRegistry.getFluid(fluidName));
	}

	public static class NSSFluid extends NormalizedSimpleStack {
		public final Fluid fluid;

		private NSSFluid(Fluid fluid) {
			this.fluid = fluid;
		}

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		@Override
		public String json() {
			return "FLUID|" + fluid.getName();
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
			// NSSFluid 是注册单例的，直接用 Object 的默认 hashCode
		}

		@Override
		public String toString() {
			return "Fluid: " + fluid.getName();
		}
	}

	private static final Map<String, NSSOreDictionary> oreDictMap = new HashMap<>();

	public static NSSOreDictionary forOreDictionary(String odName) {
		List<ItemStack> list = OreDictionary.getOres(odName);
		if (list == null || list.isEmpty()) {
			return null;
		}
		// 使用 computeIfAbsent
		return oreDictMap.computeIfAbsent(odName, NSSOreDictionary::new);
	}

	public static class NSSOreDictionary extends NormalizedSimpleStack {
		public final String od;

		private NSSOreDictionary(String od) {
			this.od = od;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
			// NSSOreDictionary 也是注册单例的，直接用 Object 的默认 hashCode
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}

		@Override
		public String json() {
			return "OD|" + od;
		}

		@Override
		public String toString() {
			return "OD: " + od;
		}
	}

	public static NSSItem fromJson(String jsonStr) {
		String[] parts = jsonStr.split("\\|", 3);
		final String name = parts[0];
		final String metaStr = parts[1];
		int itemDamage;
		if ("*".equals(metaStr))
			itemDamage = OreDictionary.WILDCARD_VALUE;
		else {
			try {
				itemDamage = Integer.parseInt(metaStr);
			} catch (NumberFormatException e) {
				PELogger.logError("Cannot get NSSItem from %s: %s", jsonStr, e);
				return null;
			}
		}

		if (parts.length == 3) {
			try {
				NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(parts[2]);
				return forItem(name, itemDamage, nbt);
			} catch (NBTException e) {
				PELogger.logError("Cannot parse NBT for NSSItem %s: %s", jsonStr, e);
			}
		}
		return forItem(name, itemDamage);
	}
}
