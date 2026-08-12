package moze_intel.projecte.utils;

import com.google.common.collect.Maps;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.integration.EtFuturum.EFRHelper;
import moze_intel.projecte.integration.Forestry.ForestryHelper;
import moze_intel.projecte.integration.GregTech.GTItemHelper;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
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
		LinkedHashMap<Integer, Integer> map = Maps.newLinkedHashMap();
		boolean metRequirement = false;
        double emcConsumed = 0;

		for (int i = 0; i < inv.getSizeInventory(); i++)
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
			else if (!metRequirement)
			{
				if (FuelMapper.isStackFuel(stack))
				{
                    double emc = getEmcValue(stack);
					int toRemove = ((int) Math.ceil((minFuel - emcConsumed) / emc));

					if (stack.stackSize >= toRemove)
					{
						map.put(i, toRemove);
						emcConsumed += emc * toRemove;
						metRequirement = true;
					}
					else
					{
						map.put(i, stack.stackSize);
						emcConsumed += emc * stack.stackSize;

						if (emcConsumed >= minFuel)
						{
							metRequirement = true;
						}
					}

				}
			}
		}

		if (metRequirement)
		{
			for (Map.Entry<Integer, Integer> entry : map.entrySet())
			{
				inv.decrStackSize(entry.getKey(), entry.getValue());
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

	public static Double getEmcValue(Block Block)
	{
		SimpleStack stack = new SimpleStack(new ItemStack(Block));

		if (stack.isValid() && EMCMapper.mapContains(stack))
			return EMCMapper.getEmcValue(stack);

		return 0.0;
	}

	public static Double getEmcValue(Item item)
	{
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
                Double emc = EMCMapper.getEmcValue(ss);

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

	public static Double getEnchantEmcBonus(ItemStack stack)
	{
        if (EnchantmentBlacklist.contains(stack)) return 0.0;

        double result = 0;
		Map<Integer, Integer> enchants = EnchantmentHelper.getEnchantments(stack);

		if (enchants.isEmpty()) return 0.0;

        for (Map.Entry<Integer, Integer> entry : enchants.entrySet())
        {
            Enchantment ench = Enchantment.enchantmentsList[entry.getKey()];

            if (ench.getWeight() == 0)
            {
                continue;
            }

            result += (double) Constants.ENCH_EMC_BONUS / ench.getWeight() * entry.getValue();
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
