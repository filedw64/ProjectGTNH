package moze_intel.projecte.emc;

import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.integration.GregTech.GTItemHelper;
import moze_intel.projecte.integration.GregTech.GTNSSItem;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.config.ProjectEConfig;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class NormalizedSimpleStack {

	public static <V extends Comparable<V>> void addMappings(IMappingCollector<NormalizedSimpleStack, V> mapper) {
		idWithUsedMeta.forEach((id, metaSet) -> {
			metaSet.remove(OreDictionary.WILDCARD_VALUE);
			metaSet.add(0);
			NormalizedSimpleStack stackWildcard = forItem(id, OreDictionary.WILDCARD_VALUE, null);
			for (int metadata : metaSet) {
				mapper.addConversion(1, stackWildcard, Collections.singletonList(forItem(id, metadata, null)));
			}
		});

		oreDictMap.forEach((odName, nssOre) -> {
			List<ItemStack> list = ItemHelper.getODItems(odName);
			for (ItemStack is: list) {
				NormalizedSimpleStack nssItem = NormalizedSimpleStack.forItem(is);
				if (nssItem != null) {
					mapper.addConversion(1, nssOre, Collections.singletonList(nssItem));
					mapper.addConversion(1, nssItem, Collections.singletonList(nssOre));
				}
			}
		});
	}

	public static void clearMap() {
		idWithUsedMeta.clear();
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

	private static final Map<String, Set<Integer>> idWithUsedMeta = new HashMap<>();
	// 引入全局缓存
	private static final Map<NSSItem, NSSItem> itemMap = new HashMap<>();

	public static NSSItem forItem(String itemName, int damage, NBTTagCompound nbt) {
		if (Item.itemRegistry.getObject(itemName) == null) {
			PELogger.logError("Could not create NSSItem: %s", itemName);
			return null;
		}

		NSSItem temp = new NSSItem(itemName, damage, nbt);
		// 复用已存在的相同 NSSItem
		NSSItem nss = itemMap.computeIfAbsent(temp, k -> k);

		// 使用 computeIfAbsent 替换 containsKey + put
		idWithUsedMeta.computeIfAbsent(itemName, k -> new HashSet<>()).add(damage);

		return nss;
	}

	public static NSSItem forItem(String itemName, int damage) {
		return forItem(itemName, damage, null);
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
		if (GTItemHelper.isGTtool(stack))
			return new GTNSSItem(stack); // GTNSSItem 内部逻辑

		String id = Item.itemRegistry.getNameForObject(stack.getItem());
		return forItem(id, stack.getItemDamage(), ProjectEConfig.getFilteredNBT(stack));
	}

	public static class NSSItem extends NormalizedSimpleStack {
		public final String itemName;
		public final int damage;
		public final NBTTagCompound nbt;
		// 预先计算并缓存 HashCode
		private final int cachedHash;

		protected NSSItem(String itemName, int damage, NBTTagCompound nbt) {
			this.itemName = itemName;
			this.damage = damage;
			this.nbt = nbt;

			int code = itemName.hashCode() ^ damage;
			if (nbt != null) {
				code = code * 31 + nbt.hashCode();
			}
			this.cachedHash = code;
		}

		@Override
		public int hashCode() {
			return cachedHash;
		}

		@Override
		public boolean equals(Object obj) {
			// 快速引用比对
			if (this == obj) return true;
			if (obj instanceof NSSItem other) {
				return this.damage == other.damage
					&& this.itemName.equals(other.itemName)
					&& Objects.equals(this.nbt, other.nbt); // 简化 NBT 比对逻辑
			}
			return false;
		}

		@Override
		public String json() {
			String base = String.format("%s|%s", itemName, damage == OreDictionary.WILDCARD_VALUE ? "*" : damage);
			if (nbt != null) {
				base += "|" + nbt.toString();
			}
			return base;
		}

		@Override
		public String toString() {
			Object obj = Item.itemRegistry.getObject(itemName);
			return String.format("%s(%s:%s)%s", itemName, Item.itemRegistry.getIDForObject(obj),
				damage == OreDictionary.WILDCARD_VALUE ? "*" : damage,
				nbt != null ? " " + nbt.toString() : "");
		}
	}

	private static final Map<String, NSSFake> fakeMap = new HashMap<>();

	public static NSSFake forFake(String desc) {
		// 使用 computeIfAbsent
		return fakeMap.computeIfAbsent(desc, NSSFake::new);
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
			return desc.hashCode();
		}
	}

	private static final Map<Fluid, NSSFluid> fluidMap = new HashMap<>();

	public static NSSFluid forFluid(Fluid fluid) {
		if (fluid == null) return null;
		// 使用 computeIfAbsent
		return fluidMap.computeIfAbsent(fluid, NSSFluid::new);
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
		public final String name;
		private final int cachedHash;

		private NSSFluid(Fluid f) {
			this.name = f.getName();
			this.cachedHash = this.name.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o instanceof NSSFluid other) {
				return name.equals(other.name);
			}
			return false;
		}

		@Override
		public String json() {
			return "FLUID|" + this.name;
		}

		@Override
		public int hashCode() {
			return cachedHash;
		}

		@Override
		public String toString() {
			return "Fluid: " + this.name;
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
		private final int cachedHash;

		private NSSOreDictionary(String od) {
			this.od = od;
			this.cachedHash = od.hashCode();
		}

		@Override
		public int hashCode() {
			return cachedHash;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o instanceof NSSOreDictionary other) {
				return this.od.equals(other.od);
			}
			return false;
		}

		@Override
		public String json() {
			return "OD|" + this.od;
		}

		@Override
		public String toString() {
			return "OD: " + od;
		}
	}

	public static NSSItem fromJson(String jsonStr) {
		String[] parts = jsonStr.split("\\|", 3);
		String name = parts[0];
		String metaStr = parts[1].split("\\{")[0];
		int itemDamage;
		if (metaStr.equals("*")) {
			itemDamage = OreDictionary.WILDCARD_VALUE;
		}
		else {
			try {
				itemDamage = Integer.parseInt(metaStr);
			} catch (NumberFormatException e) {
				PELogger.logError("Cannot get NSSItem from %s: %s", jsonStr, e);
				return null;
			}
		}

		NBTTagCompound nbt = null;
		if (parts.length == 3) {
			try {
				nbt = (NBTTagCompound) JsonToNBT.func_150315_a(parts[2]);
			} catch (NBTException e) {
				PELogger.logError("Cannot parse NBT for NSSItem %s: %s", jsonStr, e);
			}
		}

		return forItem(name, itemDamage, nbt);
	}
}
