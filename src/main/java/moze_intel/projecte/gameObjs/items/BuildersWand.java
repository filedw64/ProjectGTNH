package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.events.BuildersWandEventHandler;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.List;

/**
 * Consume EMC to use builders wand.
 *
 * @author Windy-Bye
 */
public class BuildersWand extends Item {

	private static final String NBT_MODE = "Mode";

	public BuildersWand() {
		this.setUnlocalizedName("pe_emc_builders_wand");
		this.setTextureName("projecte:builders_wand");
		this.setMaxStackSize(1);
		this.setCreativeTab(ObjHandler.tab);
	}

	/**
	 * 读模式：stackTagCompound 为空时先初始化再读，杜绝 NPE。
	 */
	public byte getMode(ItemStack stack) {
		if (stack.stackTagCompound == null) {
			stack.stackTagCompound = new NBTTagCompound();
		}

		byte mode = stack.stackTagCompound.getByte(NBT_MODE);

		if (mode < 0 || mode >= WandHelper.MODE_COUNT) {
			mode = WandHelper.MODE_3X3;
		}

		return mode;
	}

	public void setMode(ItemStack stack, byte mode) {
		if (stack.stackTagCompound == null) {
			stack.stackTagCompound = new NBTTagCompound();
		}

		stack.stackTagCompound.setByte(NBT_MODE, mode);
	}

	public static String getModeLangKey(byte mode) {
		if (mode == WandHelper.MODE_5X5) {
			return "pe.builderswand.mode.5x5";
		}

		if (mode == WandHelper.MODE_PLANE) {
			return "pe.builderswand.mode.plane";
		}

		return "pe.builderswand.mode.3x3";
	}

	private void cycleMode(ItemStack stack, EntityPlayer player) {
		byte newMode = (byte) (getMode(stack) + 1);

		if (newMode >= WandHelper.MODE_COUNT) {
			newMode = WandHelper.MODE_3X3;
		}

		setMode(stack, newMode);

		// 模式名传嵌套的 ChatComponentTranslation，由客户端按自身语言翻译
		player.addChatComponentMessage(new ChatComponentTranslation("pe.item.mode_switch",
			new ChatComponentTranslation(getModeLangKey(newMode))));

		// NBT 改动后立即同步一次，避免 tooltip 停留在旧模式
		player.inventoryContainer.detectAndSendChanges();
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		// NBT 改动只在服务端做，客户端原样返回 stack 以免手部抖动
		if (world.isRemote) return stack;

		// 潜行 + 右键空中走这条路（右键方块在服务端不会回落到这里，见 onItemUse）
		if (player.isSneaking()) {
			cycleMode(stack, player);
		}

		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) return true;

		// 1.7.10 服务端右键方块只走到 onItemUse（ForgeHooks.onPlaceItemIntoWorld 无 onItemRightClick 回落），
		// 因此潜行切模式必须在这里完成并返回 true，返回 false 会让这次交互直接丢失
		if (player.isSneaking()) {
			cycleMode(stack, player);
			return true;
		}

		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ItemStack targetStack = new ItemStack(targetBlock, 1, targetMeta);

		if (targetStack.getItem() == null) return true;

		byte mode = getMode(stack);
		// 平面无限模式的格数上限走 config，其余模式形状固定
		List<ChunkCoordinates> blocks = WandHelper.getBlocksToPlace(world, x, y, z, side, mode, ProjectEConfig.buildersWandMaxBlocks);

		if (blocks.isEmpty()) return true;

		// 【优化】循环外部 O(1) 预计算 EMC 和知识状态
		boolean hasKnowledge = Transmutation.hasKnowledgeForStack(targetStack, player);
		double emcCost = EMCHelper.getEmcValue(targetStack);
		// 【性能】一次操作只建一次背包槽位索引，之后每格 O(1) 扣减
		WandHelper.CostIndex costIndex = WandHelper.createCostIndex(player, targetStack);

		boolean placedAny = false;

		// 本次放置覆盖上一次的撤销记录
		BuildersWandEventHandler.clearPlacements(player);

		for (ChunkCoordinates coord : blocks) {
			// 传入预计算好的值
			if (!WandHelper.consumeCost(player, targetStack, hasKnowledge, emcCost, costIndex)) {
				break;
			}

			Block prevBlock = world.getBlock(coord.posX, coord.posY, coord.posZ);
			int prevMeta = world.getBlockMetadata(coord.posX, coord.posY, coord.posZ);

			// 放置失败既不计入本杖的放置，也不记入撤销记录
			if (!world.setBlock(coord.posX, coord.posY, coord.posZ, targetBlock, targetMeta, 3)) {
				continue;
			}

			placedAny = true;
			BuildersWandEventHandler.recordPlacement(player, coord, prevBlock, prevMeta, targetBlock, targetMeta);
		}

		// 统一处理音效，避免循环内部重复执行
		if (placedAny) {
			world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5,
				targetBlock.stepSound.func_150496_b(),
				(targetBlock.stepSound.getVolume() + 1.0F) / 2.0F,
				targetBlock.stepSound.getPitch() * 0.8F);

			// 循环结束只补一次背包同步、只补一次 EMC 同步
			player.inventoryContainer.detectAndSendChanges();

			if (player instanceof EntityPlayerMP) {
				Transmutation.sync(player);
			}
		}

		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean advanced) {
		list.add(StatCollector.translateToLocal("pe.builderswand.tooltip1"));
		list.add(String.format(StatCollector.translateToLocal("pe.builderswand.tooltip2"), "EMC"));
		list.add(StatCollector.translateToLocal("pe.builderswand.tooltip3"));
		list.add(StatCollector.translateToLocal("pe.item.mode") + ": " + EnumChatFormatting.AQUA
			+ StatCollector.translateToLocal(getModeLangKey(getMode(stack))));
	}
}
