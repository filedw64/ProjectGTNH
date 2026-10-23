package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.WandHelper;
import moze_intel.projecte.utils.WandPlacementQueue;
import moze_intel.projecte.utils.WandUndoProps;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EMC 建筑之杖的撤销入口：潜行 + 左键，或 GUI 的撤销按钮。
 * 还原成功才退还该格实付成本（EMC 或背包方块）。
 */
public class BuildersWandEventHandler {

	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
		// 撤销只在服务端执行
		if (event.world.isRemote) return;

		EntityPlayer player = event.entityPlayer;
		ItemStack heldItem = player.getHeldItem();

		if (heldItem == null || !(heldItem.getItem() instanceof BuildersWand)) return;
		if (!player.isSneaking()) return;

		// 必须先吞掉这次挖掘，否则玩家会把方块挖掉
		event.setCanceled(true);

		undo(player, event.world);
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
		EntityPlayer player = event.player;

		if (player == null) return;

		// 未完成作业的静态引用必须清掉，钱包在 cancel 里落盘
		WandPlacementQueue.clearPlayer(player.getUniqueID());
	}

	/**
	 * 逐格还原并退还实付成本。
	 *
	 * @return 成功还原的格数
	 */
	public static int undo(EntityPlayer player, World world) {
		WandUndoProps props = WandUndoProps.getDataFor(player);

		if (props == null || props.isEmpty()) {
			player.addChatComponentMessage(new ChatComponentTranslation("pe.builderswand.undo.none"));
			return 0;
		}

		List<WandUndoProps.Placement> records = new ArrayList<>(props.getRecords());

		// 同一方块共用一个钱包，退还单价才对
		Map<String, List<WandUndoProps.Placement>> refunds = new LinkedHashMap<>();
		// 暂时还原不了的记录留到下次（维度不符 / 区块未加载）
		List<WandUndoProps.Placement> retry = new ArrayList<>();
		int restored = 0;

		for (WandUndoProps.Placement record : records) {
			if (record.dimensionId != world.provider.dimensionId) {
				retry.add(record);
				continue;
			}

			Block prevBlock = WandUndoProps.getBlock(record.prevBlockName);
			Block placedBlock = WandUndoProps.getBlock(record.placedBlockName);

			// 反序列化失败的记录直接丢弃
			if (prevBlock == null || placedBlock == null) continue;

			if (record.posY < 0 || record.posY >= world.getHeight()) continue;
			// 绝不强制加载或生成区块
			if (!world.blockExists(record.posX, record.posY, record.posZ)) {
				retry.add(record);
				continue;
			}

			// 防复制：当前格必须仍是当时放下的那一块
			if (world.getBlock(record.posX, record.posY, record.posZ) != placedBlock) continue;
			if (world.getBlockMetadata(record.posX, record.posY, record.posZ) != record.placedMeta) continue;

			if (!world.setBlock(record.posX, record.posY, record.posZ, prevBlock, record.prevMeta, 3)) continue;

			restored++;

			// 还原成功才退费；创造模式放下的格子没有成本可退
			if (record.paySource == WandHelper.PAY_FAIL) continue;

			refunds.computeIfAbsent(record.placedBlockName + "@" + record.placedMeta, key -> new ArrayList<>()).add(record);
		}

		// 这一层到此消费完，只剩还原不了的
		props.clear();
		props.getRecords().addAll(retry);

		for (List<WandUndoProps.Placement> group : refunds.values()) {
			WandUndoProps.Placement first = group.get(0);
			Block placedBlock = WandUndoProps.getBlock(first.placedBlockName);

			if (placedBlock == null) continue;

			WandHelper.Wallet wallet = new WandHelper.Wallet(player, new ItemStack(placedBlock, 1, first.placedMeta));

			for (WandUndoProps.Placement record : group) {
				wallet.refund(record.paySource);
			}

			wallet.flush();
		}

		if (restored > 0) {
			player.inventoryContainer.detectAndSendChanges();
			Transmutation.sync(player);
			player.addChatComponentMessage(new ChatComponentTranslation("pe.builderswand.undo.done", restored));
		} else {
			player.addChatComponentMessage(new ChatComponentTranslation("pe.builderswand.undo.none"));
		}

		return restored;
	}
}
