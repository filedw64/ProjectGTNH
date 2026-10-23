package moze_intel.projecte.utils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建筑之杖的分 tick 放置队列：一个玩家同时只允许一个未完成作业。
 * 每格顺序固定为「校验 → 付费 → 放置 → 失败立即退还」。
 */
public class WandPlacementQueue {

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private static class Job {
		private final EntityPlayerMP player;
		private final WandHelper.Wallet wallet;
		// 实际放下的方块，也是撤销记录里的 placedBlock
		private final Block placeBlock;
		private final int placeMeta;
		private final int dimensionId;
		private final List<ChunkCoordinates> positions;
		private int cursor;
		private int placedTotal;
		private boolean clearedUndo;
		private boolean soundPlayed;

		private Job(EntityPlayerMP player, WandHelper.PlacementPlan plan, Block placeBlock, int placeMeta, ItemStack costStack) {
			this.player = player;
			this.placeBlock = placeBlock;
			this.placeMeta = placeMeta;
			this.dimensionId = player.worldObj.provider.dimensionId;
			this.positions = new ArrayList<>(plan.positions);
			this.wallet = new WandHelper.Wallet(player, costStack);
		}
	}

	/**
	 * placeBlock/placeMeta 是实际放下的方块，costStack 是按什么收费（聚焦方块，无聚焦时为点击方块）。
	 */
	public static void submit(EntityPlayerMP player, WandHelper.PlacementPlan plan, Block placeBlock, int placeMeta, ItemStack costStack) {
		if (player == null) return;

		// 作业进行中再右键：直接替换旧作业，不排队叠加
		cancel(player);

		if (plan == null || plan.positions.isEmpty() || placeBlock == null) return;

		// 收费栈缺失时退回按所放方块收费，绝不让钱包空转
		ItemStack payStack = costStack == null || costStack.getItem() == null ? new ItemStack(placeBlock, 1, placeMeta) : costStack;

		JOBS.put(player.getUniqueID(), new Job(player, plan, placeBlock, placeMeta, payStack));
	}

	public static void cancel(EntityPlayerMP player) {
		if (player == null) return;

		Job job = JOBS.remove(player.getUniqueID());

		if (job != null) finish(job, true);
	}

	public static boolean hasPending(EntityPlayerMP player) {
		return player != null && JOBS.containsKey(player.getUniqueID());
	}

	/**
	 * 登出清理：只落盘钱包，不再向已断线的玩家发包。
	 */
	public static void clearPlayer(UUID id) {
		Job job = JOBS.remove(id);

		if (job != null) finish(job, false);
	}

	/**
	 * 服务端停止：清空全部作业。
	 */
	public static void clearAll() {
		for (Job job : JOBS.values()) {
			finish(job, false);
		}

		JOBS.clear();
	}

	public static void onServerTick() {
		if (JOBS.isEmpty()) return;

		int budget = ProjectEConfig.buildersWandBlocksPerTick;

		if (budget < 1) budget = 1;

		Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();

		while (it.hasNext()) {
			Job job = it.next().getValue();

			boolean done;

			if (!stillValid(job)) {
				done = true;
			} else {
				done = tick(job, budget);
			}

			if (done) {
				it.remove();
				finish(job, job.player.isEntityAlive() && job.player.playerNetServerHandler != null);
			}
		}
	}

