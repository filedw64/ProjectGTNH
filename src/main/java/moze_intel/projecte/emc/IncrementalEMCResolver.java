package moze_intel.projecte.emc;

import moze_intel.projecte.emc.arithmetics.DoubleArithmetic;

import java.util.ArrayDeque;
import java.util.Collection;

/** Recomputes an acyclic downstream closure from scratch; ambiguous graphs use the full solver. */
final class IncrementalEMCResolver
{
	private static final DoubleArithmetic ARITHMETIC = DoubleArithmetic.INSTANCE;
	private static final double FREE = ARITHMETIC.getFree();

	private IncrementalEMCResolver() {}

	static Result resolve(EMCMapper.GraphIndex index, Collection<NormalizedSimpleStack> changedNodes)
	{
		int n = index.nodes.length;
		boolean[] affected = new boolean[n];
		int[] closure = new int[n];
		int size = 0;
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (NormalizedSimpleStack stack : changedNodes) {
			Integer id = index.ids.get(stack);
			if (id == null)
				return Result.failure("node outside retained graph");
			if (!affected[id]) {
				affected[id] = true;
				closure[size++] = id;
				queue.add(id);
			}
		}
		while (!queue.isEmpty()) {
			int node = queue.remove();
			for (int p = index.depStart[node]; p < index.depStart[node + 1]; p++) {
				int c = index.depConv[p];
				int out = index.convOutput[c];
				if (!active(index, c) || affected[out])
					continue;
				affected[out] = true;
				closure[size++] = out;
				queue.add(out);
			}
		}

		// Count edges exactly as stored in the reverse index, including zero-count ingredients.
		// The full solver uses those edges as recipe triggers too.
		int[] incoming = new int[n];
		for (int i = 0; i < size; i++) {
			int node = closure[i];
			for (int p = index.depStart[node]; p < index.depStart[node + 1]; p++) {
				int c = index.depConv[p];
				if (active(index, c) && affected[index.convOutput[c]])
					incoming[index.convOutput[c]]++;
			}
			for (int p = index.convOfStart[node]; p < index.convOfStart[node + 1]; p++) {
				int c = index.convOfIdx[p];
				if (active(index, c) && index.convHasNegative[c])
					return Result.failure("negative ingredient counts require the full solver");
			}
		}
		for (int i = 0; i < size; i++)
			if (incoming[closure[i]] == 0) queue.add(closure[i]);
		int[] order = new int[size];
		int count = 0;
		while (!queue.isEmpty()) {
			int node = queue.remove();
			order[count++] = node;
			for (int p = index.depStart[node]; p < index.depStart[node + 1]; p++) {
				int c = index.depConv[p];
				int out = index.convOutput[c];
				if (active(index, c) && affected[out] && --incoming[out] == 0)
					queue.add(out);
			}
		}
		if (count != size)
			return Result.failure("cyclic downstream closure requires the full solver");

		int[] changed = new int[size];
		int changedCount = 0;
		for (int node : order) {
			double value = index.hasFixed[node] ? index.fixedValue[node] : computeValue(index, node);
			double old = index.values[node];
			if (!(Double.isNaN(old) && Double.isNaN(value)) && old != value)
				changed[changedCount++] = node;
			index.values[node] = value;
		}
		return new Result(true, null, java.util.Arrays.copyOf(changed, changedCount), size);
	}

	private static boolean active(EMCMapper.GraphIndex index, int c)
	{
		int output = index.convOutput[c];
		return !index.hasFixed[output] && (index.forcedConv[output] < 0 || index.forcedConv[output] == c);
	}

	private static double computeValue(EMCMapper.GraphIndex index, int node)
	{
		double best = Double.NaN;
		for (int p = index.convOfStart[node]; p < index.convOfStart[node + 1]; p++) {
			int c = index.convOfIdx[p];
			if (!active(index, c)) continue;
			// Empty recipes are never triggered by the full solver's ingredient queue.
			boolean triggered = false;
			for (int q = index.convStart[c]; q < index.convStart[c + 1]; q++)
				triggered |= !Double.isNaN(index.values[index.convIng[q]]);
			if (!triggered) continue;
			double value = index.convValue[c];
			boolean allFree = true;
			boolean positive = false;
			boolean valid = true;
			for (int q = index.convStart[c]; q < index.convStart[c + 1]; q++) {
				int amount = index.convCount[q];
				if (amount == 0) continue;
				double ingredient = index.values[index.convIng[q]];
				if (Double.isNaN(ingredient) || ingredient == 0) { valid = false; break; }
				if (ARITHMETIC.isFree(ingredient)) continue;
				double part = ARITHMETIC.mul(amount, ingredient);
				value = ARITHMETIC.add(value, part);
				positive |= part > 0 && amount > 0;
				allFree = false;
			}
			if (!valid) continue;
			if (allFree || (positive && value <= 0)) value = FREE;
			value = ARITHMETIC.div(value, index.convOutputCount[c]);
			if ((value > 0 || value == FREE) && (Double.isNaN(best) || value < best)) best = value;
		}
		return best;
	}

	static final class Result
	{
		final boolean success;
		final String failureReason;
		final int[] changedNodes;
		final int scannedNodes;

		private Result(boolean success, String failureReason, int[] changedNodes, int scannedNodes)
		{
			this.success = success;
			this.failureReason = failureReason;
			this.changedNodes = changedNodes;
			this.scannedNodes = scannedNodes;
		}

		static Result failure(String reason)
		{
			return new Result(false, reason, new int[0], 0);
		}
	}
}
