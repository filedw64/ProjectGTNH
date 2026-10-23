package moze_intel.projecte.utils.mercurial;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.MapStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端全局剪贴板管理器
 * 防止将几千个方块存入 Item NBT 导致网络包溢出崩溃
 * 内存只是缓存，主副本在 MercurialClipboardStore 存档里
 */
public class MercurialClipboard {
	private static final Map<UUID, ClipboardData> clipboards = new HashMap<>();

	public static void saveClipboard(UUID playerId, ClipboardData data) {
		if (data != null && !data.blocks.isEmpty()) {
			clipboards.put(playerId, data);
			MercurialClipboardStore store = getStore();
			if (store != null) {
				// put 内部 markDirty，写盘时机交给存档系统
				store.put(playerId, data);
			}
		} else {
			clearClipboard(playerId);
		}
	}

	public static ClipboardData getClipboard(UUID playerId) {
		ClipboardData cached = clipboards.get(playerId);
		if (cached != null) return cached;

		MercurialClipboardStore store = getStore();
		if (store == null) return null;

		ClipboardData loaded = store.get(playerId);
		if (loaded != null) {
			clipboards.put(playerId, loaded);
		}
		return loaded;
	}

	// 玩家主动重置选区：内存和存档一起清，否则下次登录又会把旧选区拉回来
	public static void clearClipboard(UUID playerId) {
		clipboards.remove(playerId);
		MercurialClipboardStore store = getStore();
		if (store != null) {
			store.remove(playerId);
		}
	}

	// 登出只清内存：被剪掉的方块已经真的从世界消失了，删存档等于永久丢方块
	public static void evictClipboard(UUID playerId) {
		clipboards.remove(playerId);
	}

	private static MercurialClipboardStore getStore() {
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null || server.worldServers.length == 0) return null;

		// 所有 WorldServer 共用同一个全局 MapStorage，取主世界那一份即可
		MapStorage storage = server.worldServers[0].mapStorage;
		MercurialClipboardStore store = (MercurialClipboardStore) storage.loadData(MercurialClipboardStore.class, MercurialClipboardStore.NAME);
		if (store == null) {
			store = new MercurialClipboardStore(MercurialClipboardStore.NAME);
			storage.setData(MercurialClipboardStore.NAME, store);
		}
		return store;
	}
}
