package moze_intel.projecte.utils;

import cpw.mods.fml.common.Loader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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
		return this.match(itemStack);
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
            String displayName = stack.getDisplayName(),
                id = Item.itemRegistry.getNameForObject(stack.getItem());

            return searchString.isEmpty() || displayName.contains(searchString) || (id != null && id.contains(searchString));
        }
	}
}
