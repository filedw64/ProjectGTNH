package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.gui.GUIEMCBuildersWand;
import moze_intel.projecte.utils.WandHelper;
import moze_intel.projecte.utils.WandMode;
import moze_intel.projecte.utils.WandPlacementQueue;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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

	// 聚焦方块：注册名 + meta。未设置时按被点击方块放置与计费
	public static final String NBT_FOCUS = "FocusBlock";
	public static final String NBT_FOCUS_META = "FocusMeta";

	// 同一次右键可能同时命中 onItemUse 与 onItemRightClick，用世界时间做防重开标记
	@SideOnly(Side.CLIENT)
	private static long lastGuiOpenTime = Long.MIN_VALUE;

	public BuildersWand() {
		this.setUnlocalizedName("pe_emc_builders_wand");
		this.setTextureName("projecte:builders_wand");
		this.setMaxStackSize(1);
		this.setCreativeTab(ObjHandler.tab);
	}

	/**
	 * 读模式：非法值一律回落到 3x3，老存档字节语义不变。
	 */
	public byte getMode(ItemStack stack) {
		if (stack.stackTagCompound == null) {
			stack.stackTagCompound = new NBTTagCompound();
		}

		return WandMode.fromId(stack.stackTagCompound.getByte(NBT_MODE)).getId();
	}

	public void setMode(ItemStack stack, byte mode) {
		if (stack.stackTagCompound == null) {
			stack.stackTagCompound = new NBTTagCompound();
		}

		stack.stackTagCompound.setByte(NBT_MODE, mode);
	}

	/**
	 * 读聚焦方块：未设置、注册名已失效、方块无对应物品一律返回 null，不抛异常。
	 */
	public ItemStack getFocusStack(ItemStack wand) {
		if (wand == null || wand.stackTagCompound == null) return null;

		String name = wand.stackTagCompound.getString(NBT_FOCUS);

		if (name.isEmpty()) return null;

		Object obj = Block.blockRegistry.getObject(name);

		if (!(obj instanceof Block block)) return null;

		ItemStack focus = new ItemStack(block, 1, wand.stackTagCompound.getInteger(NBT_FOCUS_META));

		// 拿不到物品就没法放置和计费
		return focus.getItem() == null ? null : focus;
	}

	/**
	 * 写聚焦方块；focus 为 null 表示清除。
	 */
	public void setFocusStack(ItemStack wand, ItemStack focus) {
		if (wand == null) return;

		if (wand.stackTagCompound == null) {
			wand.stackTagCompound = new NBTTagCompound();
		}

		Block block = null;

		if (focus != null && focus.getItem() instanceof ItemBlock itemBlock) {
			block = itemBlock.field_150939_a;
		}

		String name = block == null ? null : Block.blockRegistry.getNameForObject(block);

		// 非法方块按清除处理，不留无法解析的名字
		if (name == null) {
			wand.stackTagCompound.removeTag(NBT_FOCUS);
			wand.stackTagCompound.removeTag(NBT_FOCUS_META);
			return;
		}

		wand.stackTagCompound.setString(NBT_FOCUS, name);
		wand.stackTagCompound.setInteger(NBT_FOCUS_META, focus.getItemDamage());
	}

	public static String getModeLangKey(byte mode) {
		return WandMode.fromId(mode).getLangKey();
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		// 面板只在客户端打开，服务端这条分支什么都不做
		if (world.isRemote) {
			if (player.isSneaking()) {
				openGui(player, getMode(stack), null, getFocusStack(stack));
			}

			return stack;
		}

		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		// 客户端只负责开面板，放置完全交给服务端
		if (world.isRemote) {
			if (player.isSneaking()) {
				openGui(player, getMode(stack), new ItemStack(world.getBlock(x, y, z), 1, world.getBlockMetadata(x, y, z)), getFocusStack(stack));
			}

			return true;
		}

		// 潜行 + 右键交给客户端开面板，服务端绝不放方块
		if (player.isSneaking()) return true;

		if (!(player instanceof EntityPlayerMP playerMP)) return true;

		Block targetBlock = world.getBlock(x, y, z);
		int targetMeta = world.getBlockMetadata(x, y, z);
		ItemStack targetStack = new ItemStack(targetBlock, 1, targetMeta);

		if (targetStack.getItem() == null) return true;

		// 区域照旧按被点击方块算；有聚焦则放聚焦方块、按聚焦收费
		ItemStack focus = getFocusStack(stack);
		Block placeBlock = targetBlock;
		int placeMeta = targetMeta;
		ItemStack costStack = targetStack;

		if (focus != null && focus.getItem() instanceof ItemBlock focusItem) {
			placeBlock = focusItem.field_150939_a;
			placeMeta = focus.getItemDamage();
			costStack = focus;
		}

		WandHelper.PlacementPlan plan = WandHelper.plan(world, x, y, z, side, getMode(stack),
			ProjectEConfig.buildersWandMaxBlocks, player, costStack);

		if (plan == null || plan.size() <= 0) return true;

		WandPlacementQueue.submit(playerMP, plan, placeBlock, placeMeta, costStack);

		return true;
	}

	@SideOnly(Side.CLIENT)
	private static void openGui(EntityPlayer player, byte mode, ItemStack targetBlock, ItemStack focusBlock) {
		long now = player.worldObj.getTotalWorldTime();

		if (lastGuiOpenTime == now) return;

		lastGuiOpenTime = now;

		Minecraft.getMinecraft().displayGuiScreen(new GUIEMCBuildersWand(mode, targetBlock, focusBlock));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean advanced) {
		list.add(StatCollector.translateToLocal("pe.builderswand.tooltip1"));
		list.add(String.format(StatCollector.translateToLocal("pe.builderswand.tooltip2"), "EMC"));
		list.add(StatCollector.translateToLocal("pe.builderswand.tooltip3"));
		list.add(StatCollector.translateToLocal("pe.item.mode") + ": " + EnumChatFormatting.AQUA
			+ StatCollector.translateToLocal(WandMode.fromId(getMode(stack)).getLangKey()));

		ItemStack focus = getFocusStack(stack);

		// 未设置聚焦时不显示这一行
		if (focus != null) {
			list.add(String.format(StatCollector.translateToLocal("pe.builderswand.tooltip.focus"), focus.getDisplayName()));
		}
	}
}
