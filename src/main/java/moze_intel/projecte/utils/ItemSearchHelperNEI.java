package moze_intel.projecte.utils;

import codechicken.nei.SearchField;
import codechicken.nei.api.ItemFilter;
import net.minecraft.item.ItemStack;

public class ItemSearchHelperNEI extends ItemSearchHelper
{
	ItemFilter filter;
	public ItemSearchHelperNEI(String searchString)
	{
		super(searchString);
		filter = SearchField.getFilter(searchString);
	}

	@Override
	public boolean match(ItemStack itemStack)
	{
		return filter.matches(itemStack);
	}
}
