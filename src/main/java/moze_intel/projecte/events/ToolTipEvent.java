package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.gui.GUIPedestal;
import moze_intel.projecte.integration.helpers.GTItemHelper;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.oredict.OreDictionary;

import java.text.DecimalFormat;
import java.util.List;

@SideOnly(Side.CLIENT)
public class ToolTipEvent
{
	private static final DecimalFormat INT_FORMAT = new DecimalFormat("#,###");

	private static String formatEMC(double value) {
		return moze_intel.projecte.math.ExactEMCFormatter.compact(value);
	}

	private static String formatEMC(moze_intel.projecte.math.ExactEMC value) {
		return moze_intel.projecte.math.ExactEMCFormatter.compact(value);
	}

	@SubscribeEvent
	public void tTipEvent(ItemTooltipEvent event)
	{
		ItemStack current = event.itemStack;
		if (current == null)
			return;
		Item currentItem = current.getItem();
		if (currentItem == null)
			return;
		Block currentBlock = Block.getBlockFromItem(currentItem);

		if (currentBlock == ObjHandler.dmPedestal) {
			event.toolTip.add(StatCollector.translateToLocal("pe.pedestal.tooltip1"));
			event.toolTip.add(StatCollector.translateToLocal("pe.pedestal.tooltip2"));
		}

		if (currentItem == ObjHandler.manual)
			event.toolTip.add(StatCollector.translateToLocal("pe.manual.tooltip1"));

		if (ProjectEConfig.showPedestalTooltip && currentItem instanceof IPedestalItem ipi) {
			if (ProjectEConfig.showPedestalTooltipInGUI) {
				if (Minecraft.getMinecraft().currentScreen instanceof GUIPedestal) {
					event.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("pe.pedestal.on_pedestal") + " ");
					List<String> description = ipi.getPedestalDescription();
					if (description.isEmpty())
						event.toolTip.add(IPedestalItem.TOOLTIPDISABLED);
					else event.toolTip.addAll(description);
				}
			}
			else {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("pe.pedestal.on_pedestal") + " ");
				List<String> description = ipi.getPedestalDescription();
				if (description.isEmpty())
					event.toolTip.add(IPedestalItem.TOOLTIPDISABLED);
				else event.toolTip.addAll(description);
			}
		}

		if (ProjectEConfig.showUnlocalizedNames)
			event.toolTip.add("UN: " + Item.itemRegistry.getNameForObject(currentItem));

		if (ProjectEConfig.showODNames) {
			for (int id : OreDictionary.getOreIDs(current))
				event.toolTip.add("OD: " + OreDictionary.getOreName(id));

			if (currentBlock instanceof BlockFluidBase bfb)
				event.toolTip.add("Fluid: " + bfb.getFluid().getName());
		}

		if (ProjectEConfig.showEMCTooltip) {
			// 缓存
			String emcPrefix = StatCollector.translateToLocal("pe.emc.emc_tooltip_prefix");
			String stackEmcPrefix = StatCollector.translateToLocal("pe.emc.stackemc_tooltip_prefix");

			if (EMCHelper.doesItemHaveEmc(current))
			{
				moze_intel.projecte.math.ExactEMC value = EMCHelper.getEmcValueExact(current);

				event.toolTip.add(EnumChatFormatting.YELLOW + emcPrefix + " " + EnumChatFormatting.WHITE + formatEMC(value));

				if (current.stackSize > 1) {
					moze_intel.projecte.math.ExactEMC total = value.multiply(current.stackSize);
					event.toolTip.add(EnumChatFormatting.YELLOW + stackEmcPrefix + " " + EnumChatFormatting.WHITE + formatEMC(total));
				}
			}
			else if (GTItemHelper.isGTfluidDisplay(current)) {
				double value = GTItemHelper.GTfluidDisplayEMC(current);
				if (value != 0)
					event.toolTip.add(EnumChatFormatting.YELLOW + emcPrefix + " " + EnumChatFormatting.WHITE + formatEMC(value));
			}
		}

		if (ProjectEConfig.showStatTooltip) {
			/* Collector ToolTips */
			String unit = StatCollector.translateToLocal("pe.emc.name");
			String rate = StatCollector.translateToLocal("pe.emc.rate");
			// 提取共用字典
			String maxGenRate = StatCollector.translateToLocal("pe.emc.maxgenrate_tooltip");
			String maxStorage = StatCollector.translateToLocal("pe.emc.maxstorage_tooltip");
			String maxOutRate = StatCollector.translateToLocal("pe.emc.maxoutrate_tooltip");

			// 字符串拼接
			if (currentBlock == ObjHandler.energyCollector) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxGenRate + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK1_GEN + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK1_MAX + " " + unit);
			}
			else if (currentBlock == ObjHandler.collectorMK2) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxGenRate + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK2_GEN + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK2_MAX + " " + unit);
			}
			else if (currentBlock == ObjHandler.collectorMK3) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxGenRate + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK3_GEN + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.COLLECTOR_MK3_MAX + " " + unit);
			}

			/* Relay ToolTips */
			if (currentBlock == ObjHandler.relay) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxOutRate + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK1_OUTPUT + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK1_MAX + " " + unit);
			}
			else if (currentBlock == ObjHandler.relayMK2) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxOutRate + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK2_OUTPUT + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK2_MAX + " " + unit);
			}
			else if (currentBlock == ObjHandler.relayMK3) {
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxOutRate + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK3_OUTPUT + " " + rate);
				event.toolTip.add(EnumChatFormatting.DARK_PURPLE + maxStorage + EnumChatFormatting.BLUE + " " + Constants.RELAY_MK3_MAX + " " + unit);
			}
		}

		if (current.hasTagCompound()) {
			if (current.stackTagCompound.getBoolean("ProjectEBlock")) {
				event.toolTip.add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("pe.misc.wrenched_block"));

				// 复用 stackEMC
				double stackEMC = current.stackTagCompound.getDouble("EMC");
				if (stackEMC > 0) {
					String storedEmcTooltip = StatCollector.translateToLocal("pe.emc.storedemc_tooltip");
					event.toolTip.add(EnumChatFormatting.YELLOW + storedEmcTooltip + " " + EnumChatFormatting.RESET + formatEMC(stackEMC));
				}
			}
			if (current.getItem() instanceof IItemEmc || current.stackTagCompound.hasKey("StoredEMC")
				|| current.stackTagCompound.hasKey("StoredEMCExact")) {
				String value;
				if (current.stackTagCompound.hasKey("StoredEMCExact") || current.stackTagCompound.hasKey("StoredEMC"))
					value = formatEMC(EMCHelper.getStoredEMCBonusExact(current));
				else value = formatEMC(((IItemEmc) current.getItem()).getStoredEmc(current));
				event.toolTip.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("pe.emc.storedemc_tooltip") + " " + EnumChatFormatting.RESET + value);
			}

			if (current.stackTagCompound.hasKey("StoredXP")) {
				// 替换 String.format
				int storedXp = current.stackTagCompound.getInteger("StoredXP");
				event.toolTip.add(EnumChatFormatting.DARK_GREEN + StatCollector.translateToLocal("pe.misc.storedxp_tooltip") + " " + EnumChatFormatting.GREEN + INT_FORMAT.format(storedXp));
			}
		}
	}
}
