package moze_intel.projecte.utils.mercurial;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端全局剪贴板管理器
 * 防止将几千个方块存入 Item NBT 导致网络包溢出崩溃
 */
public class MercurialClipboard {
	private static final Map<UUID, ClipboardData> clipboards = new HashMap<>();

	public static void saveClipboard(UUID playerId, ClipboardData data) {
		if (data != null && !data.blocks.isEmpty()) {
			clipboards.put(playerId, data);
		} else {
			clipboards.remove(playerId);
		}
	}

	public static ClipboardData getClipboard(UUID playerId) {
		return clipboards.get(playerId);
	}

	public static void clearClipboard(UUID playerId) {
		clipboards.remove(playerId);
	}

	public static boolean hasClipboard(UUID playerId) {
		return clipboards.containsKey(playerId);
	}
}
