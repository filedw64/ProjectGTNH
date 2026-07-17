package moze_intel.projecte.utils;

import cpw.mods.fml.common.Loader;

import net.minecraft.item.ItemStack;

import java.util.Locale;

public abstract class ItemSearchHelper
{
	public static ItemSearchHelper create(String searchString) {
		if (Loader.isModLoaded("NotEnoughItems")) {
			return new ItemSearchHelperNEI(searchString);
		}
		return new DefaultSearch(searchString);
	}

	public String searchString;
	public ItemSearchHelper(String searchString) {
		this.searchString = searchString;
	}

	public final boolean doesItemMatchFilter(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) return false;
		try {
			return this.match(itemStack);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	protected abstract boolean match(ItemStack itemStack);

	private static class DefaultSearch extends ItemSearchHelper
	{
		public DefaultSearch(String searchString)
		{
			super(searchString);
		}

		public boolean match(ItemStack stack)
		{
            String displayName;
			try
			{
				displayName = stack.getDisplayName();
			} catch (Exception e) {
				e.printStackTrace();
				//From old code... Not sure if intended to not remove items that crash on getDisplayName
				return true;
			}

            return searchString.isEmpty() || displayName.contains(searchString);
        }
	}
}
