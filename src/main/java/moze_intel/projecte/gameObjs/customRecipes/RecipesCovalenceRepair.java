package moze_intel.projecte.gameObjs.customRecipes;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import moze_intel.projecte.gameObjs.ObjHandler;

public class RecipesCovalenceRepair implements IRecipe
{
	private ItemStack output;

	private boolean isGTTool(ItemStack stack)
	{
		return stack.hasTagCompound() && stack.getTagCompound().hasKey("GT.ToolStats");
	}

	private boolean isTiConTool(ItemStack stack)
	{
		return stack.hasTagCompound() && stack.getTagCompound().hasKey("InfiTool");
	}

	@Override
	public boolean matches(InventoryCrafting inv, World world)
	{
		ItemStack[] dust = new ItemStack[8];
		ItemStack tool = null;
		boolean foundItem = false;
		int dustCounter = 0;

		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			ItemStack input = inv.getStackInSlot(i);

			if (input == null)
			{
				continue;
			}

			if (isItemRepairable(input))
			{
				if (!foundItem)
				{
					tool = input;
					foundItem = true;
				}
				else
				{
					return false;
				}
			}
			else if (input.getItem() == ObjHandler.covalence)
			{
				if (dustCounter < 8)
				{
					dust[dustCounter] = input;
					dustCounter++;
				}
				else
				{
					return false;
				}
			}
		}

		if (tool == null || dustCounter == 0)
		{
			return false;
		}

		if (!correctDustCount(dustCounter, tool))
		{
			return false;
		}

		int dustDamage = getDustType(tool);

		for (ItemStack stack : dust) {
			if (stack != null && stack.getItemDamage() < dustDamage) {
				return false;
			}
		}

		output = tool.copy();

		// 精准修复 NBT，防止 GT 工具变异和匠魂工具损坏标签残留
		if (isTiConTool(output))
		{
			NBTTagCompound infi = output.getTagCompound().getCompoundTag("InfiTool");
			infi.setInteger("Damage", 0);
			if (infi.getBoolean("Broken"))
			{
				infi.setBoolean("Broken", false);
			}
		}
		else if (isGTTool(output))
		{
			NBTTagCompound gt = output.getTagCompound().getCompoundTag("GT.ToolStats");
			gt.setLong("Damage", 0L);
		}
		else
		{
			// 原版工具修复
			output.setItemDamage(0);
		}

		return true;
	}

	private boolean correctDustCount(int dustCounter, ItemStack stack)
	{
		// 对于所有的 GT 和 匠魂工具，认为统一需要 3 个共价粉（不知道怎么适配，就这样吧）
		if (isGTTool(stack) || isTiConTool(stack))
		{
			return dustCounter == 3;
		}

		Item toRepair = stack.getItem();

		if (toRepair instanceof ItemSpade || toRepair instanceof ItemShears
			|| toRepair instanceof ItemFlintAndSteel || toRepair instanceof ItemFishingRod)
		{
			return dustCounter == 1;
		}

		if (toRepair instanceof ItemSword)
		{
			return dustCounter == 2;
		}

		if (toRepair instanceof ItemAxe || toRepair instanceof ItemPickaxe || toRepair instanceof ItemBow)
		{
			return dustCounter == 3;
		}

		if (toRepair instanceof ItemArmor)
		{
			ItemArmor armor = (ItemArmor) toRepair;
			return switch (armor.armorType) {
				case 0 -> dustCounter == 5;
				case 1 -> dustCounter == 8;
				case 2 -> dustCounter == 7;
				case 3 -> dustCounter == 4;
				default -> false;
			};
		}

		return dustCounter == 3;
	}

	private boolean isItemRepairable(ItemStack stack)
	{
		// GT 和 匠魂工具兼容判定
		if (isTiConTool(stack))
		{
			NBTTagCompound infi = stack.getTagCompound().getCompoundTag("InfiTool");
			return infi.getBoolean("Broken") || infi.getInteger("Damage") > 0;
		}

		if (isGTTool(stack))
		{
			NBTTagCompound gt = stack.getTagCompound().getCompoundTag("GT.ToolStats");
			if (gt.getBoolean("Electric")) return false; // 拒绝共价粉给电动工具充电
			return gt.getLong("Damage") > 0;
		}

		// 原版拦截
		if (stack.getHasSubtypes())
		{
			return false;
		}

		if (stack.getMaxDamage() == 0 || stack.getItemDamage() == 0)
		{
			return false;
		}

		Item item = stack.getItem();

		if (item instanceof ItemShears || item instanceof ItemFlintAndSteel || item instanceof ItemFishingRod || item instanceof ItemBow)
		{
			return true;
		}

		return (item instanceof ItemTool || item instanceof ItemSword || item instanceof ItemHoe || item instanceof ItemArmor);
	}

	private int getDustType(ItemStack stack)
	{
		// GT 和匠魂的工具普遍属于中后期工业级物品，这里强制要求使用高级共价粉
		if (isGTTool(stack) || isTiConTool(stack))
		{
			return 2;
		}

		Item item = stack.getItem();

		if (item instanceof ItemShears || item instanceof ItemFlintAndSteel)
		{
			return 1;
		}

		if (item instanceof ItemBow || item instanceof ItemFishingRod)
		{
			return 0;
		}

		String name = "";

		if (item instanceof ItemTool)
		{
			name = ((ItemTool) item).getToolMaterialName();
		}
		else if (item instanceof ItemSword)
		{
			name = ((ItemSword) item).getToolMaterialName();
		}
		else if (item instanceof ItemHoe)
		{
			name = ((ItemHoe) item).getToolMaterialName();
		}
		else if (item instanceof ItemArmor)
		{
			name = ((ItemArmor) item).getArmorMaterial().toString();
		}

		if (name.equals("WOOD") || name.equals("STONE") || name.equals("CLOTH"))
		{
			return 0;
		}

		if (name.equals("IRON") || name.equals("GOLD") || name.equals("CHAIN"))
		{
			return 1;
		}

		return 2;
	}

	@Override
	public ItemStack getCraftingResult(InventoryCrafting var1)
	{
		return output != null ? output.copy() : null;
	}

	@Override
	public int getRecipeSize()
	{
		return 10;
	}

	@Override
	public ItemStack getRecipeOutput()
	{
		return output;
	}
}
