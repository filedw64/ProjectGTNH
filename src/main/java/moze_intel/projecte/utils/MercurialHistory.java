package moze_intel.projecte.utils;

import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MercurialHistory {
	public static final int MAX_HISTORY = 16;

	// 玩家 UUID -> 历史记录链表 (最新的在表头 index 0)
	private static final Map<UUID, LinkedList<HistoryRecord>> historyMap = new HashMap<>();
	// 玩家 UUID -> 等待确认撤销的记录 ID
	private static final Map<UUID, UUID> pendingConfirmations = new HashMap<>();

	public static class BlockChange {
		public final int x, y, z;
		public final Block oldBlock, newBlock;
		public final int oldMeta, newMeta;
		public final double emcDiff;
		public final net.minecraft.nbt.NBTTagCompound oldTeNbt; // 新增：保存机器原本的 NBT 状态

		public BlockChange(int x, int y, int z, Block oldB, int oldM, Block newB, int newM, double emc, net.minecraft.nbt.NBTTagCompound teNbt) {
			this.x = x; this.y = y; this.z = z;
			this.oldBlock = oldB; this.oldMeta = oldM;
			this.newBlock = newB; this.newMeta = newM;
			this.emcDiff = emc;
			this.oldTeNbt = teNbt;
		}
	}

	public static void sendNotify(EntityPlayerMP player, String msg) {
		moze_intel.projecte.network.PacketHandler.sendTo(new moze_intel.projecte.network.packets.MercurialNotifyPKT(msg), player);
	}

	public static class HistoryRecord {
		public final UUID id = UUID.randomUUID();
		public final List<BlockChange> changes;
		public final long timestamp = System.currentTimeMillis();
		public UUID parentId = null; // 用于构建 UI 树状图的直接依赖

		public HistoryRecord(List<BlockChange> changes) {
			this.changes = changes;
		}
	}

	/**
	 * 将一次成功的批量建造压入历史栈
	 */
	public static void addRecord(EntityPlayerMP player, List<BlockChange> changes) {
		if (changes == null || changes.isEmpty()) return;
		UUID pid = player.getUniqueID();
		LinkedList<HistoryRecord> list = historyMap.computeIfAbsent(pid, k -> new LinkedList<>());

		HistoryRecord newRecord = new HistoryRecord(changes);

		// 计算树状图依赖 (查找最近一次修改过相同坐标的记录)
		outer:
		for (BlockChange bc : changes) {
			for (HistoryRecord old : list) {
				for (BlockChange oldBc : old.changes) {
					if (oldBc.x == bc.x && oldBc.y == bc.y && oldBc.z == bc.z) {
						newRecord.parentId = old.id; // 确立父子关系
						break outer;
					}
				}
			}
		}

		list.addFirst(newRecord);
		if (list.size() > MAX_HISTORY) list.removeLast();
		pendingConfirmations.remove(pid);
	}

	// 新增一个获取玩家所有记录的方法，供网络包调用
	public static List<HistoryRecord> getRecords(EntityPlayerMP player) {
		return historyMap.getOrDefault(player.getUniqueID(), new LinkedList<>());
	}

	/**
	 * 快捷触发：尝试撤销最新的一次记录
	 */
	public static void tryUndo(EntityPlayerMP player, World world) {
		UUID pid = player.getUniqueID();

		// 如果当前有处于挂起警告状态的撤销，再次按下快捷键即视为“确认”
		if (pendingConfirmations.containsKey(pid)) {
			UUID targetId = pendingConfirmations.remove(pid);
			doUndo(player, world, targetId, true);
			return;
		}

		LinkedList<HistoryRecord> list = historyMap.get(pid);
		if (list == null || list.isEmpty()) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "No history to undo."));
			return;
		}

		// 否则尝试撤销最上面的一条记录
		doUndo(player, world, list.getFirst().id, false);
	}

	/**
	 * 核心撤销逻辑：支持跨记录独立撤销与级联冲突检测
	 */
	public static void doUndo(EntityPlayerMP player, World world, UUID recordId, boolean force) {
		UUID pid = player.getUniqueID();
		LinkedList<HistoryRecord> list = historyMap.get(pid);
		if (list == null) return;

		HistoryRecord target = null;
		for (HistoryRecord r : list) {
			if (r.id.equals(recordId)) { target = r; break; }
		}

		if (target == null) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "History record not found or expired."));
			return;
		}

		// 冲突检测引擎
		List<HistoryRecord> conflicts = new ArrayList<>();
		for (BlockChange bc : target.changes) {
			Block currentBlock = world.getBlock(bc.x, bc.y, bc.z);
			int currentMeta = world.getBlockMetadata(bc.x, bc.y, bc.z);

			// 如果方块状态与建造时不符，说明被后续操作修改过
			if (currentBlock != bc.newBlock || currentMeta != bc.newMeta) {
				// 遍历比 target 更新的记录 (位于链表前方)
				for (HistoryRecord newer : list) {
					if (newer == target) break;
					for (BlockChange nbc : newer.changes) {
						if (nbc.x == bc.x && nbc.y == bc.y && nbc.z == bc.z) {
							if (!conflicts.contains(newer)) conflicts.add(newer);
						}
					}
				}
			}
		}

		// 如果存在冲突且尚未确认，挂起并警告玩家
		if (!conflicts.isEmpty() && !force) {
			pendingConfirmations.put(pid, target.id);
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.YELLOW +
				"Warning: Newer operations modified these blocks. Undoing will ALSO revert " + conflicts.size() + " newer operation(s)."));
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GREEN +
				"[Press the Undo key again to confirm]"));
			return;
		}

		// 如果强制执行，先把依赖的冲突记录(较新的)优先撤销掉，防止方块状态错乱
		if (force) {
			for (HistoryRecord conflict : conflicts) {
				revertRecord(player, world, conflict);
				list.remove(conflict);
			}
		}

		// 撤销目标记录
		revertRecord(player, world, target);
		list.remove(target);
		player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Undo successful."));
	}

	private static void revertRecord(EntityPlayerMP player, World world, HistoryRecord record) {
		double emcToRefund = 0;
		int blocksRestored = 0;

		for (BlockChange bc : record.changes) {
			world.setBlock(bc.x, bc.y, bc.z, bc.oldBlock, bc.oldMeta, 2);

			// 精确回溯 NBT (如果原来是机器/箱子)
			if (bc.oldTeNbt != null) {
				net.minecraft.tileentity.TileEntity te = net.minecraft.tileentity.TileEntity.createAndLoadEntity(bc.oldTeNbt);
				if (te != null) {
					world.setTileEntity(bc.x, bc.y, bc.z, te);
					te.updateContainingBlockInfo();
				}
			}

			emcToRefund += bc.emcDiff;
			blocksRestored++;
		}

		if (emcToRefund != 0) {
			// 实现撤销时 90% EMC 返还率
			if (emcToRefund > 0) {
				emcToRefund *= 0.9D;
			}
			double currentEmc = Transmutation.getEmc(player);
			Transmutation.setEmc(player, currentEmc + emcToRefund);
			Transmutation.sync(player);
		}
	}
}
