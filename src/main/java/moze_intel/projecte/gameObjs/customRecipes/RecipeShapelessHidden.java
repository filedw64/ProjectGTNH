package moze_intel.projecte.gameObjs.customRecipes;

import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.items.KleinStar;
import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

// This is literally just ShapelessOreRecipe, unchanged. NEI doesn't auto-pick up shapeless recipes registered this way,
// thus hiding those recipes from the Shapeless Recipes list.
public class RecipeShapelessHidden implements IRecipe
{
	private final ItemStack output;
	private final ArrayList<Object> input = new ArrayList<>();

	public RecipeShapelessHidden(ItemStack result, Object... recipe) {
		output = result.copy();
		for (int i = 0, recipeLength = recipe.length; i < recipeLength; i++) {
			Object in = recipe[i];
			if (in instanceof ItemStack is)
				input.add(is.copy());
			else if (in instanceof Item item)
				input.add(new ItemStack(item));
			else if (in instanceof Block block)
				input.add(new ItemStack(block));
			else if (in instanceof String s)
				input.add(OreDictionary.getOres(s));
			else throw new RuntimeException(Arrays.toString(recipe) + " -> " + output);
		}
	}

	/**
	 * Returns the size of the recipe area
	 */
	@Override
	public int getRecipeSize() {
		return input.size();
	}

	@Override
	public ItemStack getRecipeOutput() {
		return output;
	}

	/**
	 * Returns an Item that is the result of this recipe
	 */
	@Override
	public ItemStack getCraftingResult(InventoryCrafting var1) {
		return output.copy();
	}

	/**
	 * Used to check if a recipe matches current crafting inventory
	 */
	@Override
	public boolean matches(InventoryCrafting inv, World world)
	{
		ArrayList<Object> required = new ArrayList<>(input);

		double storedEMC = 0;
		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			ItemStack stack = inv.getStackInSlot(i);
			if (stack != null && stack.getItem() == ObjHandler.kleinStars)
			{
				storedEMC += KleinStar.getEmc(stack);
			}
		}

		if (output.getItem() == ObjHandler.kleinStars)
		{
			if (!output.hasTagCompound())
			{
				output.setTagCompound(new NBTTagCompound());
			}
			KleinStar.setEmc(output, storedEMC);
		}

		for (int x = 0; x < inv.getSizeInventory(); x++)
		{
			ItemStack slot = inv.getStackInSlot(x);
			if (slot == null) continue;

			boolean inRecipe = false;
			for (Object next : required) {
				boolean match = false;

				if (next instanceof ItemStack stack)
					match = OreDictionary.itemMatches(stack, slot, false);
				else if (next instanceof ArrayList<?> array) {
					Iterator<?> itr = array.iterator();
					while (itr.hasNext() && !match && itr.next() instanceof ItemStack is)
						match = OreDictionary.itemMatches(is, slot, false);
				}

				if (match) {
					inRecipe = true;
					required.remove(next);
					break;
				}
			}

			if (!inRecipe)
				return false;
		}

		return required.isEmpty();
	}

	/**
	 * Returns the input for this recipe, any mod accessing this value should never
	 * manipulate the values in this array as it will affect the recipe itself.
	 *
	 * @return The recipes input vales.
	 */
	public ArrayList<Object> getInput() {
		return this.input;
	}
}
