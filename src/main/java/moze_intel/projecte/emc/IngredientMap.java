package moze_intel.projecte.emc;

import java.util.HashMap;
import java.util.Map;

public class IngredientMap<T> {
	private final Map<T, Integer> ingredientCounts = new HashMap<>();

	public void addIngredient(T input, int amount) {
		ingredientCounts.put(input, amount + ingredientCounts.getOrDefault(input, 0));
	}

	public Map<T, Integer> getMap() {
		return new HashMap<>(ingredientCounts);
	}

    @Override
	public String toString() {
		return ingredientCounts.toString();
	}
}
