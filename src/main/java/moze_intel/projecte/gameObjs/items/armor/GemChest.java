package moze_intel.projecte.gameObjs.items.armor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.IFireProtector;
import moze_intel.projecte.gameObjs.items.IFlightProvider;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.utils.EnumArmorType;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

public class GemChest extends GemArmorBase implements IFireProtector, IFlightProvider
{
	public GemChest() {
		super(EnumArmorType.CHEST);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltips, boolean unused) {
		tooltips.add(StatCollector.translateToLocal("pe.gem.chest.lorename"));
	}

	private final static double SPEEDBOOST = 0.18;

	@Override
	public void onArmorTick(World world, EntityPlayer player, ItemStack chest)
	{
		if (world.isRemote)
		{
			int x = MathHelper.floor_double(player.posX);
			int y = MathHelper.floor_double(player.posY - player.getYOffset());
			int z = MathHelper.floor_double(player.posZ);

			if (world.blockExists(x, y - 1, z))
			{
				Block b = world.getBlock(x, y - 1, z);
				if ((b == Blocks.lava || b == Blocks.flowing_lava) && world.getBlock(x, y, z) == Blocks.air)
				{
					if (!player.isSneaking())
					{
						player.motionY = 0.0d;
						player.fallDistance = 0.0f;
						player.onGround = true;
					}
				}
			}

			// 修复与强化：基于玩家朝向（Yaw）的三角函数推力，彻底解决按W倒退的问题
			if (player.capabilities.isFlying && player.moveForward > 0) {
				final float yaw = (float) (player.rotationYaw * Math.PI / 180.0D);
				if (player.motionX * player.motionX + player.motionZ * player.motionZ < 3.0) {
					player.motionX -= MathHelper.sin(yaw) * SPEEDBOOST;
					player.motionZ += MathHelper.cos(yaw) * SPEEDBOOST;
				}
			}
		}
		else if (player instanceof EntityPlayerMP playerMP) {
			PlayerTimers.activateFeed(playerMP);
			if (player.getFoodStats().needFood() && PlayerTimers.canFeed(playerMP))
				player.getFoodStats().addStats(2, 10);
		}
	}

	public void doExplode(EntityPlayer player) {
		if (ProjectEConfig.offensiveAbilities)
			WorldHelper.createNovaExplosion(player.worldObj, player, player.posX, player.posY, player.posZ, 9.0F);
	}

	@Override
	public boolean canProtectAgainstFire(ItemStack stack, EntityPlayerMP player) {
		return player.getCurrentArmor(2) == stack;
	}

	@Override
	public boolean canProvideFlight(ItemStack stack, EntityPlayerMP player) {
		return player.getCurrentArmor(2) == stack;
	}
}