	/**
	 * 推进一个作业；返回 true 表示作业已结束。
	 */
	private static boolean tick(Job job, int budget) {
		World world = job.player.worldObj;
		boolean creative = job.player.capabilities.isCreativeMode;
		int placedThisTick = 0;

		while (job.cursor < job.positions.size() && placedThisTick < budget) {
			ChunkCoordinates coord = job.positions.get(job.cursor++);

			// 1. 先校验：落脚点可能已被别人改掉，这种情况不付费
			if (!canPlaceAt(world, coord, job.placeBlock)) continue;

			// 2. 再付费：创造模式免单，用 PAY_FAIL 表示本格没有可退成本
			int source = WandHelper.PAY_FAIL;

			if (!creative) {
				if (!job.wallet.canPayOne()) {
					halt(job);
					return true;
				}

				source = job.wallet.payOne();

				if (source == WandHelper.PAY_FAIL) {
					halt(job);
					return true;
				}
			}

			Block prevBlock = world.getBlock(coord.posX, coord.posY, coord.posZ);
			int prevMeta = world.getBlockMetadata(coord.posX, coord.posY, coord.posZ);

			// 3. 再放置，失败立即退还本格成本
			if (!world.setBlock(coord.posX, coord.posY, coord.posZ, job.placeBlock, job.placeMeta, 3)) {
				job.wallet.refund(source);
				continue;
			}

			// 首次真正落地时才清空旧记录，保持「单层」语义
			WandUndoProps props = WandUndoProps.getDataFor(job.player);

			if (props != null) {
				if (!job.clearedUndo) {
					props.clear();
					job.clearedUndo = true;
				}

				props.add(coord.posX, coord.posY, coord.posZ, prevBlock, prevMeta,
					job.placeBlock, job.placeMeta, job.dimensionId, source);
			}

			job.placedTotal++;
			placedThisTick++;

			if (!job.soundPlayed) {
				job.soundPlayed = true;
				world.playSoundEffect(coord.posX + 0.5, coord.posY + 0.5, coord.posZ + 0.5,
					job.placeBlock.stepSound.func_150496_b(),
					(job.placeBlock.stepSound.getVolume() + 1.0F) / 2.0F,
					job.placeBlock.stepSound.getPitch() * 0.8F);
			}
		}

		return job.cursor >= job.positions.size();
	}

	/**
	 * 材料不足：提示后结束作业，已放置的格子保留在撤销记录里。
	 */
	private static void halt(Job job) {
		job.player.addChatComponentMessage(new ChatComponentTranslation("pe.builderswand.queue.halt", job.placedTotal));
	}

	/**
	 * 作业存活的必要条件：玩家存活、维度未变、手里仍是建筑之杖。
	 */
	private static boolean stillValid(Job job) {
		EntityPlayerMP player = job.player;

		if (player == null || player.isDead || !player.isEntityAlive()) return false;
		// 已断线就绝不能再放：flush 会写回一个已脱离的实体，等于白送方块
		if (player.playerNetServerHandler == null) return false;
		if (player.worldObj == null || player.worldObj.provider.dimensionId != job.dimensionId) return false;

		ItemStack held = player.getHeldItem();

		return held != null && held.getItem() instanceof BuildersWand;
	}

	/**
	 * 放置前复核：支撑面在 plan 阶段已校验，这里只防坐标落空与覆盖。
	 */
	private static boolean canPlaceAt(World world, ChunkCoordinates coord, Block placeBlock) {
		if (coord.posY < 0 || coord.posY >= world.getHeight()) return false;
		// 绝不强制加载或生成区块
		if (!world.blockExists(coord.posX, coord.posY, coord.posZ)) return false;

		if (!world.isAirBlock(coord.posX, coord.posY, coord.posZ)
			&& !world.getBlock(coord.posX, coord.posY, coord.posZ).isReplaceable(world, coord.posX, coord.posY, coord.posZ)) return false;

		AxisAlignedBB aabb = placeBlock.getCollisionBoundingBoxFromPool(world, coord.posX, coord.posY, coord.posZ);

		return aabb == null || world.checkNoEntityCollision(aabb);
	}

	/**
	 * 作业唯一出口：钱包落盘一次，必要时补发背包与 EMC 同步。
	 */
	private static void finish(Job job, boolean notify) {
		job.wallet.flush();

		if (!notify) return;

		job.player.inventoryContainer.detectAndSendChanges();
		Transmutation.sync(job.player);
	}

	/**
	 * 挂到 FML 事件总线，照抄 TickEvents 的 ServerTickEvent(Phase.END) 写法。
	 */
	public static class TickHandler {
		@SubscribeEvent
		public void onServerTick(TickEvent.ServerTickEvent event) {
			if (event.phase == TickEvent.Phase.END) {
				WandPlacementQueue.onServerTick();
			}
		}
	}
}
