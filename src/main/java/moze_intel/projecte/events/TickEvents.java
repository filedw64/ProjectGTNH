package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import moze_intel.projecte.handlers.PlayerChecks;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.gameObjs.items.armor.GemArmorBase;

public class TickEvents
{
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
		{
			PlayerTimers.update();
		}
	}

	@SubscribeEvent
	public void playerTick(TickEvent.PlayerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END && event.side == Side.SERVER)
		{
			PlayerChecks.update(((EntityPlayerMP) event.player));
		}
	}

	// 将高频的事件合并，减少 Forge 事件总线的反射调用开销
	@SubscribeEvent
	public void onLivingUpdate(LivingEvent.LivingUpdateEvent event)
	{
		// 尽早阻断客户端执行
		if (event.entity.worldObj.isRemote) return;

		NBTTagCompound data = event.entityLiving.getEntityData();

		if (data.getBoolean("PE_HellFire") && !event.entityLiving.isBurning())
		{
			event.entityLiving.setFire(9999);
		}

		boolean hasFire = data.getBoolean("PE_InnerFire");
		boolean hasIce = data.getBoolean("PE_InnerIce");

		// 绝大多数实体没有这些 buff，将其包裹在一个判断中
		if (hasFire || hasIce)
		{
			// 秒杀
			if (hasFire && hasIce)
			{
				event.entityLiving.setDead();
				return;
			}

			// 将每秒执行一次的判断外提
			if (event.entityLiving.ticksExisted % 20 == 0)
			{
				if (hasFire)
				{
					float maxHp = event.entityLiving.getMaxHealth();
					event.entityLiving.attackEntityFrom(DamageSource.inFire, maxHp * 0.33F);
					if (event.entityLiving.isBurning()) event.entityLiving.extinguish();
				}

				if (hasIce)
				{
					IAttributeInstance maxHealthAttr = event.entityLiving.getEntityAttribute(SharedMonsterAttributes.maxHealth);
					if (maxHealthAttr != null)
					{
						double currentMax = maxHealthAttr.getBaseValue();
						double newMax = Math.max(1.0D, currentMax * 0.99D);
						maxHealthAttr.setBaseValue(newMax);

						if (event.entityLiving.getHealth() > newMax)
						{
							event.entityLiving.setHealth((float)newMax);
						}
					}
				}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onGemArmorAbsoluteDefense(LivingAttackEvent event)
	{
		if (event.entityLiving instanceof EntityPlayer)
		{
			EntityPlayer player = (EntityPlayer) event.entityLiving;
			if (GemArmorBase.hasFullSet(player) && !event.source.canHarmInCreative())
			{
				event.setCanceled(true);
			}
		}
	}
}
