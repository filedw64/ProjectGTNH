package projecte.emc;

import com.google.common.collect.ImmutableMap;
import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.collector.DoubleCollector;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.emc.generators.DoubleGenerator;
import moze_intel.projecte.emc.generators.IValueGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DoubleArithmeticSpecificTest
{
	public IValueGenerator<String, Double> valueGenerator;
	public IMappingCollector<String, Double> mappingCollector;

	@Before
	public void setup()
	{
		SimpleGraphMapper<String, Double> mapper = new SimpleGraphMapper<>(new DoubleArithmetic());
		valueGenerator = new DoubleGenerator<>(mapper);
		mappingCollector = new DoubleCollector<>(mapper);
	}

	@Test
	public void slabRecipe()
	{
		mappingCollector.setValueBefore("s", 1.0);
		mappingCollector.setValueBefore("redstone", 64.0);
		mappingCollector.setValueBefore("glass", 1.0);
		mappingCollector.addConversion(6, "slab", Arrays.asList("s", "s", "s"));
		mappingCollector.addConversion(1, "doubleslab", Arrays.asList("slab", "slab"));
		mappingCollector.addConversion(1, "transferpipe", Arrays.asList("slab", "slab", "slab", "glass", "redstone", "glass", "slab", "slab", "slab"));
		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "s"), 1e-7);
		assertEquals(64, getValue(values, "redstone"), 1e-7);
		assertEquals(1, getValue(values, "glass"), 1e-7);
		assertEquals(0.5, getValue(values, "slab"), 1e-7);
		assertEquals(3 + 64 + 2, getValue(values, "transferpipe"), 1e-7);
		assertEquals(1, getValue(values, "doubleslab"), 1e-7);

	}

	@Test
	public void nuggetExploits()
	{
		mappingCollector.setValueBefore("ingot", 2048.0);
		mappingCollector.setValueBefore("melon", 16.0);
		mappingCollector.addConversion(9, "nugget", Arrays.asList("ingot"));
		mappingCollector.addConversion(1, "goldmelon", Arrays.asList(
				"nugget", "nugget", "nugget",
				"nugget", "melon", "nugget",
				"nugget", "nugget", "nugget"
		));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(2048, getValue(values, "ingot"), 1e-7);
		assertEquals(16, getValue(values, "melon"), 1e-7);
		assertEquals(2048.0 / 9, getValue(values, "nugget"), 1e-7);
		assertEquals(8.0 * 2048 / 9 + 16, getValue(values, "goldmelon"), 1e-7);
	}

	@Test
	public void moltenEnderpearl()
	{
		mappingCollector.setValueBefore("enderpearl", 1024.0);
		mappingCollector.setValueBefore("bucket", 768.0);

		mappingCollector.addConversion(250, "moltenEnder", Arrays.asList("enderpearl"));
		mappingCollector.addConversion(1, "moltenEnderBucket", ImmutableMap.of("moltenEnder", 1000, "bucket", 1));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1024, getValue(values, "enderpearl"), 1e-7);
		assertEquals(4.096, getValue(values, "moltenEnder"), 1e-7);
		assertEquals(768, getValue(values, "bucket"), 1e-7);
		assertEquals(4*1024+768, getValue(values, "moltenEnderBucket"), 1e-7);
	}


	@Test
	public void reliquaryVials()
	{
		mappingCollector.setValueBefore("glass", 1.0);

		mappingCollector.addConversion(16, "pane", ImmutableMap.of("glass", 6));
		mappingCollector.addConversion(5, "vial", ImmutableMap.of("pane", 5));
		//Internal EMC of pane and vial: 3/8 = 0.375
		//So 8 * vial should have an emc of 3 => testItem should have emc of 1
		mappingCollector.addConversion(3, "testItem1", ImmutableMap.of("pane", 8));
		mappingCollector.addConversion(3, "testItem2", ImmutableMap.of("vial", 8));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "glass"), 1e-7);
		assertEquals(0.375, getValue(values, "pane"), 1e-7);
		assertEquals(0.375, getValue(values, "vial"), 1e-7);
		assertEquals(1, getValue(values, "testItem1"), 1e-7);
		assertEquals(1, getValue(values, "testItem2"), 1e-7);
	}

	@Test
	public void propagation()
	{
		mappingCollector.setValueBefore("a", 1.0);

		mappingCollector.addConversion(2, "ahalf", ImmutableMap.of("a", 1));
		mappingCollector.addConversion(1, "ahalf2", ImmutableMap.of("ahalf", 1));
		mappingCollector.addConversion(1, "2ahalf2", ImmutableMap.of("ahalf2", 2));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a"), 1e-7);
		assertEquals(0.5, getValue(values, "ahalf"), 1e-7);
		assertEquals(0.5, getValue(values, "ahalf2"), 1e-7);
		assertEquals(1, getValue(values, "2ahalf2"), 1e-7);

	}

	private static <T, V extends Number> double getValue(Map<T, V> map, T key) {
		V val = map.get(key);
		if (val == null) return 0;
		return val.doubleValue();
	}
}