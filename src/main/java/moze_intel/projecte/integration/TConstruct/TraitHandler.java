package moze_intel.projecte.integration.TConstruct;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.api.ProjectEAPI;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import tconstruct.library.ActiveToolMod;
import tconstruct.library.tools.ToolCore;

public class TraitHandler extends ActiveToolMod {

	private boolean hasPEMaterial(NBTTagCompound toolTags, int matId) {
		if (toolTags == null) return false;
		return toolTags.getInteger("Head") == matId ||
			toolTags.getInteger("Handle") == matId ||
			toolTags.getInteger("Accessory") == matId ||
			toolTags.getInteger("Extra") == matId;
	}

	// 亘古永恒
	@Override
	public boolean damageTool(ItemStack stack, int damage, EntityLivingBase entity) {
		NBTTagCompound tags = stack.getTagCompound().getCompoundTag("InfiTool");
		if (hasPEMaterial(tags, TConstructInit.MAT_ID_DARK_MATTER) ||
			hasPEMaterial(tags, TConstructInit.MAT_ID_RED_MATTER)) {
			return true;
		}
		return super.damageTool(stack, damage, entity);
	}

	// 制服 & 主宰
	@Override
	public int attackDamage(int modDamage, int currentDamage, ToolCore tool, NBTTagCompound tags, NBTTagCompound toolTags, ItemStack stack, EntityLivingBase player, Entity entity) {
		int finalDamage = currentDamage;
		boolean hasDM = hasPEMaterial(toolTags, TConstructInit.MAT_ID_DARK_MATTER);
		boolean hasRM = hasPEMaterial(toolTags, TConstructInit.MAT_ID_RED_MATTER);

		if ((hasDM || hasRM) && entity instanceof EntityLivingBase) {
			EntityLivingBase target = (EntityLivingBase) entity;

			// 制服 / 制服II
			int armorCount = 0;
			for (int i = 1; i <= 4; i++) {
				if (target.getEquipmentInSlot(i) != null) armorCount++;
			}
			if (armorCount > 0) {
				float multiplier = hasRM ? (1.0f + 1.0f * armorCount) : (1.0f + 0.5f * armorCount);
				finalDamage = (int) (finalDamage * multiplier);
			}

			// 主宰 / 主宰II
			double maxHealth = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).getAttributeValue();
			if (hasRM) {
				finalDamage = (int) (finalDamage * Math.pow( (maxHealth + 1), 2.0 / 3.0));
			} else if (hasDM) {
				double log2Health = Math.log(maxHealth + 1) / Math.log(2);
				finalDamage = (int) (finalDamage * Math.max(1.0, log2Health));
			}
		}
		return finalDamage;
	}

	/*
	// 飞仙
	@Override
	public void updateTool(ToolCore tool, ItemStack stack, World world, Entity entity) {
		if (entity instanceof EntityPlayer && !world.isRemote) {
			EntityPlayer player = (EntityPlayer) entity;
			NBTTagCompound tags = stack.getTagCompound().getCompoundTag("InfiTool");
			if (hasPEMaterial(tags, ProjectETiCIntegration.MAT_ID_DARK_MATTER) || hasPEMaterial(tags, ProjectETiCIntegration.MAT_ID_RED_MATTER)) {
				if (player.getHeldItem() == stack) {
					player.capabilities.allowFlying = true;
					player.sendPlayerAbilities();
				} else if (!player.capabilities.isCreativeMode) {
					player.capabilities.allowFlying = false;
					player.capabilities.isFlying = false;
					player.sendPlayerAbilities();
				}
			}
		}
	}
	 */

	// 被遗忘者系列
	private void handleEMCGeneration(EntityPlayer player, ItemStack stack) {
		if (stack == null || !stack.hasTagCompound() || player.worldObj.isRemote) return; // 确保只在服务端执行
		NBTTagCompound tags = stack.getTagCompound().getCompoundTag("InfiTool");

		if (hasPEMaterial(tags, TConstructInit.MAT_ID_RED_MATTER)) {
			// 被遗忘者从未被遗忘
			moze_intel.projecte.api.proxy.ITransmutationProxy proxy = ProjectEAPI.getTransmutationProxy();
			java.util.UUID uuid = player.getUniqueID();
			double currentEmc = proxy.getEMC(uuid);

			// 确保玩家在线且数据正常
			if (!Double.isNaN(currentEmc)) {
				proxy.setEMC(uuid, currentEmc + 4096.0);
			}

		} else if (hasPEMaterial(tags, TConstructInit.MAT_ID_DARK_MATTER)) {
			// 被遗忘者自天堂回归
			double emcToAdd = 256.0;

			// 遍历玩家背包，寻找能存储EMC的物品
			for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
				ItemStack invStack = player.inventory.getStackInSlot(i);
				if (invStack != null && invStack.getItem() instanceof moze_intel.projecte.api.item.IItemEmc) {
					moze_intel.projecte.api.item.IItemEmc emcItem = (moze_intel.projecte.api.item.IItemEmc) invStack.getItem();

					// 尝试向该物品充入EMC，返回值为实际成功充入的量
					double added = emcItem.addEmc(invStack, emcToAdd);
					emcToAdd -= added;

					if (emcToAdd <= 0) {
						break;
					}
				}
			}
		}
	}


	// 拦截挖掘
	@SubscribeEvent
	public void onBlockBreak(BlockEvent.BreakEvent event) {
		EntityPlayer player = event.getPlayer();
		if (player != null && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ToolCore) {
			handleEMCGeneration(player, player.getHeldItem());
		}
	}

	// 拦截互动
	@SubscribeEvent
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) { // 避免过度触发
			EntityPlayer player = event.entityPlayer;
			if (player != null && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ToolCore) {
				handleEMCGeneration(player, player.getHeldItem());
			}
		}
	}

	@SubscribeEvent
	public void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
		EntityPlayer player = event.entityPlayer;
		if (player != null && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ToolCore) {
			handleEMCGeneration(player, player.getHeldItem());
		}
	}

	// Tooltip 渲染
	@SubscribeEvent
	public void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
		if (event.itemStack == null || !event.itemStack.hasTagCompound()) return;
		NBTTagCompound tags = event.itemStack.getTagCompound().getCompoundTag("InfiTool");

		// 确保是匠魂工具并且有NBT
		if (tags != null && tags.hasKey("Head")) {
			boolean hasDM = hasPEMaterial(tags, TConstructInit.MAT_ID_DARK_MATTER);
			boolean hasRM = hasPEMaterial(tags, TConstructInit.MAT_ID_RED_MATTER);

			if (hasDM || hasRM) {
				// 共有特性
				event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.gengu"));
				event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.feixian"));

				if (hasRM) {
					// 红物质独有
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.zhifu2"));
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.zhuzai2"));
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.forgotten.rm"));
				} else {
					// 暗物质独有
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.zhifu"));
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.zhuzai"));
					event.toolTip.add(net.minecraft.util.StatCollector.translateToLocal("tic.trait.pe.forgotten.dm"));
				}
			}
		}
	}
}
