package moze_intel.projecte.utils;

import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.integration.helpers.EFRHelper;
import moze_intel.projecte.integration.helpers.ForestryHelper;
import moze_intel.projecte.integration.helpers.GTItemHelper;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Helper class for EMC.
 * Notice: Please try to keep methods tidy and alphabetically ordered. Thanks!
 */
public final class EMCHelper
{
	/**
	 * Consumes EMC from fuel items or Klein Stars
	 * Any extra EMC is discarded !!! To retain remainder EMC use ItemPE.consumeFuel()
	 */
	public static double consumePlayerFuel(EntityPlayer player, double minFuel)
	{
		if (player.capabilities.isCreativeMode)
		{
			return minFuel;
		}

		IInventory inv = player.inventory;
		int invSize = inv.getSizeInventory();
		// 使用基本类型数组代替 LinkedHashMap，消除每 tick 调用的对象分配和装箱开销
		int[] removeCounts = new int[invSize];
		boolean metRequirement = false;
		double emcConsumed = 0;

		for (int i = 0; i < invSize; i++)
		{
			ItemStack stack = inv.getStackInSlot(i);

			if (stack == null || stack.getItem() == null)
			{
				continue;
			}

			if (stack.getItem() instanceof IItemEmc itemEmc)
			{
				if (itemEmc.getStoredEmc(stack) >= minFuel)
				{
					itemEmc.extractEmc(stack, minFuel);
					player.inventoryContainer.detectAndSendChanges();
					return minFuel;
				}
			}
			else if (!metRequirement && FuelMapper.isStackFuel(stack))
			{
				double emc = getEmcValue(stack);
				int toRemove = ((int) Math.ceil((minFuel - emcConsumed) / emc));

				if (stack.stackSize >= toRemove)
				{
					removeCounts[i] = toRemove;
					emcConsumed += emc * toRemove;
					metRequirement = true;
				}
				else
				{
					removeCounts[i] = stack.stackSize;
					emcConsumed += emc * stack.stackSize;

					if (emcConsumed >= minFuel)
					{
						metRequirement = true;
					}
				}
			}
		}

		if (metRequirement)
		{
			for (int i = 0; i < invSize; i++)
			{
				if (removeCounts[i] > 0)
				{
					inv.decrStackSize(i, removeCounts[i]);
				}
			}

			player.inventoryContainer.detectAndSendChanges();
			return emcConsumed;
		}

		return -1;
	}

	public static boolean doesBlockHaveEmc(Block block) {
		return block != null && doesItemHaveEmc(new ItemStack(block));
	}

	public static boolean doesItemHaveEmc(Item item) {
		return item != null && doesItemHaveEmc(new ItemStack(item));
	}

	public static boolean doesItemHaveEmc(ItemStack stack) {
		if (stack == null || stack.getItem() == null)
			return false;

		SimpleStack ss = SimpleStack.getFor(stack);

		if (!ss.isValid())
			return false;

		if (!stack.getHasSubtypes() && stack.getMaxDamage() != 0)
			ss.damage = 0;

		return EMCMapper.mapContains(ss);
	}

	// 返回 double 避免拆装箱
	public static double getEmcValue(Block block)
	{
		if (block == null) return 0.0;
		SimpleStack stack = new SimpleStack(new ItemStack(block));

		if (stack.isValid() && EMCMapper.mapContains(stack))
			return EMCMapper.getEmcValue(stack);

		return 0.0;
	}

	// 返回 double 避免拆装箱
	public static double getEmcValue(Item item)
	{
		if (item == null) return 0.0;
		SimpleStack stack = new SimpleStack(new ItemStack(item));

		if (stack.isValid() && EMCMapper.mapContains(stack))
			return EMCMapper.getEmcValue(stack);

		return 0.0;
	}

	/**
	 * Ignore stack size
	 */
	public static double getEmcValue(ItemStack stack)
	{
		if (stack == null || stack.getItem() == null) return 0.0;

		if (EFRHelper.isShulkerBox(stack))
			return EFRHelper.ShulkerBoxEMC(stack);

		if (ForestryHelper.isForestryBag(stack))
			return ForestryHelper.ForestryBagEMC(stack);

		if (GTItemHelper.isGTtool(stack))
			return GTItemHelper.GTtoolEMC(stack);

		SimpleStack ss = SimpleStack.getFor(stack);

		if (!ss.isValid()) return 0.0;

		if (EMCMapper.mapContains(ss))
		{
			return EMCMapper.getEmcValue(ss) + getEnchantEmcBonus(stack) + getStoredEMCBonus(stack);
		}

		if (!stack.getHasSubtypes() && stack.getMaxDamage() != 0)
		{
			//We don't have an emc value for id:metadata, so lets check if we have a value for id:0 and apply a damage multiplier based on that emc value.
			ss.damage = 0;
			if (EMCMapper.mapContains(ss)) {
				double emc = EMCMapper.getEmcValue(ss); // Double 换为 double……额……这算优化吗？

				int rest = (stack.getMaxDamage() - stack.getItemDamage());

				if (rest <= 0)
				{
					//Not Impossible. Don't use durability or enchants for emc calculation if this happens.
					return emc;
				}

				double result = emc / stack.getMaxDamage() * rest;

				result += getEnchantEmcBonus(stack) + getStoredEMCBonus(stack);

				return result;
			}
		}
		return 0.0;
	}

	public static double getEnchantEmcBonus(ItemStack stack)
	{
		// 绝大多数物品没有附魔，拦截 NBT 校验可避免底层 EnchantmentHelper 创建昂贵的 HashMap
		if (stack.stackTagCompound == null) return 0.0;
		if (!stack.stackTagCompound.hasKey("ench") && !stack.stackTagCompound.hasKey("StoredEnchantments")) return 0.0;
		if (EnchantmentBlacklist.contains(stack)) return 0.0;

		Map<Integer, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
		if (enchants.isEmpty()) return 0.0;

		double result = 0;
		for (Map.Entry<Integer, Integer> entry : enchants.entrySet())
		{
			Enchantment ench = Enchantment.enchantmentsList[entry.getKey()];

			if (ench != null && ench.getWeight() > 0) // 补充 null 校验，防止越界或模组冲突
			{
				result += (double) Constants.ENCH_EMC_BONUS / ench.getWeight() * entry.getValue();
			}
		}

		return result;
	}

	public static double getKleinStarMaxEmc(ItemStack stack)
	{
		return Constants.MAX_KLEIN_EMC[stack.getItemDamage()];
	}

	public static double getStoredEMCBonus(ItemStack stack) {
		if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("StoredEMC")) {
			return stack.stackTagCompound.getDouble("StoredEMC");
		}
		return 0.0;
	}
}
