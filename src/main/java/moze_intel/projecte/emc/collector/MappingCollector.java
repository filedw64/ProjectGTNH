package moze_intel.projecte.emc.collector;

import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.utils.PELogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class MappingCollector<T, V extends Comparable<V>> extends AbstractMappingCollector<T,V> {

	public MappingCollector(IValueArithmetic<V> arithmetic) {
		super(arithmetic);
	}

	protected Map<T, Conversion> overwriteConversion = new HashMap<>();
	protected Map<T, Set<Conversion>> conversionsFor = new HashMap<>();
	protected Map<T, Set<Conversion>> usedIn = new HashMap<>();
	protected Map<T, V> valueBefore = new HashMap<>();
	protected Map<T, V> valueAfter = new HashMap<>();

	public static <T, V> Set<V> getOrCreateSet(Map<T, Set<V>> map, T key) {
		Set<V> set;
		if (map.containsKey(key)) {
			set = map.get(key);
		}
        else {
			set = new HashSet<>();
			map.put(key, set);
		}
		return set;
	}

	protected Set<Conversion> getConversionsFor(T something) {
		return getOrCreateSet(conversionsFor, something);
	}

	protected Set<Conversion> getUsesFor(T something) {
		return getOrCreateSet(usedIn, something);
	}

	protected void addIngredientUsage(Conversion conversion) {
		for (Map.Entry<T, Integer> ingredient : conversion.ingredientCounts.entrySet()) {
            if (ingredient.getValue() == null)
                throw new IllegalArgumentException("ingredient amount value has to be != null");
			getUsesFor(ingredient.getKey()).add(conversion);
		}
	}

	public void addConversion(int outnum, T output, Map<T, Integer> ingredientCounts) {
		if (overwriteConversion.containsKey(output))
			return;
		if (ingredientCounts.containsKey(null) || output == null || ingredientCounts.containsValue(null) || outnum <= 0) {
			PELogger.logWarn("Ignoring Recipe because of invalid input / output: %s -> %sx%s", ingredientCounts, outnum, output);
			return;
		}
		//Add the Conversions to the conversionsFor and usedIn Maps:
		Conversion conv = new Conversion(output, outnum, ingredientCounts);
		Set<Conversion> convForOut = getConversionsFor(output);
		if (convForOut.contains(conv))
            return;
        convForOut.add(conv);
		addIngredientUsage(conv);
	}

	@Override
	public void setValueBefore(T something, V value) {
		if (something == null) return;
		if (valueBefore.containsKey(something) && valueBefore.get(something).compareTo(value) != 0)
			PELogger.logWarn("Overwriting setValueBefore for %s: %s to %s", something, valueBefore.get(something), value);
        valueBefore.put(something, value);
		valueAfter.remove(something);
	}

	@Override
	public void setValueAfter(T something, V value) {
		if (something == null) return;
		if (valueAfter.containsKey(something) && valueAfter.get(something).compareTo(value) != 0)
			PELogger.logWarn("Overwriting setValueAfter for %s: %s to %s", something, valueAfter.get(something), value);
		valueAfter.put(something, value);  
	}

	@Override
	public void setValueFromConversion(int outnum, T output, Map<T, Integer> ingredientCounts) {
		if (ingredientCounts.containsKey(null) || output == null || ingredientCounts.containsValue(null) || outnum <= 0) {
			PELogger.logWarn("Ignoring Recipe because of invalid input / output: %s -> %sx%s", ingredientCounts, outnum, output);
			return;
		}
		Conversion conv = new Conversion(output, outnum, ingredientCounts);
		if (overwriteConversion.containsKey(output)) {
			Conversion oldConv = overwriteConversion.get(output);
			PELogger.logWarn("Overwriting setValueFromConversion %s with %s", oldConv, conv);
			for (T ingredient: oldConv.ingredientCounts.keySet()) {
				getUsesFor(ingredient).remove(oldConv);
			}
		}
		overwriteConversion.put(output, conv);
		addIngredientUsage(conv);
	}

    protected class Conversion {
		public T output;

		public int outnumber;
		public V value = arithmetic.getZero();
		public Map<T, Integer> ingredientCounts;

		protected Conversion(T output, int outnumber, Map<T, Integer> ingredientCounts) {
			this.output = output;
			this.outnumber = outnumber;
			this.ingredientCounts = ingredientCounts;
		}

		@Override
        public String toString() {
			if (value != arithmetic.getZero())
				return value + " + " + ingredientsToString() + " => " + outnumber + "*" + output;
			return ingredientsToString() + " => " + outnumber + "*" + output;
		}

		public String ingredientsToString() {
			if (ingredientCounts == null || ingredientCounts.isEmpty()) return "nothing";
			StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<T,Integer>> iter = ingredientCounts.entrySet().iterator();
			if (iter.hasNext()) {
				Map.Entry<T, Integer> entry = iter.next();
				sb.append(entry.getValue()).append("*").append(entry.getKey().toString());
				while (iter.hasNext()) {
					entry = iter.next();
					sb.append(" + ").append(entry.getValue()).append("*").append(entry.getKey().toString());
				}
			}

			return sb.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof MappingCollector<?,?>.Conversion other))
				return false;
			if (output.equals(other.output) && value.equals(other.value)) {
				if (ingredientCounts == null || ingredientCounts.isEmpty()) {
					return other.ingredientCounts == null || other.ingredientCounts.isEmpty();
				}
				else {
					return ingredientCounts.equals(other.ingredientCounts);
				}
			}
			return false;
		}
	}
}