package moze_intel.projecte.emc;

import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class SimpleStack {
	public int id;
	public int damage;

	public SimpleStack(int id, int damage) {
		this.id = id;
		this.damage = damage;
	}

	public SimpleStack(ItemStack stack) {
		if (stack == null || stack.getItem() == null) id = -1;
		else {
			id = Item.itemRegistry.getIDForObject(stack.getItem());
			damage = stack.getItemDamage();
		}
	}

	public boolean isValid()
	{
		return id != -1;
	}

	public ItemStack toItemStack() {
		if (!isValid()) return null;

		Item item = Item.getItemById(id);
		if (item != null)
			return new ItemStack(item, 1, damage);

		return null;
	}

	public SimpleStack copy() {
		return new SimpleStack(id, damage);
	}

	@Override
	public int hashCode() {
		return id << 15 | damage;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SimpleStack other)
			return this.id == other.id && this.damage == other.damage;
		return false;
	}

	@Override
	public String toString() {
		Object obj = Item.itemRegistry.getObjectById(id);

		if (obj != null)
			return Item.itemRegistry.getNameForObject(obj) + " " + damage;

		return "id:" + id + " damage:" + damage;
	}

    public static SimpleStack getFor(ItemStack is) {
		if (is == null || is.getItem() == null) return null;
		NBTTagCompound nbt = ItemHelper.filterNBT(is);
		if (nbt != null)
			return new NBTSimpleStack(is, nbt);
        return new SimpleStack(is);
    }
}
