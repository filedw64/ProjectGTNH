package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-session search history; no player data or disk persistence. */
@SideOnly(Side.CLIENT)
public final class SearchHistoryManager {
    private static final int MAX_ENTRIES = 50;
    private static final List<String> HISTORY = new ArrayList<>();
    // HISTORY.size() denotes the current, unsubmitted search text.
    private static int cursor;
    private static String draft = "";
    private static String lastReturned;

    private SearchHistoryManager() {}

    public static void addHistory(String text) {
        if (text != null && !text.trim().isEmpty()) {
            // Move repeated searches to the most-recent position.
            HISTORY.remove(text);
            HISTORY.add(text);
            if (HISTORY.size() > MAX_ENTRIES) HISTORY.remove(0);
        }
        resetCursor();
    }

    public static String navigateUp(String currentText) {
        String current = currentText == null ? "" : currentText;
        detectEditedText(current);
        if (HISTORY.isEmpty()) return current;
        if (cursor == HISTORY.size()) draft = current;
        if (cursor > 0) cursor--;
        lastReturned = HISTORY.get(cursor);
        return lastReturned;
    }

    public static String navigateDown(String currentText) {
        String current = currentText == null ? "" : currentText;
        detectEditedText(current);
        if (cursor >= HISTORY.size()) return current;
        cursor++;
        lastReturned = cursor == HISTORY.size() ? draft : HISTORY.get(cursor);
        return lastReturned;
    }

    private static void detectEditedText(String current) {
        if (lastReturned != null && !lastReturned.equals(current)) resetCursor();
    }

    public static void resetCursor() {
        cursor = HISTORY.size();
        draft = "";
        lastReturned = null;
    }
}
