package moze_intel.projecte.utils;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.emc.FuelMapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

import java.util.function.Predicate;

public abstract class ItemSearchHelper {
	public final String searchString;
	public final double currentEmc;

	// 兼容原版的 create，默认 EMC 为最大值
	public static ItemSearchHelper create(String searchString) {
		return create(searchString, Double.MAX_VALUE);
	}

	// 在 GUI 中调用这个，把玩家转化桌里的 EMC 传进来
	public static ItemSearchHelper create(String searchString, double currentEmc) {
		if (Loader.isModLoaded("NotEnoughItems")) {
			return new ItemSearchHelperNEI(searchString, currentEmc);
		}
		return new DefaultSearch(searchString, currentEmc);
	}

	public ItemSearchHelper(String searchString, double currentEmc) {
		this.searchString = searchString != null ? searchString : "";
		this.currentEmc = currentEmc;
	}

	public final boolean doesItemMatchFilter(ItemStack itemStack) {
		if (itemStack == null || itemStack.getItem() == null) return false;
		return this.match(itemStack);
	}

	protected abstract boolean match(ItemStack itemStack);

	// 自定义高级语法解析器 (被 NEI 和 Default 共享)
	protected static Predicate<ItemStack> parseCustomFilters(String searchString, double currentEmc) {
		Predicate<ItemStack> predicate = stack -> true;
		if (searchString == null || searchString.isEmpty()) return predicate;

		for (String token : searchString.split("\\s+")) {
			token = token.toLowerCase();
			if (token.startsWith("emc:")) {
				predicate = predicate.and(parseEmcFilter(token.substring(4), currentEmc));
			} else if (token.startsWith("type:")) {
				predicate = predicate.and(parseTypeFilter(token.substring(5)));
			}
		}
		return predicate;
	}

	private static Predicate<ItemStack> parseEmcFilter(String raw, double currentEmc) {
		if (raw.equals("aff") || raw.equals("buy")) {
			return stack -> {
				double emc = EMCHelper.getEmcValue(stack);
				return emc > 0 && emc <= currentEmc;
			};
		}
		if (raw.contains("-") && !raw.startsWith("-")) {
			String[] parts = raw.split("-", 2);
			double min = parseEmcNumber(parts[0]);
			double max = parseEmcNumber(parts[1]);
			return stack -> {
				double emc = EMCHelper.getEmcValue(stack);
				return emc >= min && emc <= max;
			};
		}
		if (raw.startsWith("<=")) {
			double max = parseEmcNumber(raw.substring(2));
			return stack -> EMCHelper.getEmcValue(stack) <= max;
		}
		if (raw.startsWith("<")) {
			double max = parseEmcNumber(raw.substring(1));
			return stack -> EMCHelper.getEmcValue(stack) < max;
		}
		if (raw.startsWith(">=")) {
			double min = parseEmcNumber(raw.substring(2));
			return stack -> EMCHelper.getEmcValue(stack) >= min;
		}
		if (raw.startsWith(">")) {
			double min = parseEmcNumber(raw.substring(1));
			return stack -> EMCHelper.getEmcValue(stack) > min;
		}

		double exact = parseEmcNumber(raw);
		// double 类型直接 == 判断可能会有精度丢失问题(经典！)，所以用 Math.abs 允许微小误差
		return stack -> Math.abs(EMCHelper.getEmcValue(stack) - exact) < 0.001;
	}

	private static double parseEmcNumber(String str) {
		if (str == null) return 0.0;
		str = str.trim().replace(",", "");
		if (str.isEmpty()) return 0.0;

		double multiplier = 1.0;
		if (str.endsWith("k")) { multiplier = 1_000.0; str = str.substring(0, str.length()-1); }
		else if (str.endsWith("m")) { multiplier = 1_000_000.0; str = str.substring(0, str.length()-1); }
		else if (str.endsWith("b") || str.endsWith("g")) { multiplier = 1_000_000_000.0; str = str.substring(0, str.length()-1); }
		else if (str.endsWith("t")) { multiplier = 1_000_000_000_000.0; str = str.substring(0, str.length()-1); }
		else if (str.endsWith("p")) { multiplier = 1_000_000_000_000_000.0; str = str.substring(0, str.length()-1); }
		else if (str.endsWith("e")) { multiplier = 1_000_000_000_000_000_000.0; str = str.substring(0, str.length()-1); }

		try {
			return Double.parseDouble(str) * multiplier;
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private static Predicate<ItemStack> parseTypeFilter(String type) {
		if (type.equals("fuel")) return FuelMapper::isStackFuel;
		if (type.equals("matter") || type.equals("nonfuel")) return stack -> !FuelMapper.isStackFuel(stack);
		if (type.equals("block")) return stack -> stack.getItem() instanceof ItemBlock;
		if (type.equals("tool") || type.equals("weapon")) return stack -> stack.getItem() instanceof ItemTool || stack.getItem() instanceof ItemSword || stack.getItem() instanceof ItemBow;
		if (type.equals("armor")) return stack -> stack.getItem() instanceof ItemArmor;
		return stack -> false;
	}

	// 默认回退搜索
	private static class DefaultSearch extends ItemSearchHelper {
		private final Predicate<ItemStack> customFilter;
		private final String basicQuery;

		public DefaultSearch(String searchString, double currentEmc) {
			super(searchString, currentEmc);
			this.customFilter = parseCustomFilters(searchString, currentEmc);

			StringBuilder sb = new StringBuilder();
			for (String token : searchString.split("\\s+")) {
				if (!token.toLowerCase().startsWith("emc:") && !token.toLowerCase().startsWith("type:")) {
					sb.append(token).append(" ");
				}
			}
			this.basicQuery = sb.toString().trim().toLowerCase();
		}

		@Override
		public boolean match(ItemStack stack) {
			if (!customFilter.test(stack)) return false;
			if (basicQuery.isEmpty()) return true;

			String displayName = stack.getDisplayName().toLowerCase();
			String id = Item.itemRegistry.getNameForObject(stack.getItem());
			return displayName.contains(basicQuery) || (id != null && id.toLowerCase().contains(basicQuery));
		}
	}
}
