package moze_intel.projecte.emc.generators;

import java.util.Map;

public interface IValueGenerator<T, V extends Comparable<V>>
{
	Map<T, V> generateValues();
}
