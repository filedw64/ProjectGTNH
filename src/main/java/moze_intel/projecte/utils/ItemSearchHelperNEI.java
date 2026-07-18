package moze_intel.projecte.utils;

import codechicken.nei.ItemList;
import codechicken.nei.SearchField;
import codechicken.nei.api.ItemFilter;
import net.minecraft.item.ItemStack;

import java.util.LinkedList;
import java.util.List;

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
