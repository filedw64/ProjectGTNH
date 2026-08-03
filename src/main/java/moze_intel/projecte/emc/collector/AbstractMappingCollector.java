package moze_intel.projecte.emc.collector;

import moze_intel.projecte.emc.arithmetics.IValueArithmetic;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMappingCollector<T, V extends Comparable<V>> implements IMappingCollector<T, V>
{
	protected IValueArithmetic<V> arithmetic;
	public AbstractMappingCollector(IValueArithmetic<V> arithmetic) {
		this.arithmetic = arithmetic;
	}

	@Override
	public void addConversion(int outnumber, T output, Iterable<T> ingredients) {
		addConversion(outnumber, output, listToMapOfCounts(ingredients));
	}

	protected Map<T, Integer> listToMapOfCounts(Iterable<T> iterable) {
		Map<T, Integer> map = new HashMap<>();
		for (T ingredient : iterable) {
			if (map.containsKey(ingredient)) {
                map.put(ingredient, map.get(ingredient) + 1);
			} else {
				map.put(ingredient, 1);
			}
		}
		return map;
	}

	@Override
	public void setValueFromConversion(int outnumber, T something, Iterable<T> ingredients) {
		this.setValueFromConversion(outnumber, something, listToMapOfCounts(ingredients));
	}

	public abstract void setValueFromConversion(int outnumber, T something, Map<T, Integer> ingredientsWithAmount);

	public abstract void addConversion(int outnumber, T output, Map<T, Integer> ingredientsWithAmount);

	@Override
	public IValueArithmetic<V> getArithmetic()
	{
		return this.arithmetic;
	}

	@Override
	public void finishCollection() {}
}