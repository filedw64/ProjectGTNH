package moze_intel.projecte.integration.NEI;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.customRecipes.RecipeAlchemyBag;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;


public class NEIAlchBagHandler extends ShapelessRecipeHandler
{
	private static final String id = "crafting";

	public int[][] stackorder = new int[][]{
			{0, 0},
			{1, 0},
			{0, 1},
			{1, 1},
			{0, 2},
			{1, 2},
			{2, 0},
			{2, 1},
			{2, 2}};

	public class CachedAlchBagRecipe extends CachedRecipe
	{
		public CachedAlchBagRecipe()
		{
			ingredients = new ArrayList<>();
		}

		public CachedAlchBagRecipe(ItemStack output)
		{
			this();
			setResult(output);
		}

        public CachedAlchBagRecipe(List<?> input, ItemStack output)
		{
			this(output);
			setIngredients(input);
		}

		public void setIngredients(List<?> items)
		{
			ingredients.clear();
			for (int i = 0; i < items.size(); i++)
			{
				PositionedStack stack = new PositionedStack(items.get(i), 25 + stackorder[i][0] * 18, 6 + stackorder[i][1] * 18);
				stack.setMaxSize(1);
				ingredients.add(stack);
			}
		}

		public void setResult(ItemStack output)
		{
			result = new PositionedStack(output, 119, 24);
		}

		@Override
		public List<PositionedStack> getIngredients()
		{
			return ingredients;
		}

		@Override
		public PositionedStack getResult()
		{
			return result;
		}

		public ArrayList<PositionedStack> ingredients;
		public PositionedStack result;
	}

    @Override
	public void loadCraftingRecipes(String outputId, Object... results)
	{
		if (outputId.equals("crafting") && getClass() == NEIAlchBagHandler.class)
		{
			List<IRecipe> allrecipes = CraftingManager.getInstance().getRecipeList();
			for (IRecipe irecipe : allrecipes)
			{
				if (irecipe instanceof RecipeAlchemyBag)
				{
					List<ItemStack> ingList = new ArrayList<>();

					if (irecipe.getRecipeOutput().getItemDamage() == 0)
					{
						ingList.add(new ItemStack(ObjHandler.alchBag, 1, OreDictionary.WILDCARD_VALUE));
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
						return;
					} else
					{
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputBag());
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
					}
				}
			}
		} else
		{
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result)
	{
		List<IRecipe> allrecipes = CraftingManager.getInstance().getRecipeList();
		for (IRecipe irecipe : allrecipes)
		{
			if (NEIServerUtils.areStacksSameTypeCrafting(irecipe.getRecipeOutput(), result))
			{
				if (irecipe instanceof RecipeAlchemyBag)
				{
					List<ItemStack> ingList = new ArrayList<>();

					if (irecipe.getRecipeOutput().getItemDamage() == 0)
					{
						ingList.add(new ItemStack(ObjHandler.alchBag, 1, OreDictionary.WILDCARD_VALUE));
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
						return;
					} else
					{
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputBag());
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
					}
				}
			}
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient)
	{
		List<IRecipe> allrecipes = CraftingManager.getInstance().getRecipeList();
		for (IRecipe irecipe : allrecipes)
		{

			if (irecipe instanceof RecipeAlchemyBag)
			{
				if (NEIServerUtils.areStacksSameTypeCrafting(((RecipeAlchemyBag) irecipe).getRecipeInputDye(), ingredient)
						|| NEIServerUtils.areStacksSameTypeCrafting(((RecipeAlchemyBag) irecipe).getRecipeInputBag(), ingredient))
				{
					List<ItemStack> ingList = new ArrayList<>();

					if (irecipe.getRecipeOutput().getItemDamage() == 0)
					{
						ingList.add(new ItemStack(ObjHandler.alchBag, 1, OreDictionary.WILDCARD_VALUE));
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
						return;
					} else
					{
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputBag());
						ingList.add(((RecipeAlchemyBag) irecipe).getRecipeInputDye());
						arecipes.add(new CachedAlchBagRecipe(ingList, irecipe.getRecipeOutput()));
					}

				}
			}
		}
	}

    @Override
	public void loadTransferRects()
	{
		this.transferRects.add(new RecipeTransferRect(new Rectangle(83, 23, 25, 10), id));
	}
}
