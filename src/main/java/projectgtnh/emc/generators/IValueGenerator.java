package projectgtnh.emc.generators;

import projectgtnh.emc.collector.IMappingCollector;

import java.util.Map;

public interface IValueGenerator<T, V extends Comparable<V>>
{
	public Map<T, V> generateValues();
}
