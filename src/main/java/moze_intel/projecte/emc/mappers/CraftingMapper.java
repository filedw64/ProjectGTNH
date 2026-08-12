package moze_intel.projecte.emc.mappers;

import com.google.common.collect.Lists;
import moze_intel.projecte.emc.IngredientMap;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.gameObjs.customRecipes.RecipeAlchemyBag;
import moze_intel.projecte.gameObjs.customRecipes.RecipeShapedKleinStar;
import moze_intel.projecte.gameObjs.customRecipes.RecipeShapelessHidden;
import moze_intel.projecte.integration.GregTech.GTItemHelper;
import moze_intel.projecte.utils.EnchantmentBlacklist;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftingMapper implements IEMCMapper<NormalizedSimpleStack, Double> {

	public static List<IRecipeMapper> recipeMappers = Arrays.asList(new VanillaRecipeMapper(), new VanillaOreRecipeMapper(), new PECustomRecipeMapper());
	public static boolean emcDependencyForUnconsumedItems = false;
    Set<Class<?>> canNotMap = new HashSet<>();
	Map<Class<?>, Integer> recipeCount = new HashMap<>();

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, final Configuration config) {
        emcDependencyForUnconsumedItems = config.getBoolean("emcDependencyForUnconsumedItems", "", false,
			"Items crafted with unconsumed ingredients get an emc value, only when unconsumed items also have a value. (Examples: Extra Utilities Sigil, Cutting Board, Mixer, Juicer...)");
        for (IRecipeMapper recipeMapper : recipeMappers) {
            recipeMapper.setEnabled(config.getBoolean("enable".concat(recipeMapper.getName()) , "IRecipeImplementations",
				true, recipeMapper.getDescription()));
        }

        for (IRecipe recipe : CraftingManager.getInstance().getRecipeList()) {
			Class<?> clazz = recipe.getClass();
			if (canNotMap.contains(clazz)) continue;

			ItemStack recipeOutput = recipe.getRecipeOutput();
			if (recipeOutput == null) continue;
            if (recipeOutput.isItemEnchanted()) {
                EnchantmentBlacklist.add(recipeOutput);
            }
			NormalizedSimpleStack outNSS = NormalizedSimpleStack.forItem(recipeOutput);
			boolean handled = false;
			for (IRecipeMapper recipeMapper : recipeMappers) {
				if (!recipeMapper.isEnabled() || !recipeMapper.canHandle(recipe)) continue;
                handled = true;
                CraftingIngredients ingredients = recipeMapper.getIngredientsFor(recipe);
                if (ingredients == null) {
                    PELogger.logError("RecipeMapper %s failed to map Recipe %s", recipeMapper, recipe);
                    break;
                }

                IngredientMap<NormalizedSimpleStack> ingredientMap = new IngredientMap<>();

                for (ItemStack stack : ingredients.fixedIngredients) {
					Item item;
					if (stack == null || (item = stack.getItem()) == null) continue;

					NormalizedSimpleStack.NSSItem nss = NormalizedSimpleStack.forItem(stack);
                    if (nss.damage == OreDictionary.WILDCARD_VALUE) {
                        //Don't check ContainerItem for WILDCARD-ItemStacks
                        ingredientMap.addIngredient(nss, 1);
                        continue;
                    }
                    //stack does not have a wildcard damage value
					if (GTItemHelper.isNullGTtool(stack)) {
						mapper.setValueBefore(nss, -Double.MAX_VALUE);
						ingredientMap.addIngredient(nss, 0);
						continue;
					}

					ItemStack container = item.getContainerItem(stack);
					if (container != null && container.getItem() != null)
						ingredientMap.addIngredient(NormalizedSimpleStack.forItem(container), -1);

					ingredientMap.addIngredient(nss, 1);
                }

                for (Iterable<ItemStack> multiIngredient : ingredients.multiIngredients) {
                    NormalizedSimpleStack fake = NormalizedSimpleStack.forFake(multiIngredient.toString());
                    ingredientMap.addIngredient(fake, 1);
                    for (ItemStack is : multiIngredient) {
						Item item;
                        if (is == null || (item = is.getItem()) == null) continue;
						NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(is);
                        IngredientMap<NormalizedSimpleStack> fakeIngredients = new IngredientMap<>();

                        if (GTItemHelper.isNullGTtool(is)) {
                            mapper.setValueBefore(nss, -Double.MAX_VALUE);
                            fakeIngredients.addIngredient(nss, 0);
                            mapper.addConversion(1, fake, fakeIngredients.getMap());
                            continue;
                        }

                        ItemStack container = item.getContainerItem(is);
                        if (container != null && container.getItem() != null)
                            fakeIngredients.addIngredient(NormalizedSimpleStack.forItem(container), -1);

                        fakeIngredients.addIngredient(nss, 1);
                        mapper.addConversion(1, fake, fakeIngredients.getMap());
                    }
                }

                mapper.addConversion(recipeOutput.stackSize, outNSS, ingredientMap.getMap());
                break;
            }
			if (!handled) {
				canNotMap.add(clazz);
				PELogger.logWarn("Can not map Crafting Recipes with Type: %s", clazz.getName());
			}
            else
				recipeCount.put(clazz, recipeCount.getOrDefault(clazz, 0) + 1);
		}

		PELogger.logInfo("CraftingMapper Statistics:");
		recipeCount.forEach((clazz, count) -> PELogger.logInfo("Found %d Recipes of %s", count, clazz.getName()));
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
        boolean isEnabled();
        void setEnabled(boolean flag);
		boolean canHandle(IRecipe recipe);

		CraftingIngredients getIngredientsFor(IRecipe recipe);
	}

    public abstract static class AbstractRecipeMapper implements IRecipeMapper {
        private boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean flag) {
            enabled = flag;
        }
    }

	public static class CraftingIngredients {
		public Iterable<ItemStack> fixedIngredients;
		public Iterable<Iterable<ItemStack>> multiIngredients;
		public CraftingIngredients(Iterable<ItemStack> fixedIngredients, Iterable<Iterable<ItemStack>> multiIngredients) {
			this.fixedIngredients = fixedIngredients;
			this.multiIngredients = multiIngredients;
		}
	}

	protected static class VanillaRecipeMapper extends AbstractRecipeMapper {

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
			Iterable<ItemStack> recipeItems = null;
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

	protected static class VanillaOreRecipeMapper extends AbstractRecipeMapper {

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
                else if (recipeItem instanceof Collection<?> recipeItemCollection) {
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

	protected static class PECustomRecipeMapper extends AbstractRecipeMapper {

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
			Iterable<?> recipeItems = null;
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
