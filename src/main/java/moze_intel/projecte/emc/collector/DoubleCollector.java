package moze_intel.projecte.emc.collector;

import java.util.Map;

public class DoubleCollector<T> extends AbstractMappingCollector<T, Double> {
	IMappingCollector<T, Double> inner;
	public DoubleCollector(IMappingCollector<T, Double> inner) {
		super(inner.getArithmetic());
		this.inner = inner;
	}

	@Override
	public void setValueFromConversion(int outnumber, T something, Map<T, Integer> ingredientCounts) {
		inner.setValueFromConversion(outnumber, something, ingredientCounts);
	}

	@Override
	public void addConversion(int outnumber, T output, Map<T, Integer> ingredientCounts) {
		inner.addConversion(outnumber, output, ingredientCounts);
	}

	@Override
	public void setValueBefore(T something, Double value) {
		inner.setValueBefore(something, value);
	}

	@Override
	public void setValueAfter(T something, Double value) {
		inner.setValueAfter(something, value);
	}
}