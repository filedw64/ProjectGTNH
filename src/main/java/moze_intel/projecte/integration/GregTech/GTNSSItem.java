package moze_intel.projecte.integration.GregTech;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class GTNSSItem extends NormalizedSimpleStack.NSSItem {
    public String primary = "";
    public String secondary = "";
    public GTNSSItem(ItemStack stack) {
        super(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage());
        if (!GTToolHelper.isGTtool(stack) || !stack.hasTagCompound()) return;
        NBTTagCompound nbt = stack.getTagCompound().getCompoundTag("GT.ToolStats");
        primary = nbt.getString("PrimaryMaterial");
        secondary = nbt.getString("SecondaryMaterial");
    }

    @Override
    public int hashCode() {
        return (itemName + primary + secondary).hashCode() ^ damage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GTNSSItem other) {
            return super.equals(other) && this.primary.equals(other.primary) && this.secondary.equals(other.secondary);
        }
        return false;
    }

    @Override
    public String json() {
        return String.format("%s|%s{GT.ToolStats:{PrimaryMaterial:%s,SecondaryMaterial:%s}}", itemName,
            damage == OreDictionary.WILDCARD_VALUE ? "*" : damage, primary, secondary);
    }

    @Override
    public String toString() {
        return String.format("%s:%s{GT.ToolStats:{PrimaryMaterial:%s,SecondaryMaterial:%s}}", itemName,
            damage == OreDictionary.WILDCARD_VALUE ? "*" : damage, primary, secondary);
    }
}
