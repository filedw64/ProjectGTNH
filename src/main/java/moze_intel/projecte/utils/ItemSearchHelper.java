package moze_intel.projecte.utils;

import moze_intel.projecte.math.ExactEMC;

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
	public final ExactEMC currentEmc;

	// 兼容原版的 create，默认 EMC 为最大值
	public static ItemSearchHelper create(String searchString) {
		return create(searchString, (ExactEMC) null);
	}

	// 在 GUI 中调用这个，把玩家转化桌里的 EMC 传进来
	public static ItemSearchHelper create(String searchString, ExactEMC currentEmc) {
		if (Loader.isModLoaded("NotEnoughItems")) {
			return new ItemSearchHelperNEI(searchString, currentEmc);
		}
		return new DefaultSearch(searchString, currentEmc);
	}

	public ItemSearchHelper(String searchString, ExactEMC currentEmc) {
		this.searchString = searchString != null ? searchString : "";
		this.currentEmc = currentEmc;
	}

	public final boolean doesItemMatchFilter(ItemStack itemStack) {
		if (itemStack == null || itemStack.getItem() == null) return false;
		return this.match(itemStack);
	}

	protected abstract boolean match(ItemStack itemStack);

	// 自定义高级语法解析器 (被 NEI 和 Default 共享)
	protected static Predicate<ItemStack> parseCustomFilters(String searchString, ExactEMC currentEmc) {
		Predicate<ItemStack> predicate = stack -> true;
		if (searchString == null || searchString.isEmpty()) return predicate;

		for (String token : searchString.split("\\s+")) {
			token = token.toLowerCase();
			if (token.startsWith("emc:")) {
				try { predicate = predicate.and(parseEmcFilter(token.substring(4), currentEmc)); }
                catch (IllegalArgumentException | ArithmeticException invalid) { predicate = stack -> false; }
			} else if (token.startsWith("type:")) {
				predicate = predicate.and(parseTypeFilter(token.substring(5)));
			}
		}
		return predicate;
	}

	private static Predicate<ItemStack> parseEmcFilter(String raw, ExactEMC currentEmc) {
		if (raw.equals("aff") || raw.equals("buy")) {
			return stack -> {
				ExactEMC emc = EMCHelper.getEmcValueExact(stack);
				return emc.signum() > 0 && (currentEmc == null || emc.compareTo(currentEmc) <= 0);
			};
		}
		java.util.regex.Matcher range = java.util.regex.Pattern.compile("^(.+?)(?<![eE])-([+]?[^-].*)$").matcher(raw);
        if (range.matches()) {
            String[] parts = { range.group(1), range.group(2) };
			ExactEMC min = parseEmcNumber(parts[0]);
			ExactEMC max = parseEmcNumber(parts[1]);
			return stack -> {
				ExactEMC emc = EMCHelper.getEmcValueExact(stack);
				return emc.compareTo(min) >= 0 && emc.compareTo(max) <= 0;
			};
		}
		if (raw.startsWith("<=")) {
			ExactEMC max = parseEmcNumber(raw.substring(2));
			return stack -> EMCHelper.getEmcValueExact(stack).compareTo(max) <= 0;
		}
		if (raw.startsWith("<")) {
			ExactEMC max = parseEmcNumber(raw.substring(1));
			return stack -> EMCHelper.getEmcValueExact(stack).compareTo(max) < 0;
		}
		if (raw.startsWith(">=")) {
			ExactEMC min = parseEmcNumber(raw.substring(2));
			return stack -> EMCHelper.getEmcValueExact(stack).compareTo(min) >= 0;
		}
		if (raw.startsWith(">")) {
			ExactEMC min = parseEmcNumber(raw.substring(1));
			return stack -> EMCHelper.getEmcValueExact(stack).compareTo(min) > 0;
		}

		ExactEMC exact = parseEmcNumber(raw);
		// double 类型直接 == 判断可能会有精度丢失问题(经典！)，所以用 Math.abs 允许微小误差
		return stack -> EMCHelper.getEmcValueExact(stack).equals(exact);
	}

    private static ExactEMC parseEmcNumber(String text) {
        if (text == null) throw new IllegalArgumentException("Missing EMC filter");
        text = text.trim().replace(",", "");
        int exponent = 0;
        if (text.endsWith("k")) exponent = 3;
        else if (text.endsWith("m")) exponent = 6;
        else if (text.endsWith("b") || text.endsWith("g")) exponent = 9;
        else if (text.endsWith("t")) exponent = 12;
        else if (text.endsWith("p")) exponent = 15;
        else if (text.endsWith("e")) exponent = 18;
        if (exponent != 0) text = text.substring(0, text.length() - 1);
        return ExactEMC.parse(text).multiply(java.math.BigInteger.TEN.pow(exponent));
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

		public DefaultSearch(String searchString, ExactEMC currentEmc) {
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
