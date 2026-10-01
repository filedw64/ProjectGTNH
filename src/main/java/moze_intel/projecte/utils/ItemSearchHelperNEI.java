package moze_intel.projecte.utils;

import codechicken.nei.SearchField;
import codechicken.nei.api.ItemFilter;
import net.minecraft.item.ItemStack;
import java.util.function.Predicate;

public class ItemSearchHelperNEI extends ItemSearchHelper {
	private final ItemFilter neiFilter;
	private final Predicate<ItemStack> customFilter;

	public ItemSearchHelperNEI(String searchString, double currentEmc) {
		super(searchString, currentEmc);

		// 解析 PE 特有的标签
		this.customFilter = parseCustomFilters(searchString, currentEmc);

		// 剥离 PE 标签，将纯净的搜索词交给 NEI
		StringBuilder neiQueryBuilder = new StringBuilder();
		if (searchString != null) {
			for (String token : searchString.split("\\s+")) {
				String lower = token.toLowerCase();
				if (!lower.startsWith("emc:") && !lower.startsWith("type:")) {
					neiQueryBuilder.append(token).append(" ");
				}
			}
		}

		String neiQuery = neiQueryBuilder.toString().trim();
		// 如果剥离完 PE 标签后，没有剩下的搜索词了，直接把 neiFilter 置空
		if (neiQuery.isEmpty()) {
			this.neiFilter = null;
		} else {
			this.neiFilter = SearchField.getFilter(neiQuery);
		}
	}

	@Override
	public boolean match(ItemStack itemStack) {
		// 先进行快速校验
		if (!customFilter.test(itemStack)) {
			return false;
		}
		// 如果 neiFilter 为 null（代表玩家只搜了 emc: 标签），直接放行
		return neiFilter == null || neiFilter.matches(itemStack);
	}
}
