package moze_intel.projecte.gameObjs.items.armor;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.utils.ChatHelper;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.EnumArmorType;
import moze_intel.projecte.utils.PEKeybind;
import moze_intel.projecte.utils.PlayerHelper;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

import java.util.List;

@Optional.InterfaceList(value = {@Optional.Interface(iface = "thaumcraft.api.nodes.IRevealer", modid = "Thaumcraft"), @Optional.Interface(iface = "thaumcraft.api.IGoggles", modid = "Thaumcraft")})
public class GemHelmet extends GemArmorBase implements IGoggles, IRevealer
{
	public GemHelmet()
	{
		super(EnumArmorType.HEAD);
	}

	public static boolean isNightVisionEnabled(ItemStack helm)
	{
		return helm.hasTagCompound() && helm.getTagCompound().hasKey("NightVision") && helm.getTagCompound().getBoolean("NightVision");
	}

	public static void toggleNightVision(ItemStack helm, EntityPlayer player)
	{
		if (!helm.hasTagCompound()) helm.setTagCompound(new NBTTagCompound());

		boolean value;
		if (helm.stackTagCompound.hasKey("NightVision"))
		{
			helm.stackTagCompound.setBoolean("NightVision", !helm.stackTagCompound.getBoolean("NightVision"));
			value = helm.stackTagCompound.getBoolean("NightVision");
		}
		else
		{
			helm.stackTagCompound.setBoolean("NightVision", false);
			value = false;
		}

		EnumChatFormatting e = value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		String s = value ? "pe.gem.enabled" : "pe.gem.disabled";
		player.addChatMessage(new ChatComponentTranslation("pe.gem.nightvision_tooltip").appendText(" ")
			.appendSibling(ChatHelper.modifyColor(new ChatComponentTranslation(s), e)));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltips, boolean unused)
	{
		tooltips.add(StatCollector.translateToLocal("pe.gem.helm.lorename"));
		tooltips.add(String.format(
			StatCollector.translateToLocal("pe.gem.nightvision.prompt"), ClientKeyHelper.getKeyName(Minecraft.getMinecraft().gameSettings.keyBindSneak), ClientKeyHelper.getKeyName(PEKeybind.ARMOR_TOGGLE)
		));

		EnumChatFormatting e = isNightVisionEnabled(stack) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		String s = isNightVisionEnabled(stack) ? "pe.gem.enabled" : "pe.gem.disabled";
		tooltips.add(StatCollector.translateToLocal("pe.gem.nightvision_tooltip") + " "
			+ e + StatCollector.translateToLocal(s));
	}

	@Override
	public void onArmorTick(World world, EntityPlayer player, ItemStack stack)
	{
		if (world.isRemote)
		{
			int x = MathHelper.floor_double(player.posX);
			int y = MathHelper.floor_double(player.posY - player.getYOffset());
			int z = MathHelper.floor_double(player.posZ);

			// 区块加载检测
			if (world.blockExists(x, y - 1, z))
			{
				Block b = world.getBlock(x, y - 1, z);
				if ((b == Blocks.water || b == Blocks.flowing_water) && world.getBlock(x, y, z) == Blocks.air)
				{
					if (!player.isSneaking())
					{
						player.motionY = 0.0d;
						player.fallDistance = 0.0f;
						player.onGround = true;
					}
				}
			}
		}
		else
		{
			PlayerTimers.activateHeal(player);
			if (player.getHealth() < player.getMaxHealth() && PlayerTimers.canHeal(player))
			{
				player.heal(2.0F);
			}

			if (isNightVisionEnabled(stack))
			{
				// 只在时间少于 11 秒时才刷新夜视
				if (!player.isPotionActive(Potion.nightVision) || player.getActivePotionEffect(Potion.nightVision).getDuration() < 220)
				{
					player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 300, 0, true));
				}
			}
			else if (player.isPotionActive(Potion.nightVision))
			{
				player.removePotionEffect(Potion.nightVision.id);
			}

			if (player.isInWater())
			{
				player.setAir(300);
			}
		}
	}

	@Override
	@Optional.Method(modid = "Thaumcraft")
	public boolean showIngamePopups(ItemStack stack, EntityLivingBase player) { return true; }
	@Override
	@Optional.Method(modid = "Thaumcraft")
	public boolean showNodes(ItemStack stack, EntityLivingBase player) { return true; }

	public void doZap(EntityPlayer player)
	{
		if (ProjectEConfig.offensiveAbilities)
		{
			Vec3 strikePos = PlayerHelper.getBlockLookingAt(player, 120.0F);
			if (strikePos != null)
			{
				player.worldObj.addWeatherEffect(new EntityLightningBolt(player.worldObj, strikePos.xCoord, strikePos.yCoord, strikePos.zCoord));
			}
		}
	}
}
