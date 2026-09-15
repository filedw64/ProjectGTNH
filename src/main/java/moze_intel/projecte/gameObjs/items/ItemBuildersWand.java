package moze_intel.projecte.gameObjs.items;

import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import java.util.List;

public class ItemBuildersWand extends Item {

	public ItemBuildersWand() {
		this.setUnlocalizedName("emc_builders_wand");
		this.setTextureName("projecte:builders_wand");
		this.setMaxStackSize(1);
		this.setCreativeTab(ObjHandler.cTab);
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) return true;

		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ItemStack targetStack = new ItemStack(targetBlock, 1, targetMeta);

		if (targetStack.getItem() == null) return true;

		List<ChunkCoordinates> blocks = WandHelper.getBlocksToPlace(world, x, y, z, side, 1024);
		if (blocks.isEmpty()) return true;

		boolean emcChanged = false;
		boolean invChanged = false;

		// 【优化】循环外部 O(1) 预计算 EMC 和知识状态
		boolean hasKnowledge = Transmutation.hasKnowledgeForStack(targetStack, player);
		double emcCost = EMCHelper.getEmcValue(targetStack);

		for (ChunkCoordinates coord : blocks) {
			// 传入预计算的值
			if (WandHelper.consumeCost(player, targetStack, hasKnowledge, emcCost)) {
				world.setBlock(coord.posX, coord.posY, coord.posZ, targetBlock, targetMeta, 3);
				emcChanged = true;
				invChanged = true;
			} else {
				break;
			}
		}

		// 统一处理音效和发包，避免循环内部重复执行
		if (emcChanged || invChanged) {
			world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5,
				targetBlock.stepSound.func_150496_b(),
				(targetBlock.stepSound.getVolume() + 1.0F) / 2.0F,
				targetBlock.stepSound.getPitch() * 0.8F);
		}

		if (invChanged) {
			player.inventoryContainer.detectAndSendChanges();
		}

		if (emcChanged && player instanceof EntityPlayerMP) {
			Transmutation.sync(player);
		}

		return true;
	}
}
