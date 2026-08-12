package moze_intel.projecte.emc.generators;

import java.util.HashMap;
import java.util.Map;

public class DoubleGenerator<T> implements IValueGenerator<T, Double>
{
	private final IValueGenerator<T, Double> inner;

	public DoubleGenerator(IValueGenerator<T, Double> inner) {
		this.inner = inner;
	}

	@Override
	public Map<T, Double> generateValues()
	{
		Map<T, Double> innerResult = inner.generateValues();
		Map<T, Double> myResult = new HashMap<>();
		innerResult.forEach((key, value) -> {
			if (value > 0)
				myResult.put(key, value);
		});
		return myResult;
	}
}