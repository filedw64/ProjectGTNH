package moze_intel.projecte.integration.GregTech;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class GTHelper {
    public static boolean isGTtool(Item item) {
        if (item == null) return false;
        return Item.itemRegistry.getNameForObject(item).startsWith("gregtech:gt.metatool");
    }

    public static boolean isGTtool(ItemStack is) {
        if (is == null) return false;
        return isGTtool(is.getItem());
    }

    public static boolean isGTtool(int id) {
        if (id < 0) return false;
        return isGTtool(Item.getItemById(id));
    }
}
