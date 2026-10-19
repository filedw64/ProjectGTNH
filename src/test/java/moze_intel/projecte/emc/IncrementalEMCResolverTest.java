package moze_intel.projecte.emc;

import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class IncrementalEMCResolverTest
{
	private static final class Node extends NormalizedSimpleStack {
		final String name;
		Node(String name) { this.name = name; }
		@Override public boolean equals(Object other) { return other instanceof Node && name.equals(((Node) other).name); }
		@Override public int hashCode() { return name.hashCode(); }
		@Override public String toString() { return name; }
		@Override public String json() { return name; }
	}

	@SuppressWarnings("unchecked")
	private static SimpleGraphMapper<NormalizedSimpleStack, Double> mapper() throws Exception {
		Class<?> type = Class.forName("moze_intel.projecte.emc.EMCMapper$IndexedGraphMapper");
		Constructor<?> constructor = type.getDeclaredConstructor();
		constructor.setAccessible(true);
		return (SimpleGraphMapper<NormalizedSimpleStack, Double>) constructor.newInstance();
	}

	private static void flag(Object mapper, String name, boolean value) throws Exception {
		Field field = mapper.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.setBoolean(mapper, value);
	}

	private static EMCMapper.GraphIndex index(SimpleGraphMapper<NormalizedSimpleStack, Double> mapper) throws Exception {
		Method method = mapper.getClass().getDeclaredMethod("buildIndex", Map.class, Map.class);
		method.setAccessible(true);
		EMCMapper.GraphIndex index = (EMCMapper.GraphIndex) method.invoke(mapper, mapper.generateValuesRaw(), Collections.emptyMap());
		index.refreshFixed(Collections.emptyMap());
		return index;
	}

	@Test public void raisesAndRemovesDownstreamValues() throws Exception {
		Node a = new Node("a"), b = new Node("b"), c = new Node("c");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 1.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 2));
		mapper.addConversion(1, c, Collections.singletonMap(b, 3));
		EMCMapper.GraphIndex index = index(mapper);
		int id = index.ids.get(a);
		index.fixedValue[id] = 10;
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		mapper.setValueBefore(a, 10.0);
		Map<NormalizedSimpleStack, Double> full = mapper.generateValuesRaw();
		for (Node node : Arrays.asList(a, b, c)) assertEquals(full.get(node), index.values[index.ids.get(node)], 0);
		index.hasFixed[id] = false;
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		for (Node node : Arrays.asList(a, b, c)) assertTrue(Double.isNaN(index.values[index.ids.get(node)]));
	}

	@Test public void cycleRequestsFullRemapWithoutMutation() throws Exception {
		Node a = new Node("a"), b = new Node("b"), c = new Node("c");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 1.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 1));
		mapper.addConversion(1, c, Collections.singletonMap(b, 1));
		mapper.addConversion(1, b, Collections.singletonMap(c, 1));
		EMCMapper.GraphIndex index = index(mapper);
		double[] before = index.values.clone();
		index.fixedValue[index.ids.get(a)] = 10;
		assertFalse(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertArrayEquals(before, index.values, 0);
	}

	@Test public void lateMapperLockWinsEvenWithoutInitialUserValue() throws Exception {
		Node a = new Node("tool");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		flag(mapper, "customEnabled", true);
		flag(mapper, "afterCustom", true);
		mapper.setValueBefore(a, 5.0);
		EMCMapper.GraphIndex index = index(mapper);
		index.refreshFixed(Collections.singletonMap(a, 99.0));
		assertEquals(5, index.fixedValue[index.ids.get(a)], 0);
	}

	@Test public void equalLateWriteStillRecordsOwnership() throws Exception {
		Node a = new Node("a");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 5.0);
		flag(mapper, "customEnabled", true);
		flag(mapper, "collectingCustom", true);
		mapper.setValueBefore(a, 5.0);
		flag(mapper, "collectingCustom", false);
		flag(mapper, "afterCustom", true);
		mapper.setValueBefore(a, 5.0);
		EMCMapper.GraphIndex index = index(mapper);
		index.refreshFixed(Collections.singletonMap(a, 12.0));
		assertEquals(5, index.fixedValue[index.ids.get(a)], 0);
	}

	@Test public void forcedAfterDoesNotPropagateOrDisableIncremental() throws Exception {
		Node a = new Node("a"), b = new Node("b"), c = new Node("c");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 1.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 2));
		mapper.addConversion(1, c, Collections.singletonMap(b, 3));
		mapper.setValueAfter(b, 100.0);
		EMCMapper.GraphIndex index = index(mapper);
		assertEquals(2, index.values[index.ids.get(b)], 0);
		assertEquals(100, index.finalValue(index.ids.get(b), Collections.emptyMap()), 0);
		assertEquals(6, index.values[index.ids.get(c)], 0);
		index.fixedValue[index.ids.get(a)] = 10;
		IncrementalEMCResolver.Result result = IncrementalEMCResolver.resolve(index, Collections.singleton(a));
		assertTrue(result.success);
		assertEquals(3, result.scannedNodes);
		mapper.setValueBefore(a, 10.0);
		Map<NormalizedSimpleStack, Double> full = mapper.generateValuesRaw();
		for (Node node : Arrays.asList(a, b, c))
			assertEquals(full.get(node), index.finalValue(index.ids.get(node), Collections.emptyMap()), 0);
	}

	@Test public void blacklistElsewhereDoesNotForceFullRemap() throws Exception {
		Node ore = new Node("ore"), a = new Node("a"), b = new Node("b");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(ore, 0.0);
		mapper.setValueAfter(ore, 0.0);
		mapper.setValueBefore(a, 2.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 3));
		EMCMapper.GraphIndex index = index(mapper);
		index.fixedValue[index.ids.get(a)] = 5;
		IncrementalEMCResolver.Result result = IncrementalEMCResolver.resolve(index, Collections.singleton(a));
		assertTrue(result.success);
		assertEquals(2, result.scannedNodes);
		assertEquals(15, index.finalValue(index.ids.get(b), Collections.emptyMap()), 0);
		assertEquals(0, index.finalValue(index.ids.get(ore), Collections.emptyMap()), 0);
	}

	@Test public void userValueRemovesEarlierAfterAndResetRestoresIt() throws Exception {
		Node a = new Node("a"), b = new Node("b");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 5.0);
		mapper.setValueAfter(a, 100.0);
		flag(mapper, "customEnabled", true);
		flag(mapper, "collectingCustom", true);
		mapper.setValueBefore(a, 5.0); // Equal propagation value still removes valueAfter.
		flag(mapper, "collectingCustom", false);
		mapper.addConversion(1, b, Collections.singletonMap(a, 2));
		EMCMapper.GraphIndex index = index(mapper);
		Map<NormalizedSimpleStack, Double> user = Collections.singletonMap(a, 5.0);
		index.refreshFixed(user);
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertEquals(5, index.finalValue(index.ids.get(a), user), 0);
		assertEquals(10, index.values[index.ids.get(b)], 0);
		index.refreshFixed(Collections.emptyMap());
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertEquals(100, index.finalValue(index.ids.get(a), Collections.emptyMap()), 0);
		assertEquals(10, index.values[index.ids.get(b)], 0);
	}

	@Test public void afterOnlyInputOutsideClosureNeverBecomesRecipeInput() throws Exception {
		Node a = new Node("a"), hidden = new Node("hidden"), b = new Node("b");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 1.0);
		mapper.setValueAfter(hidden, 100.0);
		Map<NormalizedSimpleStack, Integer> ingredients = new HashMap<>();
		ingredients.put(a, 1);
		ingredients.put(hidden, 1);
		mapper.addConversion(1, b, ingredients);
		EMCMapper.GraphIndex index = index(mapper);
		assertTrue(Double.isNaN(index.values[index.ids.get(hidden)]));
		index.fixedValue[index.ids.get(a)] = 5;
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertTrue(Double.isNaN(index.values[index.ids.get(b)]));
		assertEquals(100, index.finalValue(index.ids.get(hidden), Collections.emptyMap()), 0);
	}

	@Test public void lateAfterWinsOverUserValue() throws Exception {
		Node a = new Node("a"), b = new Node("b");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		flag(mapper, "customEnabled", true);
		flag(mapper, "collectingCustom", true);
		mapper.setValueBefore(a, 5.0);
		flag(mapper, "collectingCustom", false);
		flag(mapper, "afterCustom", true);
		mapper.setValueAfter(a, 100.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 2));
		EMCMapper.GraphIndex index = index(mapper);
		Map<NormalizedSimpleStack, Double> user = Collections.singletonMap(a, 7.0);
		index.refreshFixed(user);
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertEquals(100, index.finalValue(index.ids.get(a), user), 0);
		assertEquals(14, index.values[index.ids.get(b)], 0);
	}

	@Test public void fixedBoundaryStopsPropagationButChangedFixedSeedPropagates() throws Exception {
		Node a = new Node("a"), b = new Node("b"), c = new Node("c");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 1.0);
		mapper.setValueBefore(b, 5.0);
		mapper.addConversion(1, b, Collections.singletonMap(a, 1));
		mapper.addConversion(1, c, Collections.singletonMap(b, 2));
		EMCMapper.GraphIndex index = index(mapper);
		index.fixedValue[index.ids.get(a)] = 20;
		assertEquals(1, IncrementalEMCResolver.resolve(index, Collections.singleton(a)).scannedNodes);
		assertEquals(10, index.values[index.ids.get(c)], 0);
		index.fixedValue[index.ids.get(b)] = 8;
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(b)).success);
		assertEquals(16, index.values[index.ids.get(c)], 0);
	}

	@Test public void disabledCustomMapperDoesNotApplyOverrides() throws Exception {
		Node a = new Node("a");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 7.0);
		EMCMapper.GraphIndex index = index(mapper);
		index.refreshFixed(Collections.singletonMap(a, 99.0));
		assertEquals(7, index.fixedValue[index.ids.get(a)], 0);
	}

	@Test public void negativeIngredientRequestsFullSolverWithoutMutation() throws Exception {
		Node a = new Node("a"), b = new Node("b"), c = new Node("c");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 10.0);
		mapper.setValueBefore(b, 1.0);
		Map<NormalizedSimpleStack, Integer> ingredients = new HashMap<>();
		ingredients.put(a, 1);
		ingredients.put(b, -1);
		mapper.addConversion(1, c, ingredients);
		EMCMapper.GraphIndex index = index(mapper);
		double[] before = index.values.clone();
		index.fixedValue[index.ids.get(a)] = 20;
		assertFalse(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertArrayEquals(before, index.values, 0);
	}

	@Test public void freeIngredientUsesSharedArithmetic() throws Exception {
		Node a = new Node("a"), b = new Node("b");
		SimpleGraphMapper<NormalizedSimpleStack, Double> mapper = mapper();
		mapper.setValueBefore(a, 10.0);
		mapper.addConversion(2, b, Collections.singletonMap(a, 1));
		EMCMapper.GraphIndex index = index(mapper);
		index.fixedValue[index.ids.get(a)] = DoubleArithmetic.INSTANCE.getFree();
		assertTrue(IncrementalEMCResolver.resolve(index, Collections.singleton(a)).success);
		assertEquals(DoubleArithmetic.INSTANCE.getFree(), index.values[index.ids.get(b)], 0);
	}
}
