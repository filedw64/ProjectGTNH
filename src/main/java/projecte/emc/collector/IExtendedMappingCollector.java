package projecte.emc.collector;

import projecte.emc.arithmetics.IValueArithmetic;

import java.util.Map;

public interface IExtendedMappingCollector<T, V extends Comparable<V>, A extends IValueArithmetic> extends IMappingCollector<T, V>
{
	public void addConversion(int outnumber, T output, Map<T, Integer> ingredientsWithAmount, A arithmeticForConversion);

	public void addConversion(int outnumber, T output, Iterable<T> ingredients, A arithmeticForConversion);

	public A getArithmetic();
}
