package moze_intel.projecte.emc;

import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.emc.collector.MappingCollector;
import moze_intel.projecte.emc.generators.IValueGenerator;
import moze_intel.projecte.utils.PELogger;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class SimpleGraphMapper<T, V extends Comparable<V>> extends MappingCollector<T, V> implements IValueGenerator<T, V>
{
	private static final boolean OVERWRITE_FIXED_VALUES = false;
	protected V ZERO;

	public SimpleGraphMapper(IValueArithmetic<V> arithmetic) {
		super(arithmetic);
		ZERO = arithmetic.getZero();
	}

	protected static <K, V extends Comparable<V>> boolean hasSmallerOrEqual(Map<K, V> m, K key, V value) {
		V current = m.get(key);
		return current != null && current.compareTo(value) <= 0;
	}

	protected static<K,V extends Comparable<V>> boolean hasSmaller(Map<K,V> m, K key, V value) {
		V current = m.get(key);
		return current != null && current.compareTo(value) < 0;
	}

	protected static<K, V extends Comparable<V>> boolean updateMapWithMinimum(Map<K,V> m, K key, V value) {
		if (!hasSmaller(m, key, value)) {
			//No Value or a value that is smaller than this
			m.put(key, value);
			return true;
		}
		return false;
	}

	protected boolean canOverride(T something, V value) {
		if (OVERWRITE_FIXED_VALUES) return true;
		V beforeVal = valueBefore.get(something);
		if (beforeVal != null) {
			return beforeVal.compareTo(value) == 0;
		}
		return true;
	}

	//@Override
	public Map<T, V> generateValues_old() {
		Map<T, V> values = new HashMap<>();
		Map<T, V> nextValueFor = new HashMap<>();
		Map<T, V> newValueFor = new HashMap<>(valueBefore);

		while (!newValueFor.isEmpty()) {
			while (!newValueFor.isEmpty()) {
				for (Map.Entry<T, V> entry : newValueFor.entrySet()) {
					if (canOverride(entry.getKey(), entry.getValue()) && updateMapWithMinimum(values, entry.getKey(), entry.getValue())) {
						for (Conversion conversion : getUsesFor(entry.getKey())) {
							if (overwriteConversion.containsKey(conversion.output) && overwriteConversion.get(conversion.output) != conversion) {
								continue;
							}
							V conversionValue = arithmetic.div(valueForConversion(values, conversion), conversion.outputCount);
							if (conversionValue.compareTo(ZERO) > 0 || arithmetic.isFree(conversionValue)) {
								if (!hasSmallerOrEqual(values, conversion.output, conversionValue)) {
									updateMapWithMinimum(nextValueFor, conversion.output, conversionValue);
								}
							}
						}
					}
				}
				newValueFor.clear();
				Map<T, V> tmp = nextValueFor;
				nextValueFor = newValueFor;
				newValueFor = tmp;
			}
			for (Map.Entry<T, Set<Conversion>> entry : conversionsFor.entrySet()) {
				V minConversionValue = null;
				for (Conversion conversion : entry.getValue()) {
					V conversionValue = valueForConversion(values, conversion);
					V conversionValueSingle = arithmetic.div(conversionValue, conversion.outputCount);
					V resultValueSingle = values.containsKey(entry.getKey()) ? values.get(entry.getKey()) : ZERO;

					if (conversionValueSingle.compareTo(ZERO) > 0 || arithmetic.isFree(conversionValueSingle)) {
						if (minConversionValue == null || minConversionValue.compareTo(conversionValueSingle) > 0) {
							minConversionValue = conversionValueSingle;
						}
					}
					if (ZERO.compareTo(conversionValue) < 0 && conversionValueSingle.compareTo(resultValueSingle) < 0) {
						if (overwriteConversion.containsKey(conversion.output) && overwriteConversion.get(conversion.output) != conversion) {
							PELogger.logWarn(String.format("EMC Exploit: \"%s\" ingredient cost: %s value of result: %s setValueFromConversion: %s", conversion, conversionValue, resultValueSingle, overwriteConversion.get(conversion.output)));
						}
						else if (canOverride(entry.getKey(), ZERO)) {
							PELogger.logWarn("Setting %s to 0 because result (%s) > cost (%s): %s", entry.getKey(), resultValueSingle, conversionValue, conversion);
							newValueFor.put(conversion.output, ZERO);
						}
						else {
							PELogger.logWarn(String.format("EMC Exploit: \"%s\" ingredient cost: %s fixed value of result: %s", conversion, conversionValue, resultValueSingle));
						}
					}
				}
				if (minConversionValue == null || minConversionValue.equals(ZERO)) {
					if (values.containsKey(entry.getKey()) && !values.get(entry.getKey()).equals(ZERO) && canOverride(entry.getKey(), ZERO) && !hasSmaller(values, entry.getKey(), ZERO)) {
						PELogger.logWarn("Removing Value for %s because it does not have any nonzero-conversions anymore.", entry.getKey());
						newValueFor.put(entry.getKey(), ZERO);
					}
				}
			}
		}
		values.putAll(valueAfter);
		// 替换 keySet() 的使用
		values.entrySet().removeIf(entry -> arithmetic.isFree(entry.getValue()));
		return values;
	}

	@Override
	public Map<T, V> generateValues() {
		Map<T, V> values = new HashMap<>(valueBefore);

		// 构建 SPFA 队列
		Queue<T> workQueue = new ArrayDeque<>(values.keySet());
		Set<T> inQueue = new HashSet<>(values.keySet());

		while (!workQueue.isEmpty()) {
			T item = workQueue.poll();
			inQueue.remove(item); // 移出排队标记

			for (Conversion conv : getUsesFor(item)) {
				// 如果在 valueBefore 中存在，代表是被锁定或配置强行覆写的 EMC 值，跳过更新
				if (valueBefore.containsKey(conv.output))
					continue;
				if (overwriteConversion.containsKey(conv.output) && overwriteConversion.get(conv.output) != conv)
					continue;

				V convVal = arithmetic.div(valueForConversion(values, conv), conv.outputCount);

				if (convVal.compareTo(ZERO) > 0 || arithmetic.isFree(convVal)) {
					// 减少底层哈希寻址开销
					V currentVal = values.get(conv.output);
					if (currentVal == null || currentVal.compareTo(convVal) > 0) {
						values.put(conv.output, convVal);

						// 拦截重复入队
						if (!inQueue.contains(conv.output)) {
							workQueue.add(conv.output);
							inQueue.add(conv.output);
						}
					}
				}
			}
		}

		values.putAll(valueAfter);
		// 避免重复寻址
		values.entrySet().removeIf(entry -> arithmetic.isFree(entry.getValue()) || arithmetic.isZero(entry.getValue()));
		return values;
	}

	/**
	 * Calculate the combined Cost for the ingredients in the Conversion.
	 * @param values The values for the ingredients to use in the calculation
	 * @param conversion The Conversion for which to calculate the combined ingredient cost.
	 * @return The combined ingredient value, ZERO or arithmetic.getFree()
	 */
	protected V valueForConversion(Map<T, V> values, Conversion conversion)
	{
		try {
			return valueForConversionUnsafe(values, conversion);
		} catch (Exception e) {
			PELogger.logWarn(String.format("Could not calculate value for %s: %s", conversion.toString(), e));
			e.printStackTrace();
			return ZERO;
		}
	}

	protected V valueForConversionUnsafe(Map<T, V> values, Conversion conversion)
	{
		V value = conversion.value;
		boolean allIngredientsAreFree = true;
		boolean hasPositiveIngredientValues = false;

		for (Map.Entry<T, Integer> entry : conversion.ingredientCounts.entrySet()) {
			if (entry.getValue() == 0)
			{
				//Ingredients with an amount of 'zero' do not need to be handled.
				continue;
			}

			// 合并 containsKey 和 get
			V ingredientCost = values.get(entry.getKey());
			if (ingredientCost != null) {
				//The ingredient has a value
				//value = value + amount * ingredientcost
				V ingredientValue = arithmetic.mul(entry.getValue(), ingredientCost);
				if (ingredientValue.compareTo(ZERO) == 0) {
					//There is an ingredient with value = 0 => we cannot calculate the combined ingredient cost.
					return ZERO;
				}
				if (!arithmetic.isFree(ingredientValue)) {
					value = arithmetic.add(value, ingredientValue);
					if (ingredientValue.compareTo(ZERO) > 0 && entry.getValue() > 0) hasPositiveIngredientValues = true;
					allIngredientsAreFree = false;
				}
			}
			else {
				//There is an ingredient that does not have a value => we cannot calculate the combined ingredient cost.
				return ZERO;
			}
		}

		//When all the ingredients are free or ingredients with negative amount made the Conversion have a value <= 0, this item should be free
		if (allIngredientsAreFree || (hasPositiveIngredientValues && value.compareTo(ZERO) <= 0)) return arithmetic.getFree();
		return value;
	}
}
