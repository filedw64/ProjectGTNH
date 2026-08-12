package projecte.emc;

import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.collector.DoubleCollector;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.emc.collector.MappingCollector;
import moze_intel.projecte.emc.generators.DoubleGenerator;
import moze_intel.projecte.emc.generators.IValueGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

//@RunWith(value = Parameterized.class)
public class GraphMapperTest {

	@Before
	public void setup() {
		//mappingCollector = new SimpleGraphMapper<String, Integer>(new IntArithmetic());
		SimpleGraphMapper<String, Double> mapper = new SimpleGraphMapper<>(new DoubleArithmetic());
		valueGenerator = new DoubleGenerator<>(mapper);
		mappingCollector = new DoubleCollector<>(mapper);
	}

	@Rule
	public Timeout timeout = new Timeout(5000);
	public IValueGenerator<String, Double> valueGenerator;
	public IMappingCollector<String, Double> mappingCollector;

	@org.junit.Test
	public void testGetOrCreateSet() {
		Map<String, Set<Integer>> map = new HashMap<>();
		Set<Integer> l1 = MappingCollector.getOrCreateSet(map, "abc");
		assertNotNull(l1);
		assertTrue(map.containsKey("abc"));
		Set<Integer> l2 = MappingCollector.getOrCreateSet(map, "abc");
		assertSame(l1, l2);
	}

