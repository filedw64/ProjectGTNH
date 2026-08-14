package projecte.emc;

import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.collector.MappingCollector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GraphMapperTest {

	@Rule
	public Timeout timeout = new Timeout(5000);

	public SimpleGraphMapper<String, Double> mapper;

	@Before
	public void setup() {
		// 直接使用 SimpleGraphMapper 实例，彻底废弃无用的 DoubleCollector 和 DoubleGenerator
		mapper = new SimpleGraphMapper<>(DoubleArithmetic.INSTANCE);
	}

	@Test
	public void testGetOrCreateSet() {
		Map<String, Set<Integer>> map = new HashMap<>();
		Set<Integer> l1 = MappingCollector.getOrCreateSet(map, "abc");
		assertNotNull(l1);
		assertTrue(map.containsKey("abc"));
		Set<Integer> l2 = MappingCollector.getOrCreateSet(map, "abc");
		assertSame(l1, l2);
	}

	@Test
	public void testGenerateValuesSimple() {
		mapper.setValueBefore("a1", 1.0);
		// 使用 Collections.nCopies 避免无意义的数组分配
		mapper.addConversion(1, "c4", Collections.nCopies(4, "a1"));
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleMultiRecipe() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "c4", Collections.nCopies(4, "a1"));
		mapper.addConversion(2, "c4", Collections.nCopies(2, "b2"));
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c4"), 1e-7); // 2 * c4 = 2 * b2 => 2 * (2) = 2 * (2)
	}

	@Test
	public void testGenerateValuesSimpleMultiRecipeWithEmptyAlternative() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "c4", Collections.nCopies(4, "a1"));
		mapper.addConversion(1, "c4", Collections.emptyList()); // 使用 emptyList
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleFixedAfterInherit() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "c4", Collections.nCopies(4, "a1"));
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));
		mapper.setValueAfter("b2", 20.0);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b2"), 1e-7);
		assertEquals(4, getValue(values, "c4"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleFixedDoNotInherit() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "c4", Collections.nCopies(2, "b2"));
		mapper.setValueBefore("b2", 0.0);
		mapper.setValueAfter("b2", 20.0);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "c4"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleFixedDoNotInheritMultiRecipes() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "c", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "c", Arrays.asList("a1", "b"));
		mapper.setValueBefore("b", 0.0);
		mapper.setValueAfter("b", 20.0);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(20, getValue(values, "b"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleSelectMinValue() {
		mapper.setValueBefore("a1", 1.0);
		mapper.setValueBefore("b2", 2.0);
		mapper.addConversion(1, "c", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "c", Collections.nCopies(2, "b2"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleSelectMinValueWithDependency() {
		mapper.setValueBefore("a1", 1.0);
		mapper.setValueBefore("b2", 2.0);
		mapper.addConversion(1, "c", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "c", Collections.nCopies(2, "b2"));
		mapper.addConversion(1, "d", Collections.nCopies(2, "c"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(2, getValue(values, "c"), 1e-7);
		assertEquals(4, getValue(values, "d"), 1e-7);
	}

	@Test
	public void testGenerateValuesSimpleWoodToWorkBench() {
		mapper.setValueBefore("planks", 1.0);
		// singletonList
		mapper.addConversion(4, "planks", Collections.singletonList("wood"));
		mapper.addConversion(1, "workbench", Collections.nCopies(4, "planks"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(0, getValue(values, "wood"), 1e-7);
		assertEquals(1, getValue(values, "planks"), 1e-7);
		assertEquals(4, getValue(values, "workbench"), 1e-7);
	}

	@Test
	public void testGenerateValuesWood() {
		for (char i : "ABCD".toCharArray()) {
			mapper.setValueBefore("wood" + i, 32.0);
			mapper.addConversion(4, "planks" + i, Collections.singletonList("wood" + i));
		}

		for (char i : "ABCD".toCharArray()) {
			mapper.addConversion(4, "planks" + i, Collections.singletonList("wood"));
		}

		for (char i : "ABCD".toCharArray()) {
			for (char j : "ABCD".toCharArray()) {
				mapper.addConversion(4, "stick", Arrays.asList("planks" + i, "planks" + j));
			}
		}

		mapper.addConversion(1, "crafting_table", Collections.nCopies(4, "planksA"));

		for (char i : "ABCD".toCharArray()) {
			for (char j : "ABCD".toCharArray()) {
				mapper.addConversion(1, "wooden_hoe", Arrays.asList("stick", "stick", "planks" + i, "planks" + j));
			}
		}

		Map<String, Double> values = mapper.generateValues();
		for (char i : "ABCD".toCharArray()) {
			assertEquals(32, getValue(values, "wood" + i), 1e-7);
			assertEquals(8, getValue(values, "planks" + i), 1e-7);
		}
		assertEquals(4, getValue(values, "stick"), 1e-7);
		assertEquals(32, getValue(values, "crafting_table"), 1e-7);
		assertEquals(24, getValue(values, "wooden_hoe"), 1e-7);
	}

	@Test
	public void testGenerateValuesDeepConversions() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "b1", Collections.singletonList("a1"));
		mapper.addConversion(1, "c1", Collections.singletonList("b1"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "b1"), 1e-7);
		assertEquals(1, getValue(values, "c1"), 1e-7);
	}

	@Test
	public void testGenerateValuesDeepInvalidConversion() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "b", Arrays.asList("a1", "invalid1"));
		mapper.addConversion(1, "invalid1", Arrays.asList("a1", "invalid2"));
		mapper.addConversion(1, "invalid2", Arrays.asList("a1", "invalid3"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "b"), 1e-7);
		assertEquals(0, getValue(values, "invalid1"), 1e-7);
		assertEquals(0, getValue(values, "invalid2"), 1e-7);
		assertEquals(0, getValue(values, "invalid3"), 1e-7);
	}

	@Test
	public void testGenerateValuesMultiRecipeDeepInvalid() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "b2", Collections.singletonList("invalid1"));
		mapper.addConversion(1, "invalid1", Arrays.asList("a1", "invalid2"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "invalid1"), 1e-7);
		assertEquals(0, getValue(values, "invalid2"), 1e-7);
	}

	@Test
	public void testGenerateValuesMultiRecipesInvalidIngredient() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "b2", Collections.nCopies(2, "a1"));
		mapper.addConversion(1, "b2", Collections.singletonList("invalid"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(2, getValue(values, "b2"), 1e-7);
		assertEquals(0, getValue(values, "invalid"), 1e-7);
	}

	@Test
	public void testGenerateValuesCycleRecipe() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "cycle-1", Collections.singletonList("a1"));
		mapper.addConversion(1, "cycle-2", Collections.singletonList("cycle-1"));
		mapper.addConversion(1, "cycle-1", Collections.singletonList("cycle-2"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-2"), 1e-7);
	}

	@Test
	public void testGenerateValuesBigCycleRecipe() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "cycle-1", Collections.singletonList("a1"));
		mapper.addConversion(1, "cycle-2", Collections.singletonList("cycle-1"));
		mapper.addConversion(1, "cycle-3", Collections.singletonList("cycle-2"));
		mapper.addConversion(1, "cycle-4", Collections.singletonList("cycle-3"));
		mapper.addConversion(1, "cycle-5", Collections.singletonList("cycle-4"));
		mapper.addConversion(1, "cycle-1", Collections.singletonList("cycle-5"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-1"), 1e-7);
		assertEquals(1, getValue(values, "cycle-2"), 1e-7);
		assertEquals(1, getValue(values, "cycle-3"), 1e-7);
		assertEquals(1, getValue(values, "cycle-4"), 1e-7);
		assertEquals(1, getValue(values, "cycle-5"), 1e-7);
	}

	@Test
	public void testGenerateValuesFuelAndMatter() {
		final String coal = "coal";
		final String aCoal = "alchemicalCoal";
		final String aCoalBlock = "alchemicalCoalBlock";
		final String mFuel = "mobiusFuel";
		final String mFuelBlock = "mobiusFuelBlock";
		final String aFuel = "aeternalisFuel";
		final String aFuelBlock = "aeternalisFuelBlock";

		mapper.setValueBefore(coal, 128.0);

		mapper.addConversion(1, aCoal, Collections.nCopies(4, coal));
		mapper.addConversion(4, aCoal, Collections.singletonList(mFuel));
		mapper.addConversion(9, aCoal, Collections.singletonList(aCoalBlock));
		mapper.addConversion(1, aCoalBlock, Collections.nCopies(9, aCoal));

		mapper.addConversion(1, mFuel, Collections.nCopies(4, aCoal));
		mapper.addConversion(4, mFuel, Collections.singletonList(aFuel));
		mapper.addConversion(9, mFuel, Collections.singletonList(mFuelBlock));
		mapper.addConversion(1, mFuelBlock, Collections.nCopies(9, mFuel));

		mapper.addConversion(1, aFuel, Collections.nCopies(4, mFuel));
		mapper.addConversion(9, aFuel, Collections.singletonList(aFuelBlock));
		mapper.addConversion(1, aFuelBlock, Collections.nCopies(9, aFuel));

		mapper.setValueBefore("diamondBlock", 73728.0);
		final String dMatter = "darkMatter";
		final String dMatterBlock = "darkMatterBlock";

		mapper.addConversion(1, dMatter, Arrays.asList(aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, aFuel, "diamondBlock"));
		mapper.addConversion(1, dMatter, Collections.singletonList(dMatterBlock));
		mapper.addConversion(4, dMatterBlock, Collections.nCopies(4, dMatter));

		final String rMatter = "redMatter";
		final String rMatterBlock = "redMatterBlock";
		mapper.addConversion(1, rMatter, Arrays.asList(aFuel, aFuel, aFuel, dMatter, dMatter, dMatter, aFuel, aFuel, aFuel));
		mapper.addConversion(1, rMatter, Collections.singletonList(rMatterBlock));
		mapper.addConversion(4, rMatterBlock, Collections.nCopies(4, rMatter));

		Map<String, Double> values = mapper.generateValues();
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

	@Test
	public void testGenerateValuesWool() {
		final String[] dyes = new String[]{"Blue", "Brown", "White", "Other"};
		final double[] dyeValue = new double[]{864, 176, 48, 16};
		for (int i = 0; i < dyes.length; i++) {
			mapper.setValueBefore("dye" + dyes[i], dyeValue[i]);
			mapper.addConversion(1, "wool" + dyes[i], Arrays.asList("woolWhite", "dye" + dyes[i]));
		}

		mapper.setValueBefore("string", 12.0);
		mapper.addConversion(1, "woolWhite", Collections.nCopies(4, "string"));

		mapper.setValueBefore("stick", 4.0);
		mapper.setValueBefore("plank", 8.0);
		for (String dye : dyes) {
			mapper.addConversion(1, "bed", Arrays.asList("plank", "plank", "plank", "wool" + dye, "wool" + dye, "wool" + dye));
			mapper.addConversion(3, "carpet" + dye, Collections.nCopies(2, "wool" + dye));
			mapper.addConversion(1, "painting", Arrays.asList("wool" + dye, "stick", "stick", "stick", "stick", "stick", "stick", "stick", "stick"));
		}

		Map<String, Double> values = mapper.generateValues();
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

	@Test
	public void testGenerateValuesBucketRecipe() {
		mapper.setValueBefore("somethingElse", 9.0);
		mapper.setValueBefore("container", 23.0);
		mapper.setValueBefore("fluid", 17.0);
		mapper.addConversion(1, "filledContainer", Arrays.asList("container", "fluid"));

		Map<String, Integer> map = new HashMap<>();
		map.put("container", -1);
		map.put("filledContainer", 1);
		map.put("somethingElse", 2);
		mapper.addConversion(1, "fluidCraft", map);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(9, getValue(values, "somethingElse"), 1e-7);
		assertEquals(23, getValue(values, "container"), 1e-7);
		assertEquals(17, getValue(values, "fluid"), 1e-7);
		assertEquals(17 + 23, getValue(values, "filledContainer"), 1e-7);
		assertEquals(17 + 2 * 9, getValue(values, "fluidCraft"), 1e-7);
	}

	@Test
	public void testGenerateValuesWaterBucketRecipe() {
		mapper.setValueBefore("somethingElse", 9.0);
		mapper.setValueBefore("container", 23.0);
		mapper.setValueBefore("fluid", -Double.MAX_VALUE);
		mapper.addConversion(1, "filledContainer", Arrays.asList("container", "fluid"));

		Map<String, Integer> map = new HashMap<>();
		map.put("container", -1);
		map.put("filledContainer", 1);
		map.put("somethingElse", 2);
		mapper.addConversion(1, "fluidCraft", map);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(9, getValue(values, "somethingElse"), 1e-7);
		assertEquals(23, getValue(values, "container"), 1e-7);
		assertEquals(0, getValue(values, "fluid"), 1e-7);
		assertEquals(23, getValue(values, "filledContainer"), 1e-7);
		assertEquals(2 * 9, getValue(values, "fluidCraft"), 1e-7);
	}

	@Test
	public void testGenerateValuesCycleRecipeExploit() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "exploitable", Collections.singletonList("a1"));
		mapper.addConversion(2, "exploitable", Collections.singletonList("exploitable"));

		mapper.addConversion(1, "notExploitable", Collections.singletonList("a1"));
		mapper.addConversion(2, "notExploitable", Collections.nCopies(2, "notExploitable"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable"), 1e-7);
		assertEquals(1, getValue(values, "notExploitable"), 1e-7);
	}

	@Test
	public void testGenerateValuesDelayedCycleRecipeExploit() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "exploitable1", Collections.singletonList("a1"));
		mapper.addConversion(2, "exploitable2", Collections.singletonList("exploitable1"));
		mapper.addConversion(1, "exploitable1", Collections.singletonList("exploitable2"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable2"), 1e-7);
	}

	@Test
	public void testGenerateValuesCycleRecipeExploit2() {
		mapper.setValueBefore("a1", 1.0);
		mapper.addConversion(1, "exploitable", Collections.singletonList("a1"));
		mapper.addConversion(2, "exploitable", Collections.singletonList("exploitable"));
		mapper.addConversion(1, "b", Collections.singletonList("exploitable"));

		mapper.addConversion(1, "notExploitable", Collections.singletonList("a1"));
		mapper.addConversion(2, "notExploitable", Collections.nCopies(2, "notExploitable"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(1, getValue(values, "a1"), 1e-7);
		assertEquals(0, getValue(values, "exploitable"), 1e-7);
		assertEquals(1, getValue(values, "notExploitable"), 1e-7);
		assertEquals(0, getValue(values, "b"), 1e-7);
	}

	@Test
	public void testGenerateValuesCoalToFireChargeWithWildcard() {
		String[] logTypes = new String[]{"logA", "logB", "logC"};
		String[] log2Types = new String[]{"log2A", "log2B", "log2C"};
		String[] coalTypes = new String[]{"coal0", "coal1"};

		mapper.setValueBefore("coalore", 0.0);
		mapper.setValueBefore("coal0", 128.0);
		mapper.setValueBefore("gunpowder", 192.0);
		mapper.setValueBefore("blazepowder", 768.0);

		for (String logType : logTypes) {
			mapper.setValueBefore(logType, 32.0);
			mapper.addConversion(1, "log*", Collections.singletonList(logType));
		}
		for (String log2Type : log2Types) {
			mapper.setValueBefore(log2Type, 32.0);
			mapper.addConversion(1, "log2*", Collections.singletonList(log2Type));
		}

		mapper.addConversion(1, "coal1", Collections.singletonList("log*"));

		for (String coalType : coalTypes) {
			mapper.addConversion(1, "coal*", Collections.singletonList(coalType));
			mapper.addConversion(3, "firecharge", Arrays.asList(coalType, "gunpowder", "blazepowder"));
		}

		mapper.addConversion(1, "firecharge*", Collections.singletonList("firecharge"));

		Map<String, Integer> m = new HashMap<>();
		m.put("coal0", 9);
		mapper.addConversion(1, "coalblock", m);

		m.clear();
		m.put("coalore", 7);
		m.put("coal*", 1);
		mapper.addConversion(7, "coal0", m);

		m.clear();
		m.put("log*", 7);
		m.put("coal*", 1);
		mapper.addConversion(7, "coal1", m);

		m.clear();
		m.put("log2*", 7);
		m.put("coal*", 1);
		mapper.addConversion(7, "coal1", m);

		mapper.addConversion(1, "coal0", Collections.singletonList("coalore"));
		mapper.addConversion(9, "coal0", Collections.singletonList("coalblock"));

		Map<String, Double> values = mapper.generateValues();
		for (String logType : logTypes) {
			assertEquals(32, getValue(values, logType), 1e-7);
		}
		assertEquals(32, getValue(values, "log*"), 1e-7);
		assertEquals(128, getValue(values, "coal0"), 1e-7);
		assertEquals(32, getValue(values, "coal1"), 1e-7);
		assertEquals(32, getValue(values, "coal*"), 1e-7);
		assertEquals((32 + 192 + 768) / 3.0, getValue(values, "firecharge"), 1e-7);
	}

	@Test
	public void testGenerateValuesChisel2AntiBlock() {
		final String gDust = "glowstone dust";
		final String stone = "stone";

		final String[] dyes = new String[]{"Blue", "Brown", "White", "Other"};
		final double[] dyeValue = new double[]{864, 176, 48, 16};
		for (int i = 0; i < dyes.length; i++) {
			mapper.setValueBefore("dye" + dyes[i], dyeValue[i]);
			mapper.addConversion(8, "antiblock" + dyes[i], Arrays.asList(
				"antiblock_all", "antiblock_all", "antiblock_all",
				"antiblock_all", "dye" + dyes[i], "antiblock_all",
				"antiblock_all", "antiblock_all", "antiblock_all"
			));
			mapper.addConversion(1, "antiblock_all", Collections.singletonList("antiblock" + dyes[i]));
		}

		mapper.setValueBefore(gDust, 384.0);
		mapper.setValueBefore(stone, 1.0);
		mapper.addConversion(8, "antiblockWhite", Arrays.asList(
			stone, stone, stone,
			stone, gDust, stone,
			stone, stone, stone));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(49, getValue(values, "antiblockWhite"), 1e-7);
		for (int i = 0; i < dyes.length; i++) {
			assertEquals(dyeValue[i], getValue(values, "dye" + dyes[i]), 1e-7);
			if (!dyes[i].equals("White")) {
				assertEquals((dyeValue[i] + 392) / 8, getValue(values, "antiblock" + dyes[i]), 1e-7);
			}
		}
	}

	@Test
	public void testGenerateValuesZeroCountIngredientDependency() {
		mapper.setValueBefore("a", 2.0);
		mapper.setValueBefore("b", 3.0);
		mapper.setValueBefore("notConsume1", 1.0);

		HashMap<String, Integer> ingredients = new HashMap<>();
		ingredients.put("a", 1);
		ingredients.put("b", 1);
		ingredients.put("notConsume1", 0);
		mapper.addConversion(1, "c1", ingredients);

		ingredients.remove("notConsume1");
		ingredients.put("notConsume2", 0);
		mapper.addConversion(1, "c2", ingredients);

		Map<String, Double> values = mapper.generateValues();
		assertEquals(2, getValue(values, "a"), 1e-7);
		assertEquals(3, getValue(values, "b"), 1e-7);
		assertEquals(1, getValue(values, "notConsume1"), 1e-7);
		assertEquals(0, getValue(values, "notConsume2"), 1e-7);
		assertEquals(5, getValue(values, "c1"), 1e-7);
		assertEquals(5, getValue(values, "c2"), 1e-7);
	}

	@Test
	public void testGenerateValuesFreeAlternatives() {
		mapper.setValueBefore("freeWater", -Double.MAX_VALUE); // 'Free'
		mapper.setValueBefore("waterBottle", 0.0);
		mapper.addConversion(1, "waterGroup", Collections.singletonList("freeWater"));
		mapper.addConversion(1, "waterGroup", Collections.singletonList("waterBottle"));
		mapper.setValueBefore("a", 3.0);
		mapper.addConversion(1, "result", Arrays.asList("a", "waterGroup"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(3, getValue(values, "a"), 1e-7);
		assertEquals(0, getValue(values, "freeWater"), 1e-7);
		assertEquals(0, getValue(values, "waterBottle"), 1e-7);
		assertEquals(0, getValue(values, "waterGroup"), 1e-7);
		assertEquals(3, getValue(values, "result"), 1e-7);
	}

	@Test
	public void testGenerateValuesFreeAlternativesWithNegativeIngredients() {
		mapper.setValueBefore("bucket", 768.0);
		mapper.setValueBefore("waterBucket", 768.0);
		mapper.setValueBefore("waterBottle", 0.0);

		Map<String, Integer> m = new HashMap<>();
		m.put("waterBucket", 1);
		m.put("bucket", -1);
		mapper.addConversion(1, "waterGroup", m);
		mapper.addConversion(1, "waterGroup", Collections.singletonList("waterBottle"));
		mapper.setValueBefore("a", 3.0);
		mapper.addConversion(1, "result", Arrays.asList("a", "waterGroup"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(3, getValue(values, "a"), 1e-7);
		assertEquals(768, getValue(values, "bucket"), 1e-7);
		assertEquals(768, getValue(values, "waterBucket"), 1e-7);
		assertEquals(0, getValue(values, "waterGroup"), 1e-7);
		assertEquals(3, getValue(values, "result"), 1e-7);
	}

	@Test
	public void testOverflowWithIngredients() {
		mapper.setValueBefore("a", Double.MAX_VALUE / 2);
		mapper.setValueBefore("b", Double.MAX_VALUE / 2);
		mapper.addConversion(1, "c", Arrays.asList("a", "b"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "a"), 1e-7);
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "b"), 1e-7);
		assertEquals(Double.MAX_VALUE, getValue(values, "c"), 1e-7);
	}

	@Test
	public void testOverflowWithAmount() {
		mapper.setValueBefore("a", Double.MAX_VALUE / 2);
		mapper.addConversion(3, "a", Collections.singletonList("something"));

		Map<String, Double> values = mapper.generateValues();
		assertEquals(Double.MAX_VALUE / 2, getValue(values, "a"), 1e-7);
	}

	@Test
	public void testOverwriteConversions() {
		mapper.setValueBefore("a", 1.0);
		mapper.setValueFromConversion(1, "b", Collections.nCopies(3, "a"));
		mapper.addConversion(1, "b", Collections.singletonList("a"));
		mapper.addConversion(1, "c", Collections.nCopies(2, "b"));

		Map<String, Double> values = mapper.generateValues();
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
