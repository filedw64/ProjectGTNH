package moze_intel.projecte.emc;

import moze_intel.projecte.PECore;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;
import moze_intel.projecte.emc.mappers.APICustomConversionMapper;
import moze_intel.projecte.emc.mappers.APICustomEMCMapper;
import moze_intel.projecte.emc.mappers.CraftingMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.emc.mappers.FluidMapper;
import moze_intel.projecte.emc.mappers.IEMCMapper;
import moze_intel.projecte.emc.mappers.IntegrationMapper;
import moze_intel.projecte.emc.mappers.LazyMapper;
import moze_intel.projecte.emc.mappers.OreDictionaryMapper;
import moze_intel.projecte.emc.mappers.SmeltingMapper;
import moze_intel.projecte.emc.mappers.customConversions.CustomConversionMapper;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.PrefixConfiguration;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EMCMapper
{
	public static boolean enableNBTprocess = true;
	public static Map<SimpleStack, Double> emc = new HashMap<>();

	/** Retained, compact index of the conversion graph. {@code null} means every incremental entry point degrades to a full {@link #map()}. */
	private static GraphIndex index;

	/** The user supplied values (custom_emc.cfg) that produced the currently active mapping. */
	private static Map<NormalizedSimpleStack, Double> appliedUserValues = new HashMap<>();

	private static long lastVerificationNanos;
	private static final long VERIFY_INTERVAL_NANOS = 60_000_000_000L;

	private EMCMapper() {}

	public static void map()
	{
		// 在开始映射前清空之前的记录
		clearMaps();

		IndexedGraphMapper mapper = new IndexedGraphMapper();
		collectMappings(mapper);

		PELogger.logInfo("Start to generate Values");
		long start = System.currentTimeMillis();
		Map<NormalizedSimpleStack, Double> rawValues = mapper.generateValuesRaw();
		PELogger.logInfo("EMC Values Generated! (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		retainGraphIndex(mapper, rawValues);

		// 复用 generateValues() 的收尾语义：free / zero 条目不出现在最终映射里
		Map<NormalizedSimpleStack, Double> graphMapperValues = new HashMap<>(rawValues);
		graphMapperValues.entrySet().removeIf(entry -> DoubleArithmetic.INSTANCE.isFree(entry.getValue())
			|| DoubleArithmetic.INSTANCE.isZero(entry.getValue()));
		filterEMCMap(graphMapperValues);

		graphMapperValues.forEach((nss, val) -> {
			SimpleStack key = toEmcKey(nss);
			if (key != null)
				emc.put(key, val);
		});

		NormalizedSimpleStack.clearMap();
		postRemapEvents();
	}

	/**
	 * Runs every mapper against {@code mapper}. Shared by the regular full remap and the (debug only) verification run.
	 */
	private static void collectMappings(IndexedGraphMapper mapper)
	{
		List<IEMCMapper<NormalizedSimpleStack, Double>> emcMappers = Arrays.asList(
			new OreDictionaryMapper(),
			new LazyMapper(),
			APICustomEMCMapper.instance,
			new CustomConversionMapper(),
			new CustomEMCMapper(!mapper.verification),
			new CraftingMapper(),
			new FluidMapper(),
			new SmeltingMapper(),
			new APICustomConversionMapper(),
			new IntegrationMapper()
		);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

		enableNBTprocess = config.getBoolean("enableNBTprocess", "general", true, "Process items that have different NBT tags as different items.");

		PELogger.logInfo("Start to collect Mappings");
		for (IEMCMapper<NormalizedSimpleStack, Double> emcMapper : emcMappers) {
			if (!config.getBoolean(emcMapper.getName(), "enabledMappers", emcMapper.isAvailable(), emcMapper.getDescription()) || !emcMapper.isAvailable())
				continue;
			mapper.collectingCustom = emcMapper instanceof CustomEMCMapper;
			if (mapper.collectingCustom) mapper.customEnabled = true;
			else if (mapper.customEnabled) mapper.afterCustom = true;
			long start = System.currentTimeMillis();
			try {
				emcMapper.addMappings(mapper, new PrefixConfiguration(config, "mapperConfigurations." + emcMapper.getName()));
			}
			catch (Exception e) {
				PELogger.logFatal("Exception during Mapping Collection from Mapper %s. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!", emcMapper.getClass().getName());
				throw new IllegalStateException("EMC mapping collection failed", e);
			}
			PELogger.logInfo("Collected Mappings from %s. (took %.3fs)", emcMapper.getClass().getName(), (System.currentTimeMillis() - start) / 1e3);
		}

		mapper.collectingCustom = false;
		long start = System.currentTimeMillis();
		NormalizedSimpleStack.addMappings(mapper);
		PELogger.logInfo("Collected Mappings from NormalizedSimpleStack. (took %.3fs)", (System.currentTimeMillis() - start) / 1e3);

		PELogger.logInfo("Mapping Collection finished");
		mapper.finishCollection();

		if (!mapper.verification) config.save();
	}

	private static void retainGraphIndex(IndexedGraphMapper mapper, Map<NormalizedSimpleStack, Double> rawValues)
	{
		GraphIndex built = null;
		try {
			built = mapper.buildIndex(rawValues, CustomEMCParser.userValues);
			if (built == null || built.nodes.length == 0 || built.convOutput.length == 0) {
				PELogger.logWarn("EMC graph index is empty, incremental EMC updates are disabled until the next full remap.");
				built = null;
			}
		}
		catch (Throwable t) {
			PELogger.logWarn("Could not build the EMC graph index, incremental EMC updates are disabled until the next full remap: %s", t);
			t.printStackTrace();
			built = null;
		}

		index = built;
		appliedUserValues = new HashMap<>(CustomEMCParser.userValues);

		if (built != null) {
			built.refreshFixed(CustomEMCParser.userValues);
			PELogger.logInfo("EMC graph index retained: %d nodes, %d conversions, ~%.1f MB",
				built.nodes.length, built.convOutput.length, built.estimatedBytes() / (1024.0 * 1024.0));
		}
	}

	private static void postRemapEvents()
	{
		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	/**
	 * Remove all entrys from the map, that are not {@link NormalizedSimpleStack.NSSItem} or {@link NormalizedSimpleStack.NSSFluid},
	 * have a value <= 0 or WILDCARD_VALUE as metadata.
	 */
	static void filterEMCMap(Map<NormalizedSimpleStack, Double> map) {
		// 使用 entrySet() 代替 keySet()，避免重复寻址查询
		// 接管之前 DoubleGenerator 负责的 <= 0 过滤，原地剔除，不产生新的 HashMap
		map.entrySet().removeIf(entry -> entry.getValue() <= 0 || !isEmcKey(entry.getKey()));
	}

	/** Nodes that the public {@code emc} map can hold at all. */
	private static boolean isEmcKey(NormalizedSimpleStack nss)
	{
		if (nss instanceof NormalizedSimpleStack.NSSItem nssItem)
			return nssItem.damage != OreDictionary.WILDCARD_VALUE;
		return nss instanceof NormalizedSimpleStack.NSSFluid;
	}

	/** Mirrors the (single) normalised-stack to {@link SimpleStack} conversion used by {@link #map()}. */
	private static SimpleStack toEmcKey(NormalizedSimpleStack nss)
	{
		if (nss instanceof NormalizedSimpleStack.NSSItem nssItem) {
			Object obj = Item.itemRegistry.getObject(nssItem.itemName);
			int id = Item.itemRegistry.getIDForObject(obj);
			if (id == -1) {
				// 还是做一下判断，万一呢
				PELogger.logWarn("Item not found in registry for NSSItem: %s. Skipping...", nssItem.itemName);
				return null;
			}
			if (nss instanceof NormalizedSimpleStack.NBTNSSItem nbtnssItem)
				return new NBTSimpleStack(id, nbtnssItem.damage, nbtnssItem.nbt);
			return new SimpleStack(id, nssItem.damage);
		}
		if (nss instanceof NormalizedSimpleStack.NSSFluid nssFluid)
			return new FluidSimpleStack(nssFluid.fluid.getID());
		return null;
	}

	// ------------------------------------------------------------------------------------------------
	// Incremental re-evaluation
	// ------------------------------------------------------------------------------------------------

	/**
	 * Re-evaluates only the downstream closure of {@code changedNodes} instead of remapping everything.
	 * On any failure the previous {@code emc} content is restored, so a {@code false} result always means "nothing changed".
	 */
	public static boolean applyIncremental(Collection<NormalizedSimpleStack> changedNodes)
	{
		if (changedNodes == null || changedNodes.isEmpty())
			return true;

		Set<NormalizedSimpleStack> changed = new LinkedHashSet<>(changedNodes);
		GraphIndex idx = index;
		if (idx == null) {
			PELogger.logInfo("[degenerate] Graph index unavailable; requesting full EMC remap.");
			return fullRemapWithRollback("index unavailable");
		}

		for (NormalizedSimpleStack nss : changed) {
			if (idx.ids.get(nss) == null) {
				// 索引里没有这个节点（例如完全没有配方、也从未锁定过的物品），增量无从下手
				PELogger.logInfo("[degenerate] Node %s outside retained graph; requesting full EMC remap.", nss);
				return fullRemapWithRollback("node outside the graph index");
			}
		}

		Map<SimpleStack, Double> emcSnapshot = new HashMap<>(emc);
		Map<NormalizedSimpleStack, Double> userSnapshot = appliedUserValues;
		boolean[] hasFixedSnapshot = idx.hasFixed.clone();
		double[] fixedValueSnapshot = idx.fixedValue.clone();
		double[] valuesSnapshot = idx.values.clone();

		try {
			idx.refreshFixed(CustomEMCParser.userValues);
			appliedUserValues = new HashMap<>(CustomEMCParser.userValues);

			IncrementalEMCResolver.Result result = IncrementalEMCResolver.resolve(idx, changed);
			if (!result.success) {
				System.arraycopy(hasFixedSnapshot, 0, idx.hasFixed, 0, hasFixedSnapshot.length);
				System.arraycopy(fixedValueSnapshot, 0, idx.fixedValue, 0, fixedValueSnapshot.length);
				System.arraycopy(valuesSnapshot, 0, idx.values, 0, valuesSnapshot.length);
				appliedUserValues = userSnapshot;
				return fullRemapWithRollback(result.failureReason);
			}

			Set<Integer> publishNodes = new LinkedHashSet<>();
			for (int nodeId : result.changedNodes) publishNodes.add(nodeId);
			// A user write can remove/restore valueAfter without changing the propagation value.
			for (NormalizedSimpleStack seed : changed) publishNodes.add(idx.ids.get(seed));
			int updated = 0;
			for (int nodeId : publishNodes) {
				NormalizedSimpleStack nss = idx.nodes[nodeId];
				double value = idx.finalValue(nodeId, CustomEMCParser.userValues);
				SimpleStack key = toEmcKey(nss);
				if (key == null || !isEmcKey(nss))
					continue;
				if (value > 0) {
					emc.put(key, value);
					updated++;
				}
				else if (emc.remove(key) != null)
					updated++;
			}

			postRemapEvents();
			PELogger.logInfo("Applied incremental EMC update: %d nodes recomputed in topological order, %d values changed.",
				result.scannedNodes, updated);

			long now = System.nanoTime();
			if (ProjectEConfig.verifyIncrementalEMC && (lastVerificationNanos == 0
				|| now - lastVerificationNanos >= VERIFY_INTERVAL_NANOS)) {
				lastVerificationNanos = now;
				if (!verifyIncremental()) throw new IllegalStateException("Incremental EMC verification failed");
			}

			return true;
		}
		catch (Throwable t) {
			PELogger.logFatal("[failed] Incremental EMC update failed, rolling back to the previous values: %s", t);
			t.printStackTrace();
			emc.clear();
			emc.putAll(emcSnapshot);
			appliedUserValues = userSnapshot;
			CustomEMCParser.userValues = new HashMap<>(userSnapshot);
			System.arraycopy(hasFixedSnapshot, 0, idx.hasFixed, 0, hasFixedSnapshot.length);
			System.arraycopy(fixedValueSnapshot, 0, idx.fixedValue, 0, fixedValueSnapshot.length);
			System.arraycopy(valuesSnapshot, 0, idx.values, 0, valuesSnapshot.length);
			// 派生缓存（tome knowledge / fuel）可能已经读到过新值，用回滚后的 emc 再刷一次，尽力而为
			try {
				Transmutation.cacheFullKnowledge();
				FuelMapper.loadMap();
			}
			catch (Throwable ignored) {
			}
			return false;
		}
	}

	/** Rereads {@code custom_emc.cfg} and incrementally applies the user values that changed. */
	public static boolean reloadCustomEmc()
	{
		Map<NormalizedSimpleStack, Double> previous = appliedUserValues;
		try {
			CustomEMCParser.readUserData();
		}
		catch (RuntimeException e) {
			PELogger.logWarn("Could not reload custom EMC data; previous values retained: %s", e);
			return false;
		}
		Map<NormalizedSimpleStack, Double> current = CustomEMCParser.userValues;

		List<NormalizedSimpleStack> changed = new ArrayList<>();
		for (Map.Entry<NormalizedSimpleStack, Double> entry : current.entrySet()) {
			Double old = previous.get(entry.getKey());
			if (old == null || Double.compare(old, entry.getValue()) != 0)
				changed.add(entry.getKey());
		}
		for (NormalizedSimpleStack nss : previous.keySet()) {
			if (!current.containsKey(nss))
				changed.add(nss);
		}

		if (changed.isEmpty())
			return true; // 幂等：文件没带来任何变化

		boolean success = applyIncremental(changed);
		if (!success) CustomEMCParser.userValues = new HashMap<>(previous);
		return success;
	}

	/**
	 * Debug helper ({@code verifyIncrementalEMC}): recomputes the whole mapping on a throw-away mapper and diffs it against
	 * the values that the incremental path produced. The live {@code emc} map / graph index are not touched.
	 */
	public static boolean verifyIncremental()
	{
		if (index == null) {
			PELogger.logWarn("verifyIncrementalEMC: no retained EMC graph index, skipping the verification run.");
			return false;
		}

		Map<SimpleStack, Double> expected = null;
		boolean nbtSnapshot = enableNBTprocess;
		try {
			IndexedGraphMapper verifier = new IndexedGraphMapper();
			verifier.verification = true;
			collectMappings(verifier);
			Map<NormalizedSimpleStack, Double> full = verifier.generateValues();
			expected = new HashMap<>();
			for (Map.Entry<NormalizedSimpleStack, Double> entry : full.entrySet()) {
				if (entry.getValue() <= 0 || !isEmcKey(entry.getKey()))
					continue;
				SimpleStack key = toEmcKey(entry.getKey());
				if (key != null)
					expected.put(key, entry.getValue());
			}
		}
		catch (Throwable t) {
			PELogger.logWarn("verifyIncrementalEMC: the full remap used for the comparison failed: %s", t);
			t.printStackTrace();
			expected = null;
		}
		finally {
			enableNBTprocess = nbtSnapshot;
			// the verification run repopulated the normalised stack pools; leave them the way map() does
			NormalizedSimpleStack.clearMap();
		}

		if (expected == null)
			return false;

		Set<SimpleStack> keys = new HashSet<>(expected.keySet());
		keys.addAll(emc.keySet());

		int differences = 0;
		List<String> details = new ArrayList<>();
		for (SimpleStack key : keys) {
			Double incremental = emc.get(key);
			Double full = expected.get(key);
			boolean same = incremental == null ? full == null : full != null && Double.compare(incremental, full) == 0;
			if (same)
				continue;
			differences++;
			if (details.size() < 20)
				details.add(key + ": incremental=" + incremental + ", full remap=" + full);
		}

		if (differences == 0) {
			PELogger.logInfo("verifyIncrementalEMC: incremental result matches the full remap for all %d entries.", keys.size());
			return true;
		}

		PELogger.logWarn("verifyIncrementalEMC: %d of %d entries differ between the incremental result and a full remap (showing at most 20):",
			differences, keys.size());
		for (String detail : details)
			PELogger.logWarn("verifyIncrementalEMC: %s", detail);
		return false;
	}

	/**
	 * Last resort path: rebuild everything. Restores the previous {@code emc} content if the remap blows up.
	 */
	private static boolean fullRemapWithRollback(String reason)
	{
		Map<SimpleStack, Double> emcSnapshot = new HashMap<>(emc);
		Map<NormalizedSimpleStack, Double> userSnapshot = appliedUserValues;
		GraphIndex indexSnapshot = index;
		boolean nbtSnapshot = enableNBTprocess;
		try {
			PELogger.logInfo("[degenerate] Starting full EMC remap: %s", reason);
			CustomEMCParser.readUserData();
			map();
			PELogger.logInfo("[degenerate] Full EMC remap applied successfully: %s", reason);
			return true;
		}
		catch (Throwable t) {
			PELogger.logFatal("[failed] Full EMC remap (%s) failed, restoring the previous values: %s", reason, t);
			t.printStackTrace();
			emc.clear();
			emc.putAll(emcSnapshot);
			appliedUserValues = userSnapshot;
			CustomEMCParser.userValues = new HashMap<>(userSnapshot);
			index = indexSnapshot;
			enableNBTprocess = nbtSnapshot;
			NormalizedSimpleStack.clearMap();
			try {
				Transmutation.cacheFullKnowledge();
				FuelMapper.loadMap();
			}
			catch (Throwable ignored) {
			}
			return false;
		}
	}

	public static boolean mapContains(SimpleStack key) {
		return emc.containsKey(key);
	}

	public static Double getEmcValue(SimpleStack stack) {
		return emc.get(stack);
	}

	public static void clearMaps() {
		emc.clear();
		index = null;
		lastVerificationNanos = 0;
	}

	// ------------------------------------------------------------------------------------------------
	// Retained graph index
	// ------------------------------------------------------------------------------------------------

	/**
	 * Compact, array based snapshot of the collected conversion graph.
	 * Holds no {@code Conversion} objects: only primitive arrays plus the node table.
	 */
	static final class GraphIndex
	{
		boolean customEnabled;
		NormalizedSimpleStack[] nodes;
		Map<NormalizedSimpleStack, Integer> ids;

		/** Flat conversion storage. */
		int[] convOutput;
		int[] convOutputCount;
		int[] convStart;    // per conversion, offset into convIng / convCount (length conversions + 1)
		int[] convIng;
		int[] convCount;
		boolean[] convHasNegative;
		double[] convValue;

		/** Conversions per output node (CSR). */
		int[] convOfStart;
		int[] convOfIdx;

		/** Reverse index: for an ingredient node, the conversions that consume it (CSR). */
		int[] depStart;
		int[] depConv;

		/** valueBefore as written by the mappers only (never by custom_emc.cfg). */
		double[] baseFixedValue;
		boolean[] baseFixed;

		/** {@link #baseFixed} with the user overrides of the current custom_emc.cfg applied on top. */
		double[] fixedValue;
		boolean[] hasFixed;

		/**
		 * Nodes whose custom_emc.cfg value was overwritten by a mapper that runs after CustomEMCMapper
		 * (CraftingMapper's GT null tools, integration mappers). The full solver keeps the mapper value, so the user value
		 * must not be layered on top of it again.
		 */
		boolean[] ignoreUserValue;

		/** Mapper valueAfter overrides, applied only when publishing, never as recipe inputs. */
		double[] forcedValue;
		boolean[] hasForced;
		boolean[] afterUserValue;

		/** setValueFromConversion target, -1 when the node is not forced to a single conversion. */
		int[] forcedConv;

		/** Propagation-stage value per node, before valueAfter; NaN when absent. */
		double[] values;

		private GraphIndex() {}

		static GraphIndex allocate(int nodeCount, int conversionCount, int ingredientCount, int dependentCount)
		{
			GraphIndex idx = new GraphIndex();
			idx.nodes = new NormalizedSimpleStack[nodeCount];
			idx.convOutput = new int[conversionCount];
			idx.convOutputCount = new int[conversionCount];
			idx.convStart = new int[conversionCount + 1];
			idx.convIng = new int[ingredientCount];
			idx.convCount = new int[ingredientCount];
			idx.convHasNegative = new boolean[conversionCount];
			idx.convValue = new double[conversionCount];
			idx.convOfStart = new int[nodeCount + 1];
			idx.convOfIdx = new int[conversionCount];
			idx.depStart = new int[nodeCount + 1];
			idx.depConv = new int[dependentCount];
			idx.baseFixedValue = new double[nodeCount];
			idx.baseFixed = new boolean[nodeCount];
			idx.fixedValue = new double[nodeCount];
			idx.hasFixed = new boolean[nodeCount];
			idx.ignoreUserValue = new boolean[nodeCount];
			idx.forcedValue = new double[nodeCount];
			idx.hasForced = new boolean[nodeCount];
			idx.afterUserValue = new boolean[nodeCount];
			idx.forcedConv = new int[nodeCount];
			Arrays.fill(idx.forcedConv, -1);
			idx.values = new double[nodeCount];
			Arrays.fill(idx.values, Double.NaN);
			return idx;
		}

		long estimatedBytes()
		{
			int n = nodes.length;
			long bytes = 0;
			bytes += 8L * n * 6;                        // nodes + values + baseFixedValue + fixedValue + forcedValue (+ slack)
			bytes += 5L * n;                            // boolean arrays
			bytes += 4L * n;                            // forcedConv
			bytes += 4L * convOfStart.length + 4L * convOfIdx.length + 4L * convStart.length;
			bytes += convHasNegative.length;
			bytes += 4L * convOutput.length + 4L * convOutputCount.length + 8L * convValue.length;
			bytes += 4L * convIng.length + 4L * convCount.length;
			bytes += 4L * depStart.length + 4L * depConv.length;
			bytes += 48L * n;                           // rough guess for the ids HashMap
			return bytes;
		}

		/**
		 * {@link #hasFixed} / {@link #fixedValue} = mapper locking (already user free) with the current user values on top.
		 * A user value that disappeared from custom_emc.cfg therefore falls back to the mapper locking instead of losing its value.
		 */
		/** Custom setValueBefore removes earlier valueAfter; later mapper writes take precedence. */
		double finalValue(int node, Map<NormalizedSimpleStack, Double> userValues)
		{
			boolean userSuppressesAfter = customEnabled && userValues.containsKey(nodes[node])
				&& !ignoreUserValue[node] && !afterUserValue[node];
			return hasForced[node] && !userSuppressesAfter ? forcedValue[node] : values[node];
		}

		void refreshFixed(Map<NormalizedSimpleStack, Double> userValues)
		{
			System.arraycopy(baseFixed, 0, hasFixed, 0, baseFixed.length);
			System.arraycopy(baseFixedValue, 0, fixedValue, 0, baseFixedValue.length);

			if (customEnabled && userValues != null) {
				for (Map.Entry<NormalizedSimpleStack, Double> entry : userValues.entrySet()) {
					Integer id = ids.get(entry.getKey());
					if (id == null)
						continue;
					if (ignoreUserValue[id])
						continue; // 这个节点的用户值当年就被后置 mapper 覆盖了，全量重算同样不会采用它
					hasFixed[id] = true;
					fixedValue[id] = entry.getValue();
				}
			}
		}
	}

	/**
	 * {@link SimpleGraphMapper} subclass that additionally knows how to turn the collected conversions into a {@link GraphIndex}.
	 * Lives here so the protected collector structures stay protected.
	 */
	private static final class IndexedGraphMapper extends SimpleGraphMapper<NormalizedSimpleStack, Double>
	{
		/** Track writes rather than infer ownership from equal numeric values. */
		final Map<NormalizedSimpleStack, Double> mapperLocks = new HashMap<>();
		final Set<NormalizedSimpleStack> lateLocks = new HashSet<>();
		boolean verification;
		boolean collectingCustom;
		boolean afterCustom;
		boolean customEnabled;
		final Map<NormalizedSimpleStack, Double> mapperAfter = new HashMap<>();
		final Set<NormalizedSimpleStack> lateAfter = new HashSet<>();
		Map<NormalizedSimpleStack, Double> propagationValues;

		@Override
		public void setValueBefore(NormalizedSimpleStack stack, Double value)
		{
			super.setValueBefore(stack, value);
			if (stack != null && !collectingCustom) {
				mapperLocks.put(stack, value);
				mapperAfter.remove(stack);
				lateAfter.remove(stack);
				if (afterCustom) lateLocks.add(stack);
			}
		}

		@Override
		public void setValueAfter(NormalizedSimpleStack stack, Double value)
		{
			super.setValueAfter(stack, value);
			if (stack != null && !collectingCustom) {
				mapperAfter.put(stack, value);
				if (afterCustom) lateAfter.add(stack);
			}
		}

		@Override
		public Map<NormalizedSimpleStack, Double> generateValuesRaw()
		{
			propagationValues = generatePropagationValues();
			Map<NormalizedSimpleStack, Double> published = new HashMap<>(propagationValues);
			published.putAll(valueAfter);
			return published;
		}

		IndexedGraphMapper()
		{
			super(DoubleArithmetic.INSTANCE);
		}

		GraphIndex buildIndex(Map<NormalizedSimpleStack, Double> generatedValues, Map<NormalizedSimpleStack, Double> userValues)
		{
			List<Conversion> conversions = new ArrayList<>();
			Map<Conversion, Integer> conversionIds = new IdentityHashMap<>();

			for (Set<Conversion> set : conversionsFor.values())
				for (Conversion conv : set)
					idOf(conversions, conversionIds, conv);
			for (Conversion conv : overwriteConversion.values())
				idOf(conversions, conversionIds, conv);
			for (Set<Conversion> set : usedIn.values())
				for (Conversion conv : set)
					idOf(conversions, conversionIds, conv);

			List<NormalizedSimpleStack> nodeList = new ArrayList<>();
			Map<NormalizedSimpleStack, Integer> nodeIds = new HashMap<>();
			for (Conversion conv : conversions) {
				idOf(nodeList, nodeIds, conv.output);
				if (conv.ingredientCounts == null)
					continue;
				for (NormalizedSimpleStack ingredient : conv.ingredientCounts.keySet())
					idOf(nodeList, nodeIds, ingredient);
			}
			for (NormalizedSimpleStack nss : valueBefore.keySet())
				idOf(nodeList, nodeIds, nss);
			for (NormalizedSimpleStack nss : mapperAfter.keySet())
				idOf(nodeList, nodeIds, nss);
			for (NormalizedSimpleStack nss : valueAfter.keySet())
				idOf(nodeList, nodeIds, nss);
			for (NormalizedSimpleStack nss : overwriteConversion.keySet())
				idOf(nodeList, nodeIds, nss);

			int nodeCount = nodeList.size();
			int conversionCount = conversions.size();
			int ingredientCount = 0;
			for (Conversion conv : conversions)
				ingredientCount += conv.ingredientCounts == null ? 0 : conv.ingredientCounts.size();

			int[] dependentCounts = new int[nodeCount];
			int dependentCount = 0;
			for (Map.Entry<NormalizedSimpleStack, Set<Conversion>> entry : usedIn.entrySet()) {
				Integer nodeId = nodeIds.get(entry.getKey());
				if (nodeId == null)
					continue;
				for (Conversion conv : entry.getValue()) {
					if (!conversionIds.containsKey(conv))
						continue;
					dependentCounts[nodeId]++;
					dependentCount++;
				}
			}

			GraphIndex idx = GraphIndex.allocate(nodeCount, conversionCount, ingredientCount, dependentCount);
			idx.customEnabled = customEnabled;
			idx.ids = nodeIds; // nodeIds values are exactly the nodeList indexes

			for (int i = 0; i < nodeCount; i++) {
				NormalizedSimpleStack nss = nodeList.get(i);
				idx.nodes[i] = nss;

				Double generated = propagationValues.get(nss);
				idx.values[i] = generated == null ? Double.NaN : generated;

				Double mapperValue = mapperLocks.get(nss);
				if (mapperValue != null) {
					idx.baseFixed[i] = true;
					idx.baseFixedValue[i] = mapperValue;
				}
				idx.ignoreUserValue[i] = lateLocks.contains(nss);
				idx.afterUserValue[i] = lateAfter.contains(nss);
				Double after = mapperAfter.get(nss);
				if (after != null) {
					idx.hasForced[i] = true;
					idx.forcedValue[i] = after;
				}
				Conversion forced = overwriteConversion.get(nss);
				if (forced != null) {
					Integer forcedId = conversionIds.get(forced);
					idx.forcedConv[i] = forcedId == null ? -1 : forcedId;
				}
			}

			int cursor = 0;
			for (int i = 0; i < conversionCount; i++) {
				Conversion conv = conversions.get(i);
				idx.convStart[i] = cursor;
				idx.convOutput[i] = nodeIds.get(conv.output);
				idx.convOutputCount[i] = conv.outputCount;
				idx.convValue[i] = conv.value == null ? 0.0 : conv.value;
				if (conv.ingredientCounts == null)
					continue;
				for (Map.Entry<NormalizedSimpleStack, Integer> entry : conv.ingredientCounts.entrySet()) {
					idx.convIng[cursor] = nodeIds.get(entry.getKey());
					idx.convCount[cursor] = entry.getValue() == null ? 0 : entry.getValue();
					idx.convHasNegative[i] |= idx.convCount[cursor] < 0;
					cursor++;
				}
			}
			idx.convStart[conversionCount] = cursor;

			int[] cursorPerNode = new int[nodeCount];
			int running = 0;
			for (int i = 0; i < conversionCount; i++)
				cursorPerNode[idx.convOutput[i]]++;
			for (int i = 0; i < nodeCount; i++) {
				idx.convOfStart[i] = running;
				running += cursorPerNode[i];
			}
			idx.convOfStart[nodeCount] = running;
			Arrays.fill(cursorPerNode, 0);
			for (int i = 0; i < conversionCount; i++) {
				int node = idx.convOutput[i];
				idx.convOfIdx[idx.convOfStart[node] + cursorPerNode[node]++] = i;
			}

			running = 0;
			for (int i = 0; i < nodeCount; i++) {
				idx.depStart[i] = running;
				running += dependentCounts[i];
			}
			idx.depStart[nodeCount] = running;
			if (running != dependentCount)
				throw new IllegalStateException("dependent index mismatch: " + running + " != " + dependentCount);
			Arrays.fill(cursorPerNode, 0);
			for (Map.Entry<NormalizedSimpleStack, Set<Conversion>> entry : usedIn.entrySet()) {
				Integer nodeId = nodeIds.get(entry.getKey());
				if (nodeId == null)
					continue;
				for (Conversion conv : entry.getValue()) {
					Integer conversionId = conversionIds.get(conv);
					if (conversionId == null)
						continue;
					idx.depConv[idx.depStart[nodeId] + cursorPerNode[nodeId]++] = conversionId;
				}
			}

			return idx;
		}

		private static int idOf(List<Conversion> conversions, Map<Conversion, Integer> ids, Conversion conv)
		{
			Integer known = ids.get(conv);
			if (known != null)
				return known;
			int id = conversions.size();
			conversions.add(conv);
			ids.put(conv, id);
			return id;
		}

		private static int idOf(List<NormalizedSimpleStack> nodes, Map<NormalizedSimpleStack, Integer> ids, NormalizedSimpleStack nss)
		{
			Integer known = ids.get(nss);
			if (known != null)
				return known;
			int id = nodes.size();
			nodes.add(nss);
			ids.put(nss, id);
			return id;
		}
	}
}
