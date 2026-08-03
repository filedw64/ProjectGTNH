package moze_intel.projecte.emc.collector;

import com.google.common.collect.Maps;
import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.utils.PELogger;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class MappingCollector<T, V extends Comparable<V>> extends AbstractMappingCollector<T,V> {

	public MappingCollector(IValueArithmetic<V> arithmetic) {
		super(arithmetic);
	}

	protected Map<T, Conversion> overwriteConversion = Maps.newHashMap();
	protected Map<T, List<Conversion>> conversionsFor = Maps.newHashMap();
	protected Map<T, List<Conversion>> usedIn = Maps.newHashMap();
	protected Map<T, V> fixValueBeforeInherit = Maps.newHashMap();
	protected Map<T, V> fixValueAfterInherit = Maps.newHashMap();

	public static <T, V> List<V> getOrCreateList(Map<T, List<V>> map, T key) {
		List<V> list;
		if (map.containsKey(key)) {
			list = map.get(key);
		}
        else {
			list = new LinkedList<>();
			map.put(key, list);
		}
		return list;
	}

	protected List<Conversion> getConversionsFor(T something) {
		return getOrCreateList(conversionsFor, something);
	}

	protected List<Conversion> getUsesFor(T something) {
		return getOrCreateList(usedIn, something);
	}

	protected void addConversionToIngredientUsages(Conversion conversion) {
		for (Map.Entry<T, Integer> ingredient : conversion.ingredientsWithAmount.entrySet()) {
            if (ingredient.getValue() == null)
                throw new IllegalArgumentException("ingredient amount value has to be != null");
			List<Conversion> usesFor = getUsesFor(ingredient.getKey());
			if (!usesFor.contains(conversion))
				usesFor.add(conversion);
		}
	}

	public void addConversion(int outnum, T output, Map<T, Integer> ingredientCounts) {
		if (ingredientCounts.containsKey(null) || output == null || outnum <= 0) {
			PELogger.logWarn("Ignoring Recipe because of invalid input / output: %s -> %dx%s", ingredientCounts, outnum, output);
			return;
		}
		//Add the Conversions to the conversionsFor and usedIn Maps:
		Conversion conversion = new Conversion(output, outnum, ingredientCounts);
		conversion.value = arithmetic.getZero();
        List<Conversion> conversionsForOut = getConversionsFor(output);
		if (conversionsForOut.contains(conversion))
            return;
        conversionsForOut.add(conversion);
		addConversionToIngredientUsages(conversion);
	}

	@Override
	public void setValueBefore(T something, V value) {
		if (something == null) return;
		if (fixValueBeforeInherit.containsKey(something) && fixValueBeforeInherit.get(something).compareTo(value) != 0)
			PELogger.logWarn("Overwriting fixValueBeforeInherit for " + something + ":" + fixValueBeforeInherit.get(something) + " to " + value);
        fixValueBeforeInherit.put(something, value);
		fixValueAfterInherit.remove(something);
	}

	@Override
	public void setValueAfter(T something, V value) {
		if (something == null) return;
		if (fixValueAfterInherit.containsKey(something) && fixValueAfterInherit.get(something) != value)
			PELogger.logWarn("Overwriting fixValueAfterInherit for " + something + ":" + fixValueAfterInherit.get(something) + " to " + value);
		fixValueAfterInherit.put(something, value);
	}

	@Override
	public void setValueFromConversion(int outnumber, T something, Map<T, Integer> ingredientsWithAmount)
	{
		if (something == null || ingredientsWithAmount.containsKey(null)) {
			PELogger.logWarn(String.format("Ignoring setValueFromConversion because of invalid ingredient or output: %s -> %dx%s", ingredientsWithAmount, outnumber, something));
			return;
		}
		if (outnumber <= 0)
			throw new IllegalArgumentException("outnumber has to be > 0!");
		Conversion conversion = new Conversion(something, outnumber, ingredientsWithAmount);
		if (overwriteConversion.containsKey(something)) {
			Conversion oldConversion = overwriteConversion.get(something);
			PELogger.logWarn("Overwriting setValueFromConversion " + overwriteConversion.get(something) + " with " + conversion);
			for (T ingredient: ingredientsWithAmount.keySet()) {
				getUsesFor(ingredient).remove(oldConversion);
			}
		}
		addConversionToIngredientUsages(conversion);
		overwriteConversion.put(something, conversion);
	}

    protected class Conversion {
		public T output;

		public int outnumber = 1;
		public V value = arithmetic.getZero();
		public Map<T, Integer> ingredientsWithAmount;

		protected Conversion(T output) {
			this.output = output;
		}

		protected Conversion(T output, int outnumber, Map<T, Integer> ingredientsWithAmount) {
			this(output);
			this.outnumber = outnumber;
			this.ingredientsWithAmount = ingredientsWithAmount;
		}

        public String toString() {
			if (value != arithmetic.getZero())
				return value + " + " + ingredientsToString() + " => " + outnumber + "*" + output;
			return ingredientsToString() + " => " + outnumber + "*" + output;
		}

		public String ingredientsToString() {
			if (ingredientsWithAmount == null || ingredientsWithAmount.isEmpty()) return "nothing";
			StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<T,Integer>> iter = ingredientsWithAmount.entrySet().iterator();
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
				if (ingredientsWithAmount == null || ingredientsWithAmount.isEmpty()) {
					return other.ingredientsWithAmount == null || other.ingredientsWithAmount.isEmpty();
				}
				else {
					return ingredientsWithAmount.equals(other.ingredientsWithAmount);
				}
			}
			return false;
		}
	}
}