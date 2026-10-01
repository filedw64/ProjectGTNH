package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryManager {
	private static final int MAX_HISTORY = 50; // 搜索频繁，容量给大点
	private static final List<String> HISTORY = new ArrayList<>(MAX_HISTORY + 1);

	private static int historyIndex = -1;
	private static String tempInput = "";

	public static synchronized void addHistory(String query) {
		if (query == null) return;
		query = query.trim();
		if (query.isEmpty()) return;

		if (!HISTORY.isEmpty() && HISTORY.get(HISTORY.size() - 1).equalsIgnoreCase(query)) {
			resetCursor();
			return;
		}

		final String finalQuery = query;
		HISTORY.removeIf(s -> s.equalsIgnoreCase(finalQuery));
		HISTORY.add(query);

		if (HISTORY.size() > MAX_HISTORY) {
			HISTORY.remove(0);
		}
		resetCursor();
	}

	public static synchronized void resetCursor() {
		historyIndex = -1;
		tempInput = "";
	}

	public static synchronized String navigateUp(String currentInput) {
		if (HISTORY.isEmpty()) return currentInput != null ? currentInput : "";

		if (historyIndex == -1) {
			tempInput = currentInput != null ? currentInput : "";
			historyIndex = HISTORY.size() - 1;
			return HISTORY.get(historyIndex);
		}
		if (historyIndex > 0) historyIndex--;

		return HISTORY.get(historyIndex);
	}

	public static synchronized String navigateDown(String currentInput) {
		if (HISTORY.isEmpty() || historyIndex == -1) return currentInput != null ? currentInput : "";

		if (historyIndex < HISTORY.size() - 1) {
			historyIndex++;
			return HISTORY.get(historyIndex);
		}
		resetCursor();
		return tempInput;
	}
}
