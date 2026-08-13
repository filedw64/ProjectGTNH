package projecte.emc;

import com.google.common.collect.ImmutableMap;
import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DoubleArithmeticSpecificTest
{
	public SimpleGraphMapper<String, Double> mapper;

	@Before
	public void setup() {
		// 直接使用 SimpleGraphMapper 实例，彻底废弃无用的 DoubleCollector 和 DoubleGenerator
		mapper = new SimpleGraphMapper<>(DoubleArithmetic.INSTANCE);
	}

	@Test
	public void slabRecipe()
	{
		mapper.setValueBefore("s", 1.0);
		mapper.setValueBefore("redstone", 64.0);
		mapper.setValueBefore("glass", 1.0);

		// 使用 nCopies 减少数组对象分配
		mapper.addConversion(6, "slab", Collections.nCopies(3, "s"));
		mapper.addConversion(1, "doubleslab", Collections.nCopies(2, "slab"));
		mapper.addConversion(1, "transferpipe", Arrays.asList("slab", "slab", "slab", "glass", "redstone", "glass", "slab", "slab", "slab"));

		Map<String, Double> values = mapper.generateValues();
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
		mapper.setValueBefore("ingot", 2048.0);
		mapper.setValueBefore("melon", 16.0);

		// singletonList
		mapper.addConversion(9, "nugget", Collections.singletonList("ingot"));
		mapper.addConversion(1, "goldmelon", Arrays.asList(
			"nugget", "nugget", "nugget",
			"nugget", "melon", "nugget",
			"nugget", "nugget", "nugget"
		));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(2048, getValue(values, "ingot"), 1e-7);
		assertEquals(16, getValue(values, "melon"), 1e-7);
		assertEquals(2048.0 / 9, getValue(values, "nugget"), 1e-7);
		assertEquals(8.0 * 2048 / 9 + 16, getValue(values, "goldmelon"), 1e-7);
	}

	@Test
	public void moltenEnderpearl()
	{
		mapper.setValueBefore("enderpearl", 1024.0);
		mapper.setValueBefore("bucket", 768.0);

		mapper.addConversion(250, "moltenEnder", Collections.singletonList("enderpearl"));
		mapper.addConversion(1, "moltenEnderBucket", ImmutableMap.of("moltenEnder", 1000, "bucket", 1));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1024, getValue(values, "enderpearl"), 1e-7);
		assertEquals(4.096, getValue(values, "moltenEnder"), 1e-7);
		assertEquals(768, getValue(values, "bucket"), 1e-7);
		assertEquals(4 * 1024 + 768, getValue(values, "moltenEnderBucket"), 1e-7);
	}

	@Test
	public void reliquaryVials()
	{
		mapper.setValueBefore("glass", 1.0);

		mapper.addConversion(16, "pane", ImmutableMap.of("glass", 6));
		mapper.addConversion(5, "vial", ImmutableMap.of("pane", 5));
		//Internal EMC of pane and vial: 3/8 = 0.375
		//So 8 * vial should have an emc of 3 => testItem should have emc of 1
		mapper.addConversion(3, "testItem1", ImmutableMap.of("pane", 8));
		mapper.addConversion(3, "testItem2", ImmutableMap.of("vial", 8));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "glass"), 1e-7);
		assertEquals(0.375, getValue(values, "pane"), 1e-7);
		assertEquals(0.375, getValue(values, "vial"), 1e-7);
		assertEquals(1, getValue(values, "testItem1"), 1e-7);
		assertEquals(1, getValue(values, "testItem2"), 1e-7);
	}

	@Test
	public void propagation()
	{
		mapper.setValueBefore("a", 1.0);

		mapper.addConversion(2, "ahalf", ImmutableMap.of("a", 1));
		mapper.addConversion(1, "ahalf2", ImmutableMap.of("ahalf", 1));
		mapper.addConversion(1, "2ahalf2", ImmutableMap.of("ahalf2", 2));

		Map<String, Double> values = mapper.generateValues();
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
