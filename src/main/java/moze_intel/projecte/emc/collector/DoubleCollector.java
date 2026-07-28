package moze_intel.projecte.emc.collector;

import moze_intel.projecte.emc.arithmetics.IValueArithmetic;

import java.util.Map;

public class DoubleCollector<T, A extends IValueArithmetic<Double>> extends AbstractMappingCollector<T, Double, A>
{
	IExtendedMappingCollector<T, Double, A> inner;
	public DoubleCollector(IExtendedMappingCollector<T, Double, A> inner) {
		super(inner.getArithmetic());
		this.inner = inner;
	}
	@Override
	public void setValueFromConversion(int outnumber, T something, Map<T, Integer> ingredientsWithAmount)
	{
		inner.setValueFromConversion(outnumber, something, ingredientsWithAmount);
	}

	@Override
	public void addConversion(int outnumber, T output, Map<T, Integer> ingredientsWithAmount, A arithmeticForConversion)
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