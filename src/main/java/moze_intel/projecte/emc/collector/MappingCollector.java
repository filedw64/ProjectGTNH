package moze_intel.projecte.emc.collector;

import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.utils.PELogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
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

	@Override
	public void setValueBefore(T something, V value) {
		if (something == null) return;
		// 直接利用 put 返回的旧值，减少 containsKey 和 get 造成的多次哈希寻址
		V old = valueBefore.put(something, value);
		if (old != null && old.compareTo(value) != 0) {
			PELogger.logWarn("Overwriting setValueBefore for %s: %s to %s", something, old, value);
		}
		valueAfter.remove(something);
	}

	@Override
	public void setValueAfter(T something, V value) {
		if (something == null) return;
		V old = valueAfter.put(something, value);
		if (old != null && old.compareTo(value) != 0) {
			PELogger.logWarn("Overwriting setValueAfter for %s: %s to %s", something, old, value);
		}
	}

	public static <T, V> Set<V> getOrCreateSet(Map<T, Set<V>> map, T key) {
		// computeIfAbsent 一步到位
		return map.computeIfAbsent(key, k -> new HashSet<>());
	}

	protected Set<Conversion> getConversionsFor(T something) {
		return getOrCreateSet(conversionsFor, something);
	}

	protected Set<Conversion> getUsesFor(T something) {
		return getOrCreateSet(usedIn, something);
	}

	protected void addIngredientUsage(Conversion conv) {
		conv.ingredientCounts.keySet().forEach(input -> getUsesFor(input).add(conv));
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
		// 这里的 contains 依赖于正确的 equals 和 hashCode
		if (convForOut.contains(conv))
			return;
		convForOut.add(conv);
		addIngredientUsage(conv);
	}

	@Override
	public void setValueFromConversion(int outnum, T output, Map<T, Integer> ingredientCounts) {
		if (ingredientCounts.containsKey(null) || output == null || ingredientCounts.containsValue(null) || outnum <= 0) {
			PELogger.logWarn("Ignoring Recipe because of invalid input / output: %s -> %sx%s", ingredientCounts, outnum, output);
			return;
		}
		Conversion conv = new Conversion(output, outnum, ingredientCounts);
		// 返回旧值
		Conversion oldConv = overwriteConversion.put(output, conv);
		if (oldConv != null) {
			PELogger.logWarn("Overwriting setValueFromConversion %s with %s", oldConv, conv);
			for (T ingredient: oldConv.ingredientCounts.keySet()) {
				getUsesFor(ingredient).remove(oldConv);
			}
		}
		addIngredientUsage(conv);
	}

	protected class Conversion {
		public final T output;
		public final int outputCount;
		public final Map<T, Integer> ingredientCounts;
		public final V value;

		protected Conversion(T output, int outputCount, Map<T, Integer> ingredientCounts) {
			this.output = output;
			this.outputCount = outputCount;
			this.ingredientCounts = ingredientCounts;
			this.value = arithmetic.getZero();
		}

		protected Conversion(T output, int outputCount, Map<T, Integer> ingredientCounts, V value) {
			this.output = output;
			this.outputCount = outputCount;
			this.ingredientCounts = ingredientCounts;
			this.value = value;
		}

		@Override
		public String toString() {
			if (value.compareTo(arithmetic.getZero()) != 0)
				return value + " + " + ingredientsToString() + " => " + outputCount + "*" + output;
			return ingredientsToString() + " => " + outputCount + "*" + output;
		}

		public String ingredientsToString() {
			if (ingredientCounts == null || ingredientCounts.isEmpty()) return "nothing";
			StringBuilder builder = new StringBuilder();
			ingredientCounts.forEach((input, count) -> {
				if (builder.length() == 0)
					builder.append(count).append("*").append(input);
				else builder.append(" + ").append(count).append("*").append(input);
			});
			return builder.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
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

		// 补充缺失的 hashCode() 方法，确保 HashSet 的查重机制正常工作
		@Override
		public int hashCode() {
			int result = output != null ? output.hashCode() : 0;
			result = 31 * result + (value != null ? value.hashCode() : 0);
			if (ingredientCounts != null && !ingredientCounts.isEmpty()) {
				result = 31 * result + ingredientCounts.hashCode();
			}
			return result;
		}
	}
}