	@org.junit.Test
	public void testGenerateValuesSimple() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "c4", Arrays.asList("a1", "a1", "a1", "a1"));
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);

	}

	@org.junit.Test
	public void testGenerateValuesSimpleMultiRecipe() {
		mappingCollector.setValueBefore("a1", 1.0);
		//2 Recipes for c4
		mappingCollector.addConversion(1, "c4", Arrays.asList("a1", "a1", "a1", "a1"));
		mappingCollector.addConversion(2, "c4", Arrays.asList("b2", "b2"));
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c4"), 1e-7); //2 * c4 = 2 * b2 => 2 * (2) = 2 * (2)
	}

	@org.junit.Test
	public void testGenerateValuesSimpleMultiRecipeWithEmptyAlternative() {
		mappingCollector.setValueBefore("a1", 1.0);
		//2 Recipes for c4
		mappingCollector.addConversion(1, "c4", Arrays.asList("a1", "a1", "a1", "a1"));
		mappingCollector.addConversion(1, "c4", new LinkedList<>());
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleFixedAfterInherit() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "c4", Arrays.asList("a1", "a1", "a1", "a1"));
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));
		mappingCollector.setValueAfter("b2", 20.0);

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleFixedDoNotInherit() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "c4", Arrays.asList("b2", "b2"));
		mappingCollector.setValueBefore("b2", 0.0);
		mappingCollector.setValueAfter("b2", 20.0);

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "c4"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleFixedDoNotInheritMultiRecipes() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "c", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "c", Arrays.asList("a1", "b"));
		mappingCollector.setValueBefore("b", 0.0);
		mappingCollector.setValueAfter("b", 20.0);

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleSelectMinValue() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.setValueBefore("b2", 2.0);
		mappingCollector.addConversion(1, "c", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "c", Arrays.asList("b2", "b2"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleSelectMinValueWithDependency() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.setValueBefore("b2", 2.0);
		mappingCollector.addConversion(1, "c", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "c", Arrays.asList("b2", "b2"));
		mappingCollector.addConversion(1, "d", Arrays.asList("c", "c"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
		assertEquals(4, getValue(values, "d"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesSimpleWoodToWorkBench() {
		mappingCollector.setValueBefore("planks", 1.0);
		mappingCollector.addConversion(4, "planks", Arrays.asList("wood"));
		mappingCollector.addConversion(1, "workbench", Arrays.asList("planks", "planks", "planks", "planks"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(0, getValue(values, "wood"), 1e-7);
		assertEquals(1, getValue(values, "planks"), 1e-7);
		assertEquals(4, getValue(values, "workbench"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesWood() {
		for (char i : "ABCD".toCharArray())
		{
			mappingCollector.setValueBefore("wood" + i, 32.0);
			mappingCollector.addConversion(4, "planks" + i, Arrays.asList("wood" + i));
		}

		for (char i : "ABCD".toCharArray()) {
			mappingCollector.addConversion(4, "planks" + i, Arrays.asList("wood"));
		}

		for (char i : "ABCD".toCharArray())
			for (char j : "ABCD".toCharArray())
				mappingCollector.addConversion(4, "stick", Arrays.asList("planks" + i, "planks" + j));
		mappingCollector.addConversion(1, "crafting_table", Arrays.asList("planksA", "planksA", "planksA", "planksA"));
		for (char i : "ABCD".toCharArray())
			for (char j : "ABCD".toCharArray())
				mappingCollector.addConversion(1, "wooden_hoe", Arrays.asList("stick", "stick", "planks" + i, "planks" + j));

		Map<String, Double> values = valueGenerator.generateValues();
		for (char i : "ABCD".toCharArray())
			assertEquals(32, getValue(values, "wood" + i), 1e-7);
		for (char i : "ABCD".toCharArray())
			assertEquals(8, getValue(values, "planks" + i), 1e-7);
		assertEquals(4, getValue(values, "stick"), 1e-7);
		assertEquals(32, getValue(values, "crafting_table"), 1e-7);
		assertEquals(24, getValue(values, "wooden_hoe"), 1e-7);

	}

	@org.junit.Test
	public void testGenerateValuesDeepConversions() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "b1", Arrays.asList("a1"));
		mappingCollector.addConversion(1, "c1", Arrays.asList("b1"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "b1"), 1e-7);
		assertEquals(1, getValue(values, "c1"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesDeepInvalidConversion() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "b", Arrays.asList("a1", "invalid1"));
		mappingCollector.addConversion(1, "invalid1", Arrays.asList("a1", "invalid2"));
		mappingCollector.addConversion(1, "invalid2", Arrays.asList("a1", "invalid3"));
		
		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "b"), 1e-7);
		assertEquals(0, getValue(values, "invalid1"), 1e-7);
		assertEquals(0, getValue(values, "invalid2"), 1e-7);
		assertEquals(0, getValue(values, "invalid3"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesMultiRecipeDeepInvalid() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "b2", Arrays.asList("invalid1"));
		mappingCollector.addConversion(1, "invalid1", Arrays.asList("a1", "invalid2"));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "invalid1"), 1e-7);
		assertEquals(0, getValue(values, "invalid2"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesMultiRecipesInvalidIngredient() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "b2", Arrays.asList("a1", "a1"));
		mappingCollector.addConversion(1, "b2", Arrays.asList("invalid"));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "invalid"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesCycleRecipe() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "cycle-1", Arrays.asList("a1"));
		mappingCollector.addConversion(1, "cycle-2", Arrays.asList("cycle-1"));
		mappingCollector.addConversion(1, "cycle-1", Arrays.asList("cycle-2"));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-2"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesBigCycleRecipe() {
		mappingCollector.setValueBefore("a1", 1.0);
		mappingCollector.addConversion(1, "cycle-1", Arrays.asList("a1"));
		mappingCollector.addConversion(1, "cycle-2", Arrays.asList("cycle-1"));
		mappingCollector.addConversion(1, "cycle-3", Arrays.asList("cycle-2"));
		mappingCollector.addConversion(1, "cycle-4", Arrays.asList("cycle-3"));
		mappingCollector.addConversion(1, "cycle-5", Arrays.asList("cycle-4"));
		mappingCollector.addConversion(1, "cycle-1", Arrays.asList("cycle-5"));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-2"), 1e-7);
		assertEquals(1, getValue(values, "cycle-3"), 1e-7);
		assertEquals(1, getValue(values, "cycle-4"), 1e-7);
		assertEquals(1, getValue(values, "cycle-5"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesFuelAndMatter() {
		final String coal = "coal";
		final String aCoal = "alchemicalCoal";
		final String aCoalBlock = "alchemicalCoalBlock";
		final String mFuel = "mobiusFuel";
		final String mFuelBlock = "mobiusFuelBlock";
		final String aFuel = "aeternalisFuel";
		final String aFuelBlock = "aeternalisFuelBlock";
		String repeat;

		mappingCollector.setValueBefore(coal, 128.0);

		mappingCollector.addConversion(1, aCoal, Arrays.asList(coal, coal, coal, coal));
		mappingCollector.addConversion(4, aCoal, Arrays.asList(mFuel));
		mappingCollector.addConversion(9, aCoal, Arrays.asList(aCoalBlock));
		repeat = aCoal;
		mappingCollector.addConversion(1, aCoalBlock, Arrays.asList(repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat));

		mappingCollector.addConversion(1, mFuel, Arrays.asList(aCoal, aCoal, aCoal, aCoal));
		mappingCollector.addConversion(4, mFuel, Arrays.asList(aFuel));
		mappingCollector.addConversion(9, mFuel, Arrays.asList(mFuelBlock));
		repeat = mFuel;
		mappingCollector.addConversion(1, mFuelBlock, Arrays.asList(repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat));

		mappingCollector.addConversion(1, aFuel, Arrays.asList(mFuel, mFuel, mFuel, mFuel));
		mappingCollector.addConversion(9, aFuel, Arrays.asList(aFuelBlock));
		repeat = aFuel;
		mappingCollector.addConversion(1, aFuelBlock, Arrays.asList(repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat, repeat));

		mappingCollector.setValueBefore("diamondBlock", 73728.0);
		final String dMatter = "darkMatter";
		final String dMatterBlock = "darkMatterBlock";

		mappingCollector.addConversion(1, dMatter, Arrays.asList(aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, "diamondBlock"));
		mappingCollector.addConversion(1, dMatter, Arrays.asList(dMatterBlock));
		mappingCollector.addConversion(4, dMatterBlock, Arrays.asList(dMatter, dMatter, dMatter, dMatter));

		final String rMatter = "redMatter";
		final String rMatterBlock = "redMatterBlock";
		mappingCollector.addConversion(1, rMatter, Arrays.asList(aFuel, aFuel, aFuel, dMatter, dMatter, dMatter, aFuel, aFuel, aFuel));
		mappingCollector.addConversion(1, rMatter, Arrays.asList(rMatterBlock));
		mappingCollector.addConversion(4, rMatterBlock, Arrays.asList(rMatter, rMatter, rMatter, rMatter));


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(128, getValue(values, coal), 1e-7);
		assertEquals(512, getValue(values, aCoal), 1e-7);
		assertEquals(4608, getValue(values, aCoalBlock), 1e-7);
		assertEquals(2048, getValue(values, mFuel), 1e-7);
		assertEquals(18432, getValue(values, mFuelBlock), 1e-7);
		assertEquals(8192, getValue(values, aFuel), 1e-7);
		assertEquals(73728, getValue(values, aFuelBlock), 1e-7);
		assertEquals(73728, getValue(values, "diamondBlock"), 1e-7);
		assertEquals(139264, getValue(values, dMatter), 1e-7);
		assertEquals(139264, getValue(values, dMatterBlock), 1e-7);
		assertEquals(466944, getValue(values, rMatter), 1e-7);
		assertEquals(466944, getValue(values, rMatterBlock), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesWool() {
		final String[] dyes = new String[]{"Blue", "Brown", "White", "Other"};
		final double[] dyeValue = new double[]{864, 176, 48, 16};
		for (int i = 0; i < dyes.length; i++)
		{
			mappingCollector.setValueBefore("dye" + dyes[i], dyeValue[i]);
			mappingCollector.addConversion(1, "wool" + dyes[i], Arrays.asList("woolWhite", "dye" + dyes[i]));
		}
		mappingCollector.setValueBefore("string", 12.0);
		mappingCollector.addConversion(1, "woolWhite", Arrays.asList("string", "string", "string", "string"));

		mappingCollector.setValueBefore("stick", 4.0);
		mappingCollector.setValueBefore("plank", 8.0);
		for (String dye : dyes) {
			mappingCollector.addConversion(1, "bed", Arrays.asList("plank", "plank", "plank", "wool" + dye, "wool" + dye, "wool" + dye));
			mappingCollector.addConversion(3, "carpet" + dye, Arrays.asList("wool" + dye, "wool" + dye));
			mappingCollector.addConversion(1, "painting", Arrays.asList("wool" + dye, "stick", "stick", "stick", "stick", "stick", "stick", "stick", "stick"));
		}

		Map<String, Double> values = valueGenerator.generateValues();
		for (int i = 0; i < dyes.length; i++) {
			assertEquals(dyeValue[i], getValue(values, "dye" + dyes[i]), 1e-7);
		}
		assertEquals(12, getValue(values, "string"), 1e-7);
		assertEquals(48, getValue(values, "woolWhite"), 1e-7);
		assertEquals(224, getValue(values, "woolBrown"), 1e-7);
		assertEquals(912, getValue(values, "woolBlue"), 1e-7);
		assertEquals(64, getValue(values, "woolOther"), 1e-7);

		assertEquals(32, getValue(values, "carpetWhite"), 1e-7);
		assertEquals(224.0 * 2 / 3, getValue(values, "carpetBrown"), 1e-7);
		assertEquals(608, getValue(values, "carpetBlue"), 1e-7);
		assertEquals(64.0 * 2 / 3, getValue(values, "carpetOther"), 1e-7);

		assertEquals(168, getValue(values, "bed"), 1e-7);
		assertEquals(80, getValue(values, "painting"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesBucketRecipe() {
		mappingCollector.setValueBefore("somethingElse", 9.0);
		mappingCollector.setValueBefore("container", 23.0);
		mappingCollector.setValueBefore("fluid", 17.0);
		mappingCollector.addConversion(1, "filledContainer", Arrays.asList("container", "fluid"));

		//Recipe that only consumes fluid:
		Map<String, Integer> map = new HashMap<>();
		map.put("container", -1);
		map.put("filledContainer", 1);
		map.put("somethingElse", 2);
		mappingCollector.addConversion(1, "fluidCraft", map);

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(9, getValue(values, "somethingElse"), 1e-7);
		assertEquals(23, getValue(values, "container"), 1e-7);
		assertEquals(17, getValue(values, "fluid"), 1e-7);
		assertEquals(17 + 23, getValue(values, "filledContainer"), 1e-7);
		assertEquals(17 + 2 * 9, getValue(values, "fluidCraft"), 1e-7);

	}

	@org.junit.Test
	public void testGenerateValuesWaterBucketRecipe() {
		mappingCollector.setValueBefore("somethingElse", 9.0);
		mappingCollector.setValueBefore("container", 23.0);
		mappingCollector.setValueBefore("fluid", -Double.MAX_VALUE);
		mappingCollector.addConversion(1, "filledContainer", Arrays.asList("container", "fluid"));

		//Recipe that only consumes fluid:
		Map<String, Integer> map = new HashMap<>();
		map.put("container", -1);
		map.put("filledContainer", 1);
		map.put("somethingElse", 2);
		mappingCollector.addConversion(1, "fluidCraft", map);

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(9, getValue(values, "somethingElse"), 1e-7);
		assertEquals(23, getValue(values, "container"), 1e-7);
		assertEquals(0, getValue(values, "fluid"), 1e-7);
		assertEquals(23, getValue(values, "filledContainer"), 1e-7);
		assertEquals(2 * 9, getValue(values, "fluidCraft"), 1e-7);

	}

	@org.junit.Test
	public void testGenerateValuesCycleRecipeExploit() {
		mappingCollector.setValueBefore("a1", 1.0);
		//Exploitable Cycle Recype
		mappingCollector.addConversion(1, "exploitable", Arrays.asList("a1"));
		mappingCollector.addConversion(2, "exploitable", Arrays.asList("exploitable"));

		//Not-exploitable Cycle Recype
		mappingCollector.addConversion(1, "notExploitable", Arrays.asList("a1"));
		mappingCollector.addConversion(2, "notExploitable", Arrays.asList("notExploitable", "notExploitable"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable"), 1e-7);
		assertEquals(1, getValue(values, "notExploitable"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesDelayedCycleRecipeExploit() {
		mappingCollector.setValueBefore("a1", 1.0);
		//Exploitable Cycle Recype
		mappingCollector.addConversion(1, "exploitable1", Arrays.asList("a1"));
		mappingCollector.addConversion(2, "exploitable2", Arrays.asList("exploitable1"));
		mappingCollector.addConversion(1, "exploitable1", Arrays.asList("exploitable2"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable2"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesCycleRecipeExploit2() {
		mappingCollector.setValueBefore("a1", 1.0);
		//Exploitable Cycle Recype
		mappingCollector.addConversion(1, "exploitable", Arrays.asList("a1"));
		mappingCollector.addConversion(2, "exploitable", Arrays.asList("exploitable"));
		mappingCollector.addConversion(1, "b", Arrays.asList("exploitable"));

		//Not-exploitable Cycle Recype
		mappingCollector.addConversion(1, "notExploitable", Arrays.asList("a1"));
		mappingCollector.addConversion(2, "notExploitable", Arrays.asList("notExploitable", "notExploitable"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable"), 1e-7);
		assertEquals(1, getValue(values, "notExploitable"), 1e-7);
		assertEquals(0, getValue(values, "b"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesCoalToFireChargeWithWildcard() {
		String[] logTypes = new String[]{"logA", "logB", "logC"};
		String[] log2Types = new String[]{"log2A", "log2B", "log2C"};
		String[] coalTypes = new String[]{"coal0", "coal1"};

		mappingCollector.setValueBefore("coalore", 0.0);
		mappingCollector.setValueBefore("coal0", 128.0);
		mappingCollector.setValueBefore("gunpowder", 192.0);
		mappingCollector.setValueBefore("blazepowder", 768.0);

		for (String logType : logTypes)
		{
			mappingCollector.setValueBefore(logType, 32.0);
			mappingCollector.addConversion(1, "log*", Arrays.asList(logType));
		}
		for (String log2Type : log2Types)
		{
			mappingCollector.setValueBefore(log2Type, 32.0);
			mappingCollector.addConversion(1, "log2*", Arrays.asList(log2Type));
		}
		mappingCollector.addConversion(1, "coal1", Arrays.asList("log*"));
		for (String coalType : coalTypes) {
			mappingCollector.addConversion(1, "coal*", Arrays.asList(coalType));
			mappingCollector.addConversion(3, "firecharge", Arrays.asList(coalType, "gunpowder", "blazepowder"));
		}
		mappingCollector.addConversion(1, "firecharge*", Arrays.asList("firecharge"));
		Map<String, Integer> m = new HashMap<>();
		m.put("coal0", 9);
		mappingCollector.addConversion(1, "coalblock", m);

		m.clear();
		//Philosophers stone smelting 7xCoalOre -> 7xCoal
		m.put("coalore", 7);
		m.put("coal*", 1);
		mappingCollector.addConversion(7, "coal0", m);

		m.clear();
		//Philosophers stone smelting logs
		m.put("log*", 7);
		m.put("coal*", 1);
		mappingCollector.addConversion(7, "coal1", m);

		m.clear();
		//Philosophers stone smelting log2s
		m.put("log2*", 7);
		m.put("coal*", 1);
		mappingCollector.addConversion(7, "coal1", m);


		//Smelting single coal ore
		mappingCollector.addConversion(1, "coal0", Arrays.asList("coalore"));
		//Coal Block
		mappingCollector.addConversion(9, "coal0", Arrays.asList("coalblock"));

		Map<String, Double> values = valueGenerator.generateValues();
		for (String logType : logTypes) {
			assertEquals(32, getValue(values, logType), 1e-7);
		}
		assertEquals(32, getValue(values, "log*"), 1e-7);
		assertEquals(128, getValue(values, "coal0"), 1e-7);
		assertEquals(32, getValue(values, "coal1"), 1e-7);
		assertEquals(32, getValue(values, "coal*"), 1e-7);
		assertEquals((32 + 192 + 768) / 3.0, getValue(values, "firecharge"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesChisel2AntiBlock() {
		final String gDust = "glowstone dust";
		final String stone = "stone";

		final String[] dyes = new String[]{"Blue", "Brown", "White", "Other"};
		final double[] dyeValue = new double[]{864, 176, 48, 16};
		for (int i = 0; i < dyes.length; i++)
		{
			mappingCollector.setValueBefore("dye" + dyes[i], dyeValue[i]);
			mappingCollector.addConversion(8, "antiblock" + dyes[i], Arrays.asList(
					"antiblock_all", "antiblock_all", "antiblock_all",
					"antiblock_all", "dye" + dyes[i], "antiblock_all",
					"antiblock_all", "antiblock_all", "antiblock_all"
			));
			mappingCollector.addConversion(1, "antiblock_all", Arrays.asList("antiblock" + dyes[i]));
		}

		mappingCollector.setValueBefore(gDust, 384.0);
		mappingCollector.setValueBefore(stone, 1.0);
		mappingCollector.addConversion(8, "antiblockWhite", Arrays.asList(
				stone, stone, stone,
				stone, gDust, stone,
				stone, stone, stone));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(49, getValue(values, "antiblockWhite"), 1e-7);
		for (int i = 0; i < dyes.length; i++) {
			assertEquals(dyeValue[i], getValue(values, "dye" + dyes[i]), 1e-7);
			if (!dyes[i].equals("White"))
				assertEquals((dyeValue[i] + 392)/8, getValue(values, "antiblock" + dyes[i]), 1e-7);
		}
	}

	@org.junit.Test
	public void testGenerateValuesZeroCountIngredientDependency() {
		mappingCollector.setValueBefore("a", 2.0);
		mappingCollector.setValueBefore("b", 3.0);
		mappingCollector.setValueBefore("notConsume1", 1.0);
		HashMap<String, Integer> ingredients = new HashMap<>();
		ingredients.put("a", 1);
		ingredients.put("b", 1);
		ingredients.put("notConsume1", 0);
		mappingCollector.addConversion(1, "c1", ingredients);
		ingredients.remove("notConsume1");
		ingredients.put("notConsume2", 0);
		mappingCollector.addConversion(1, "c2", ingredients);


		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(2, getValue(values, "a"), 1e-7);
		assertEquals(3, getValue(values, "b"), 1e-7);
		assertEquals(1, getValue(values, "notConsume1"), 1e-7);
		assertEquals(0, getValue(values, "notConsume2"), 1e-7);
		assertEquals(5, getValue(values, "c1"), 1e-7);
		assertEquals(5, getValue(values, "c2"), 1e-7);
	}


	@org.junit.Test
	public void testGenerateValuesFreeAlternatives() {
		mappingCollector.setValueBefore("freeWater", -Double.MAX_VALUE/* = 'Free' */);
		mappingCollector.setValueBefore("waterBottle", 0.0);
		mappingCollector.addConversion(1, "waterGroup", Arrays.asList("freeWater"));
		mappingCollector.addConversion(1, "waterGroup", Arrays.asList("waterBottle"));
		mappingCollector.setValueBefore("a", 3.0);
		mappingCollector.addConversion(1, "result", Arrays.asList("a", "waterGroup"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(3, getValue(values, "a"), 1e-7);
		assertEquals(0, getValue(values, "freeWater"), 1e-7);
		assertEquals(0, getValue(values, "waterBottle"), 1e-7);
		assertEquals(0, getValue(values, "waterGroup"), 1e-7);
		assertEquals(3, getValue(values, "result"), 1e-7);
	}

	@org.junit.Test
	public void testGenerateValuesFreeAlternativesWithNegativeIngredients() {
		mappingCollector.setValueBefore("bucket", 768.0);
		mappingCollector.setValueBefore("waterBucket", 768.0);
		mappingCollector.setValueBefore("waterBottle", 0.0);
		Map<String, Integer> m = new HashMap<>();
		m.put("waterBucket", 1);
		m.put("bucket", -1);
		mappingCollector.addConversion(1, "waterGroup", m);
		mappingCollector.addConversion(1, "waterGroup", Arrays.asList("waterBottle"));
		mappingCollector.setValueBefore("a", 3.0);
		mappingCollector.addConversion(1, "result", Arrays.asList("a", "waterGroup"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(3, getValue(values, "a"), 1e-7);
		assertEquals(768, getValue(values, "bucket"), 1e-7);
		assertEquals(768, getValue(values, "waterBucket"), 1e-7);
		assertEquals(0, getValue(values, "waterGroup"), 1e-7);
		assertEquals(3, getValue(values, "result"), 1e-7);
	}


	@org.junit.Test
	public void testOverflowWithIngredients() {
		mappingCollector.setValueBefore("a", Double.MAX_VALUE / 2);
		mappingCollector.setValueBefore("b", Double.MAX_VALUE / 2);
		mappingCollector.addConversion(1, "c", Arrays.asList("a", "b"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "a"), 1e-7);
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "b"), 1e-7);
		assertEquals(Double.MAX_VALUE, getValue(values, "c"), 1e-7);
	}

	@org.junit.Test
	public void testOverflowWithAmount() {
		mappingCollector.setValueBefore("a", Double.MAX_VALUE / 2);
		mappingCollector.addConversion(3, "a", Arrays.asList("something"));

		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "a"), 1e-7);
	}

	@org.junit.Test
	public void testOverwriteConversions()
	{
		mappingCollector.setValueBefore("a", 1.0);
		mappingCollector.setValueFromConversion(1, "b", Arrays.asList("a", "a", "a"));
		mappingCollector.addConversion(1, "b", Arrays.asList("a"));
		mappingCollector.addConversion(1, "c", Arrays.asList("b", "b"));
		Map<String, Double> values = valueGenerator.generateValues();
		assertEquals(1, getValue(values, "a"), 1e-7);
		assertEquals(3, getValue(values, "b"), 1e-7);
		assertEquals(6, getValue(values, "c"), 1e-7);

	}

	private static <T, V extends Number> double getValue(Map<T, V> map, T key) {
		V val = map.get(key);
		if (val == null) return 0;
		return val.doubleValue();
	}
}