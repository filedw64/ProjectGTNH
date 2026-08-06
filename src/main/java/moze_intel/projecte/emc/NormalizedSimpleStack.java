package moze_intel.projecte.emc;

import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.integration.GregTech.GTItemHelper;
import moze_intel.projecte.integration.GregTech.GTNSSItem;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class NormalizedSimpleStack {

	public static <V extends Comparable<V>> void addMappings(IMappingCollector<NormalizedSimpleStack, V> mapper) {
		idWithUsedMeta.forEach((id, metaSet) -> {
			metaSet.remove(OreDictionary.WILDCARD_VALUE);
			metaSet.add(0);
			NormalizedSimpleStack stackWildcard = new NSSItem(id, OreDictionary.WILDCARD_VALUE);
			for (int metadata : metaSet) {
				mapper.addConversion(1, stackWildcard, Collections.singletonList(new NSSItem(id, metadata)));
			}
		});

		oreDictMap.forEach((odName, nssOre) -> {
			List<ItemStack> list = ItemHelper.getODItems(odName);
			for (ItemStack is: list) {
				mapper.addConversion(1, nssOre, Collections.singletonList(NormalizedSimpleStack.forItem(is)));
				mapper.addConversion(1, NormalizedSimpleStack.forItem(is), Collections.singletonList(nssOre));
			}
		});
	}

	public static void clearMap() {
		idWithUsedMeta.clear();
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

	public static NSSItem forItem(String itemName, int damage) {
		if (Item.itemRegistry.getObject(itemName) == null) {
			PELogger.logError("Could not create NSSItem: %s", itemName);
			return null;
		}
		NSSItem nss = new NSSItem(itemName, damage);
		Set<Integer> usedMeta;
		if (!idWithUsedMeta.containsKey(itemName)) {
			usedMeta = new HashSet<>();
			idWithUsedMeta.put(itemName, usedMeta);
		}
		else {
			usedMeta = idWithUsedMeta.get(itemName);
		}
		usedMeta.add(damage);
		return nss;
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
			return new GTNSSItem(stack);
		return forItem(stack.getItem(), stack.getItemDamage());
	}

	public static class NSSItem extends NormalizedSimpleStack {
		public final String itemName;
		public final int damage;

		protected NSSItem(String itemName, int damage) {
			this.itemName = itemName;
			this.damage = damage;
		}

		@Override
		public int hashCode() {
			return itemName.hashCode() ^ damage;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof NSSItem other) {
				return this.itemName.equals(other.itemName) && this.damage == other.damage;
			}
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
		if (fakeMap.containsKey(desc))
			return fakeMap.get(desc);
		NSSFake nss = new NSSFake(desc);
		fakeMap.put(desc, nss);
		return nss;
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
		if (fluidMap.containsKey(fluid))
			return fluidMap.get(fluid);
		NSSFluid nss = new NSSFluid(fluid);
		fluidMap.put(fluid, nss);
		return nss;
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
		private NSSFluid(Fluid f) {
			this.name = f.getName();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof NSSFluid other) {
				return name.equals(other.name);
			}
			return false;
		}

		@Override
		public String json()
		{
			return "FLUID|" + this.name;
		}

		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public String toString() {
			return "Fluid: " + this.name;
		}
	}

	private static final Map<String, NSSOreDictionary> oreDictMap = new HashMap<>();

	public static NSSOreDictionary forOreDictionary(String odName) {
		if (oreDictMap.containsKey(odName))
			return oreDictMap.get(odName);
		List<ItemStack> list = OreDictionary.getOres(odName);
		if (list == null || list.isEmpty()) {
			return null;
		}
		NSSOreDictionary nss = new NSSOreDictionary(odName);
		oreDictMap.put(odName, nss);
		return nss;
	}

	public static class NSSOreDictionary extends NormalizedSimpleStack {
		public final String od;

		private NSSOreDictionary(String od) {
			this.od = od;
		}

		@Override
		public int hashCode()
		{
			return od.hashCode();
		}

		@Override
		public boolean equals(Object o)
		{
			if (o instanceof NSSOreDictionary other) {
				return this.od.equals(other.od);
			}
			return false;
		}

		@Override
		public String json()
		{
			return "OD|" + this.od;
		}

		@Override
		public String toString() {
			return "OD: " + od;
		}
	}

	public static NSSItem fromJson(String jsonStr) {
		int pipeIndex = jsonStr.indexOf('|');
		String name = jsonStr.substring(0, pipeIndex);
		String metaStr = jsonStr.substring(pipeIndex + 1).split("\\{")[0];
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
		return forItem(name, itemDamage);
	}
}