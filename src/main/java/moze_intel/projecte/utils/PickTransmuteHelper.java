package moze_intel.projecte.utils;

import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.ArcaneTransmutationTablet;
import moze_intel.projecte.gameObjs.items.TransmutationTablet;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * 中键转化的共享实现：方块路径（PickBlockPKT）与 GUI 格子路径（GuiSlotPickPKT）共用。
 * 调用方须保证 template.stackSize == 1，且不得依赖本类修改 template 本身。
 */
public final class PickTransmuteHelper
{

	private PickTransmuteHelper() {}

	/**
	 * 背包 36 格内需有便携式转化桌或奥术转化桌，创造模式同样要求。
	 */
	public static boolean hasTablet(EntityPlayer player)
	{
		if (player == null) return false;

		ItemStack[] inv = player.inventory.mainInventory;
		if (inv == null) return false;

		for (ItemStack stack : inv)
		{
			if (stack == null || stack.getItem() == null) continue;
			if (stack.getItem() instanceof TransmutationTablet || stack.getItem() instanceof ArcaneTransmutationTablet) return true;
		}
		return false;
	}

	/**
	 * 消耗 EMC 把 template 对应物品按配置的数量放进玩家背包。
	 * @return true 表示确实发放了物品；静默失败一律 false
	 */
	public static boolean tryTransmute(EntityPlayerMP player, ItemStack template)
	{
		if (!hasTablet(player)) return false;
		if (template == null || template.getItem() == null) return false;
		if (!ProjectEConfig.enablePickBlockTransmute) return false;

		// 自己拷贝，调用方的栈只当模板
		ItemStack stack = template.copy();
		stack.stackSize = 1;

		// 模板耐久归一：EMC 与知识都以含 itemDamage 的键比对，受损工具/弓与“已学习的满耐久版本”对不上。
		// 非 damageable 物品（羊毛颜色等 meta 变体）绝不动 itemDamage。
		if (stack.getItem() != null && stack.getMaxDamage() > 0)
		{
			stack.setItemDamage(0);
		}

		if (!EMCHelper.doesItemHaveEmc(stack)) return false;
		double unit = EMCHelper.getEmcValue(stack);
		if (unit <= 0 || Double.isNaN(unit) || Double.isInfinite(unit)) return false;
		if (!Transmutation.hasKnowledgeForStack(stack, player)) return false;

		int maxStack = stack.getMaxStackSize();
		if (maxStack <= 0) maxStack = 64;

		long want = Math.max(1, ProjectEConfig.pickBlockMaxStacks) * (long) maxStack;
		if (want > Integer.MAX_VALUE) want = Integer.MAX_VALUE;

		boolean free = ProjectEConfig.pickBlockCreativeFree && player.capabilities.isCreativeMode;
		int qty;
		if (free)
		{
			qty = (int) want;
		}
		else
		{
			qty = (int) Math.min(want, (long) Math.floor(Transmutation.getEmc(player) / unit));
		}
		if (qty <= 0) return false;

		// 全有或全无：放不下就零副作用返回
		int space = ItemHelper.getSpaceFor(player.inventory.mainInventory, stack);
		if (space < qty) return false;

		int inserted = 0;
		int remaining = qty;
		while (remaining > 0)
		{
			int part = Math.min(maxStack, remaining);
			ItemStack toAdd = stack.copy();
			toAdd.stackSize = part;
			boolean whole = player.inventory.addItemStackToInventory(toAdd);
			// addItemStackToInventory 会把放不下的余量写回传入栈，两种信号取较大值以防自定义背包语义不同
			inserted += whole ? part : part - Math.max(0, Math.min(part, toAdd.stackSize));
			remaining -= part;
		}

		if (inserted <= 0) return false;

		if (!free)
		{
			Transmutation.setEmc(player, Transmutation.getEmc(player) - inserted * unit);
			Transmutation.sync(player);
		}
		player.inventoryContainer.detectAndSendChanges();

		// 容量已校验，正常不会走到这里；出现时只说明背包行为异常，不刷 logInfo
		if (inserted < qty)
		{
			PELogger.logDebug("PickTransmute: inserted " + inserted + " of " + qty + " for " + player.getCommandSenderName());
		}

		return true;
	}
}
