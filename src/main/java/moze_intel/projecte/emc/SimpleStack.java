package moze_intel.projecte.emc;

import moze_intel.projecte.integration.GregTech.GTItemHelper;
import moze_intel.projecte.integration.GregTech.GTSimpleStack;
import moze_intel.projecte.config.ProjectEConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class SimpleStack
{
	public int id;
	public int damage;
	public int qnty;
	public NBTTagCompound nbt;

	public SimpleStack(int id, int qnty, int damage, NBTTagCompound nbt)
	{
		this.id = id;
		this.qnty = qnty;
		this.damage = damage;
		this.nbt = nbt;
	}

	public SimpleStack(int id, int qnty, int damage)
	{
		this(id, qnty, damage, null);
	}

	public SimpleStack(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null)
		{
			id = -1;
		}
		else
		{
			id = Item.itemRegistry.getIDForObject(stack.getItem());
			damage = stack.getItemDamage();
			qnty = stack.stackSize;
			nbt = ProjectEConfig.getFilteredNBT(stack);
		}
	}

	public boolean isValid()
	{
		return id != -1;
	}

	public ItemStack toItemStack()
	{
		if (!isValid()) return null;

		Item item = Item.getItemById(id);
		if (item != null) {
			ItemStack stack = new ItemStack(item, qnty, damage);
			if (this.nbt != null) {
				stack.setTagCompound((NBTTagCompound) this.nbt.copy());
			}
			return stack;
		}

		return null;
	}

	public SimpleStack copy()
	{
		return new SimpleStack(id, qnty, damage, nbt != null ? (NBTTagCompound) nbt.copy() : null);
	}

	@Override
	public int hashCode()
	{
		// Hash 算法
		int code = id << 15 | damage;
		if (nbt != null) {
			code = code * 31 + nbt.hashCode();
		}
		return code;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof SimpleStack other)
		{
			// 移除 OreDictionary.WILDCARD_VALUE
			// 严格 equals 匹配
			boolean nbtMatches = (this.nbt == null && other.nbt == null) || (this.nbt != null && this.nbt.equals(other.nbt));
			return this.id == other.id && this.qnty == other.qnty && this.damage == other.damage && nbtMatches;
		}
		return false;
	}

	@Override
	public String toString()
	{
		Object obj = Item.itemRegistry.getObjectById(id);
		String name = obj != null ? Item.itemRegistry.getNameForObject(obj) : "id:" + id;
		return name + " " + qnty + " " + damage + (nbt != null ? " " + nbt.toString() : "");
	}

	public static SimpleStack getFor(ItemStack is) {
		if (GTItemHelper.isGTtool(is))
			return new GTSimpleStack(is);
		return new SimpleStack(is);
	}
}
