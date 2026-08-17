package moze_intel.projecte.gameObjs.items;

import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

public class ItemBuildersWand extends Item {

	public ItemBuildersWand() {
		this.setUnlocalizedName("emc_builders_wand");
		this.setMaxStackSize(1);
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		// 建筑之杖的逻辑只在服务端执行，客户端只负责渲染
		if (world.isRemote) {
			return true;
		}

		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ItemStack targetStack = new ItemStack(targetBlock, 1, targetMeta);

		// 如果点击的方块没有物品形态（或者无法获取EMC），直接返回
		if (targetStack.getItem() == null) {
			return true;
		}

		// 获取要放置的位置列表 (上限1024)
		List<ChunkCoordinates> blocks = WandHelper.getBlocksToPlace(world, x, y, z, side, 1024);
		ForgeDirection dir = ForgeDirection.getOrientation(side);

		boolean emcChanged = false;

		for (ChunkCoordinates coord : blocks) {
			int placeX = coord.posX + dir.offsetX;
			int placeY = coord.posY + dir.offsetY;
			int placeZ = coord.posZ + dir.offsetZ;

			// 验证目标位置是否可以放置（必须是空气或可替换方块，如高草丛、水等）
			if (world.isAirBlock(placeX, placeY, placeZ) || world.getBlock(placeX, placeY, placeZ).isReplaceable(world, placeX, placeY, placeZ)) {

				// 调用 WandHelper 尝试扣除 EMC 或 背包内相同方块
				if (WandHelper.consumeCost(player, targetStack)) {
					// 放置方块并更新方块状态 (flag 3: 更新方块及客户端)
					world.setBlock(placeX, placeY, placeZ, targetBlock, targetMeta, 3);

					// 播放原版方块放置音效
					world.playSoundEffect(placeX + 0.5, placeY + 0.5, placeZ + 0.5,
						targetBlock.stepSound.func_150496_b(),
						(targetBlock.stepSound.getVolume() + 1.0F) / 2.0F,
						targetBlock.stepSound.getPitch() * 0.8F);

					emcChanged = true;
				} else {
					// EMC和物品都不足时，停止后续的扩散放置
					break;
				}
			}
		}

		// 如果消耗了转化桌内的 EMC，向客户端发送知识同步包更新 UI 显示
		if (emcChanged && player instanceof EntityPlayerMP) {
			Transmutation.sync(player);
		}

		return true;
	}
}
