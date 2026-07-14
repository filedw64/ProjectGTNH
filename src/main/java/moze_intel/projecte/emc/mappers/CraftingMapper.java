package moze_intel.projecte.emc.mappers;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import moze_intel.projecte.gameObjs.customRecipes.RecipeAlchemyBag;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.gameObjs.customRecipes.RecipeShapedKleinStar;
import moze_intel.projecte.gameObjs.customRecipes.RecipeShapelessHidden;
import moze_intel.projecte.utils.PELogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftingMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

	List<IRecipeMapper> recipeMappers = Arrays.asList(new VanillaRecipeMapper(), new VanillaOreRecipeMapper(), new PECustomRecipeMapper());
	Set<Class> canNotMap = Sets.newHashSet();
	Map<Class, Integer> recipeCount = Maps.newHashMap();

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, final Configuration config) {
		recipeCount.clear();
		canNotMap.clear();
		recipeloop:
        for (IRecipe recipe : CraftingManager.getInstance().getRecipeList()) {
			boolean handled = false;
			ItemStack recipeOutput = recipe.getRecipeOutput();
			if (recipeOutput == null) continue;
			NormalizedSimpleStack recipeOutputNorm = NormalizedSimpleStack.getFor(recipeOutput);
			for (IRecipeMapper recipeMapper : recipeMappers) {
				if (!config.getBoolean("enable" + recipeMapper.getName(), "IRecipeImplementations", true, recipeMapper.getDescription()))
					continue;
                if (!recipeMapper.canHandle(recipe))
                    continue;
                handled = true;
                CraftingIngredients ingredients = recipeMapper.getIngredientsFor(recipe);
                if (ingredients == null) {
                    PELogger.logWarn("RecipeMapper " + recipeMapper + " failed to map Recipe" + recipe);
                    break;
                }
                IngredientMap<NormalizedSimpleStack> ingredientMap = new IngredientMap<>();
                for (ItemStack stack : ingredients.fixedIngredients) {
                    if (stack == null || stack.getItem() == null) continue;
                    if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                        //Don't check for doesContainerItemLeaveCraftingGrid for WILDCARD-ItemStacks
                        ingredientMap.addIngredient(NormalizedSimpleStack.getFor(stack), 1);
                        continue;
                    }
                    //stack does not have a wildcard damage value
                    try {
                        if (stack.getItem().hasContainerItem(stack)) {
                            if (stack.getItem().getContainerItem(stack) == stack && !config.getBoolean("emcDependencyForUnconsumedItems", "", false, "If this option is enabled items that are made by crafting, with unconsumed ingredients, should only get an emc value, if the unconsumed item also has a value. (Examples: Extra Utilities Sigil, Cutting Board, Mixer, Juicer...)")){
                                continue;
                            }
                            ingredientMap.addIngredient(NormalizedSimpleStack.getFor(stack.getItem().getContainerItem(stack)), -1);
                        }
                        ingredientMap.addIngredient(NormalizedSimpleStack.getFor(stack), 1);
                    } catch (Exception e) {
                        PELogger.logFatal("Exception in CraftingMapper when parsing Recipe Ingredients: RecipeType: %s, Ingredient: %s", recipe.getClass().getName(), stack.toString());
                        e.printStackTrace();
                        continue recipeloop;
                    }
                }
                for (Iterable<ItemStack> multiIngredient : ingredients.multiIngredients) {
                    NormalizedSimpleStack nss = NormalizedSimpleStack.createFake(multiIngredient.toString());
                    ingredientMap.addIngredient(nss, 1);
                    for (ItemStack stack : multiIngredient) {
                        if (stack == null || stack.getItem() == null) continue;
                        IngredientMap<NormalizedSimpleStack> groupIngredientMap = new IngredientMap<>();
                        if (stack.getItem().hasContainerItem(stack)) {
                            if (stack.getItem().getContainerItem(stack) == stack && !config.getBoolean("emcDependencyForUnconsumedItems", "", false, "If this option is enabled items that are made by crafting, with unconsumed ingredients, should only get an emc value, if the unconsumed item also has a value. (Examples: Extra Utilities Sigil, Cutting Board, Mixer, Juicer...)")){
                                continue;
                            }
                            groupIngredientMap.addIngredient(NormalizedSimpleStack.getFor(stack.getItem().getContainerItem(stack)), -1);
                        }
                        groupIngredientMap.addIngredient(NormalizedSimpleStack.getFor(stack), 1);
                        mapper.addConversion(1, nss, groupIngredientMap.getMap());
                    }
                }
                if (recipeOutput.stackSize > 0) {
                    mapper.addConversion(recipeOutput.stackSize, recipeOutputNorm, ingredientMap.getMap());
                }
                else {
                    PELogger.logWarn("Ignoring Recipe because outnumber <= 0: " + ingredientMap.getMap().toString() + " -> " + recipeOutput);
                }
            }
			if (!handled) {
				if (!canNotMap.contains(recipe.getClass())) {
					canNotMap.add(recipe.getClass());
					PELogger.logWarn("Can not map Crafting Recipes with Type: " + recipe.getClass().getName());
				}
			}
            else {
				int count = 0;
				if (recipeCount.containsKey(recipe.getClass())) {
					count = recipeCount.get(recipe.getClass());
				}
				count += 1;
				recipeCount.put(recipe.getClass(), count);
			}
		}

		PELogger.logInfo("CraftingMapper Statistics:");
		for (Map.Entry<Class, Integer> entry: recipeCount.entrySet()) {
			PELogger.logInfo(String.format("Found %d Recipes of Type %s", entry.getValue(), entry.getKey()));
		}
	}

	@Override
	public String getName() {
		return "CraftingMapper";
	}

	@Override
	public String getDescription() {
		return "Add Conversions for Crafting Recipes gathered from net.minecraft.item.crafting.CraftingManager";
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	public interface IRecipeMapper {
		String getName();
		String getDescription();
		boolean canHandle(IRecipe recipe);

		CraftingIngredients getIngredientsFor(IRecipe recipe);
	}

	public static class CraftingIngredients {
		public Iterable<ItemStack> fixedIngredients;
		public Iterable<Iterable<ItemStack>> multiIngredients;
		public CraftingIngredients(Iterable<ItemStack> fixedIngredients, Iterable<Iterable<ItemStack>> multiIngredients) {
			this.fixedIngredients = fixedIngredients;
			this.multiIngredients = multiIngredients;
		}
	}

	protected static class VanillaRecipeMapper implements IRecipeMapper {

		@Override
		public String getName() {
			return "VanillaRecipeMapper";
		}

		@Override
		public String getDescription() {
			return "Maps `IRecipe` crafting recipes that extend `ShapedRecipes` or `ShapelessRecipes`";
		}

		@Override
		public boolean canHandle(IRecipe recipe) {
			return recipe instanceof ShapedRecipes || recipe instanceof ShapelessRecipes;
		}

		@Override
		public CraftingIngredients getIngredientsFor(IRecipe recipe) {
			Iterable recipeItems = null;
			if (recipe instanceof ShapedRecipes sr) {
				recipeItems = Arrays.asList(sr.recipeItems);
			} else if (recipe instanceof ShapelessRecipes sr) {
				recipeItems = sr.recipeItems;
			}
            if (recipeItems == null) return null;
			List<ItemStack> inputs = new LinkedList<>();
			for (Object o : recipeItems) {
				if (o == null) continue;
				if (o instanceof ItemStack recipeItem) {
                    inputs.add(recipeItem.copy());
				}
                else {
					PELogger.logWarn("Illegal Ingredient in Crafting Recipe: " + o);
				}
			}
			return new CraftingIngredients(inputs, new LinkedList<>());
		}

	}

	protected static class VanillaOreRecipeMapper implements IRecipeMapper {

		@Override
		public String getName() {
			return "VanillaOreRecipeMapper";
		}

		@Override
		public String getDescription() {
			return "Maps `IRecipe` crafting recipes that extend `ShapedOreRecipe` or `ShapelessOreRecipe`. This includes CraftingRecipes that use OreDictionary ingredients.";
		}

		@Override
		public boolean canHandle(IRecipe recipe) {
			return recipe instanceof ShapedOreRecipe || recipe instanceof ShapelessOreRecipe;
		}

		@Override
		public CraftingIngredients getIngredientsFor(IRecipe recipe) {
            Iterable<Object> recipeItems = null;
			if (recipe instanceof ShapedOreRecipe sor) {
				recipeItems = Arrays.asList(sor.getInput());
			}
            else if (recipe instanceof ShapelessOreRecipe sor) {
				recipeItems = sor.getInput();
			}
			if (recipeItems == null) return null;
			ArrayList<Iterable<ItemStack>> variableInputs = Lists.newArrayList();
			ArrayList<ItemStack> fixedInputs = Lists.newArrayList();
			for (Object recipeItem : recipeItems) {
				if (recipeItem instanceof ItemStack is) {
					fixedInputs.add(is);
				}
                else if (recipeItem instanceof Collection recipeItemCollection) {
                    if (recipeItemCollection.size() == 1) {
						Object element = recipeItemCollection.iterator().next();
						if (element instanceof ItemStack is) {
							fixedInputs.add(is.copy());
						}
                        else {
							PELogger.logWarn("Can not map recipe " + recipe + " because found " + element.toString() + " instead of ItemStack");
							return null;
						}
						continue;
					}

                    List<ItemStack> recipeItemOptions = new LinkedList<>();
					for (Object option : recipeItemCollection) {
						if (option instanceof ItemStack is) {
							recipeItemOptions.add(is.copy());
						}
                        else {
							PELogger.logWarn("Can not map recipe " + recipe + " because found " + option.toString() + " instead of ItemStack");
							return null;
						}
					}
					variableInputs.add(recipeItemOptions);
				}
			}
			return new CraftingIngredients(fixedInputs, variableInputs);
		}
	}

	protected static class PECustomRecipeMapper implements IRecipeMapper {

		@Override
		public String getName() {
			return "PECustomRecipeMapper";
		}

		@Override
		public String getDescription() {
			return "Maps custom IRecipe's from ProjectE";
		}

		@Override
		public boolean canHandle(IRecipe recipe) {
			return recipe instanceof RecipeShapedKleinStar || recipe instanceof RecipeShapelessHidden
                || recipe instanceof RecipeAlchemyBag;
		}

		@Override
		public CraftingIngredients getIngredientsFor(IRecipe recipe) {
			Iterable recipeItems = null;
			if (recipe instanceof RecipeShapedKleinStar rsk) {
				recipeItems = Arrays.asList(rsk.recipeItems);
			} else if (recipe instanceof RecipeShapelessHidden rsh) {
				recipeItems = rsh.getInput();
			} else if (recipe instanceof RecipeAlchemyBag bag) {
                recipeItems = Arrays.asList(bag.getRecipeInputBag(), bag.getRecipeInputDye());
            }
            if (recipeItems == null) return null;
			List<ItemStack> inputs = new LinkedList<>();
			for (Object obj : recipeItems) {
				if (obj == null) continue;
				if (obj instanceof ItemStack recipeItem) {
                    inputs.add(recipeItem);
				}
                else {
					PELogger.logWarn("Illegal Ingredient in Crafting Recipe: " + obj);
				}
			}
			return new CraftingIngredients(inputs, new LinkedList<>());
		}
	}
}
