package moze_intel.projecte.emc;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class NBTSimpleStack extends SimpleStack {
	public NBTTagCompound nbt;

	public NBTSimpleStack(int id, int qnty, int damage, NBTTagCompound nbt) {
		super(id, qnty, damage);
		this.nbt = (NBTTagCompound) nbt.copy();
	}

	public NBTSimpleStack(ItemStack stack, NBTTagCompound filtered) {
		super(stack);
		nbt = filtered;
	}

	@Override
	public ItemStack toItemStack() {
		if (!isValid()) return null;

		Item item = Item.getItemById(id);
		if (item == null) return null;

		ItemStack stack = new ItemStack(item, qnty, damage);
		stack.stackTagCompound = (NBTTagCompound) nbt.copy();

		return stack;
	}

	@Override
	public SimpleStack copy() {
		if (nbt == null || nbt.hasNoTags())
			return new SimpleStack(id, qnty, damage);
		return new NBTSimpleStack(id, qnty, damage, nbt);
	}

	@Override
	public int hashCode() {
		return (id << 15 | damage) * 31 + nbt.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof NBTSimpleStack other)
			return id == other.id && qnty == other.qnty && damage == other.damage && nbt.equals(other.nbt);
		return false;
	}

	@Override
	public String toString() {
		Object obj = Item.itemRegistry.getObjectById(id);

		if (obj != null)
			return Item.itemRegistry.getNameForObject(obj) + " " + qnty + " " + damage + " " + nbt;

		return "id:" + id + " damage:" + damage + " qnty:" + qnty + " nbt:" + nbt;
	}
}
