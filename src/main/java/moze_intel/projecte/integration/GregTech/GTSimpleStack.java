package moze_intel.projecte.integration.GregTech;

import moze_intel.projecte.emc.SimpleStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class GTSimpleStack extends SimpleStack {
    public String primary;
    public String secondary;

    public GTSimpleStack(int id, int qnty, int damage, String primary, String secondary) {
        super(id, qnty, damage);
        this.primary = primary;
        this.secondary = secondary;
    }

    public GTSimpleStack(ItemStack is) {
        super(is);
        if(!GTToolHelper.isGTtool(is) || !is.hasTagCompound()) return;
        NBTTagCompound nbt = is.getTagCompound().getCompoundTag("GT.ToolStats");
        primary = nbt.getString("PrimaryMaterial");
        secondary = nbt.getString("SecondaryMaterial");
    }

    @Override
    public int hashCode() {
        return (primary + secondary).hashCode() ^ id;
    }

    @Override
    public SimpleStack copy() {
        return new GTSimpleStack(id, qnty, damage, primary, secondary);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GTSimpleStack other)
            return super.equals(other) && this.primary.equals(other.primary) && this.secondary.equals(other.secondary);
        return false;
    }

    @Override
    public String toString() {
        Item item = Item.getItemById(id);

        if (item != null) {
            return Item.itemRegistry.getNameForObject(item) + " " + qnty + " " + damage + " " + primary + " " + secondary;
        }

        return "id:" + id + " damage:" + damage + " qnty:" + qnty + " primary:" + primary + " secondary" + secondary;
    }
}
