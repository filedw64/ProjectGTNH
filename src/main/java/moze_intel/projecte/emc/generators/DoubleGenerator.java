package moze_intel.projecte.emc.generators;

import com.google.common.collect.Maps;

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
		Map<T, Double> innerReslt = inner.generateValues();
		Map<T, Double> myResult = Maps.newHashMap();
		for (Map.Entry<T, Double> entry: innerReslt.entrySet())
		{
            Double value = entry.getValue();
			if (value > 0)
			{
				myResult.put(entry.getKey(), value);
			}
		}
		return myResult;
	}
}
