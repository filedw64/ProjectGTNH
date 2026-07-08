package projecte.emc.collector;

import projecte.emc.arithmetics.IValueArithmetic;

import java.util.Map;

public class DoubleCollector<T, DoubleArithmetic extends IValueArithmetic<?>> extends AbstractMappingCollector<T, Double, DoubleArithmetic>
{
	IExtendedMappingCollector<T, Double, DoubleArithmetic> inner;
	public DoubleCollector(IExtendedMappingCollector<T, Double, DoubleArithmetic> inner) {
		super(inner.getArithmetic());
		this.inner = inner;
	}
	@Override
	public void setValueFromConversion(int outnumber, T something, Map<T, Integer> ingredientsWithAmount)
	{
		inner.setValueFromConversion(outnumber, something, ingredientsWithAmount);
	}

	@Override
	public void addConversion(int outnumber, T output, Map<T, Integer> ingredientsWithAmount, DoubleArithmetic arithmeticForConversion)
	{
		inner.addConversion(outnumber, output, ingredientsWithAmount, arithmeticForConversion);
	}

	@Override
	public void setValueBefore(T something, Double value)
	{
		inner.setValueBefore(something, value);
	}

	@Override
	public void setValueAfter(T something, Double value)
	{
		inner.setValueAfter(something, value);
	}
}
