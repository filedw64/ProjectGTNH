package moze_intel.projecte.gameObjs.items.rings;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import cpw.mods.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.api.item.IProjectileShooter;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.entity.EntityFireProjectile;
import moze_intel.projecte.gameObjs.items.IFireProtector;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.utils.MathUtils;
import moze_intel.projecte.utils.PlayerHelper;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class Ignition extends RingToggle implements IBauble, IPedestalItem, IFireProtector, IProjectileShooter
{
	public Ignition()
	{
		super("ignition");
		this.setNoRepair();
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int inventorySlot, boolean par5)
	{
		if (world.isRemote || inventorySlot > 8 || !(entity instanceof EntityPlayer)) return;

		super.onUpdate(stack, world, entity, inventorySlot, par5);
		EntityPlayerMP player = (EntityPlayerMP)entity;

		if (stack.getItemDamage() != 0)
		{
			if (getEmc(stack) == 0 && !consumeFuel(player, stack, 64, false))
			{
				stack.setItemDamage(0);
			}
			else
			{
				// 可配置是否点燃方块
				if (ProjectEConfig.ignitionRingIgniteBlocks)
				{
					WorldHelper.igniteNearby(world, player);
				}

				// 点燃周围的敌对生物
				AxisAlignedBB box = player.boundingBox.expand(5, 5, 5);
				List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
				for (EntityLivingBase ent : entities)
				{
					if (ent instanceof IMob)
					{
						if (!ent.isBurning())
						{
							ent.setFire(3);
						}
					}
				}

				removeEmc(stack, 0.32F);
			}
		}
		else
		{
			WorldHelper.extinguishNearby(world, player);
		}
	}

	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		if (stack.getItemDamage() == 0)
		{
			if (getEmc(stack) == 0 && !consumeFuel(player, stack, 64, false))
			{
				//NOOP (used to be sounds)
			}
			else
			{
				stack.setItemDamage(1);
			}
		}
		else
		{
			stack.setItemDamage(0);
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			if (player.isSneaking() && stack.getItemDamage() != 0)
			{
				// Config 拦截
				if (!ProjectEConfig.enableRingShiftRMB) return stack;

				// Shift+右键
				AxisAlignedBB box = player.boundingBox.expand(5, 5, 5);
				int minX = MathHelper.floor_double(box.minX);
				int minY = MathHelper.floor_double(box.minY);
				int minZ = MathHelper.floor_double(box.minZ);
				int maxX = MathHelper.floor_double(box.maxX);
				int maxY = MathHelper.floor_double(box.maxY);
				int maxZ = MathHelper.floor_double(box.maxZ);

				for (int x = minX; x <= maxX; x++)
				{
					for (int y = minY; y <= maxY; y++)
					{
						for (int z = minZ; z <= maxZ; z++)
						{
							if (!world.blockExists(x, y, z)) continue;
							Block block = world.getBlock(x, y, z);
							Material mat = block.getMaterial();

							if (block == Blocks.grass)
							{
								world.setBlock(x, y, z, Blocks.dirt, 0, 3);
							}
							else if (block == Blocks.cobblestone)
							{
								world.setBlock(x, y, z, Blocks.stone, 0, 3);
							}
							// 完美囊括原木、草、树叶、藤蔓和各类植物
							else if (block.isWood(world, x, y, z) || mat == Material.plants || mat == Material.leaves || mat == Material.vine)
							{
								world.setBlockToAir(x, y, z);
							}
						}
					}
				}

				List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
				for (EntityLivingBase ent : entities)
				{
					if (ent instanceof IMob)
					{
						ent.getEntityData().setBoolean("PE_HellFire", true);
						ent.setFire(9999);
					}
				}
				world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);

				// 拦截原版的点燃 TNT 射线检测逻辑
				return stack;
			}

			// 原版主动
			MovingObjectPosition mop = getMovingObjectPositionFromPlayer(world, player, false);
			if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
			{
				if (world.getBlock(mop.blockX, mop.blockY, mop.blockZ) instanceof BlockTNT
					&& PlayerHelper.hasBreakPermission(((EntityPlayerMP) player), mop.blockX, mop.blockY, mop.blockZ))
				{
					((BlockTNT) world.getBlock(mop.blockX, mop.blockY, mop.blockZ)).func_150114_a(world, mop.blockX, mop.blockY, mop.blockZ, 1, player);
					world.setBlockToAir(mop.blockX, mop.blockY, mop.blockZ);
				}
			}
			world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
		}
		return stack;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public baubles.api.BaubleType getBaubleType(ItemStack itemstack)
	{
		return BaubleType.RING;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase player)
	{
		this.onUpdate(stack, player.worldObj, player, 0, false);
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canEquip(ItemStack itemstack, EntityLivingBase player)
	{
		return true;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player)
	{
		return true;
	}

	@Override
	public void updateInPedestal(World world, int x, int y, int z)
	{
		if (!world.isRemote && ProjectEConfig.ignitePedCooldown != -1)
		{
			DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
			if (tile.getActivityCooldown() == 0)
			{
				List<EntityLiving> list = world.getEntitiesWithinAABB(EntityLiving.class, tile.getEffectBounds());
				for (EntityLiving living : list)
				{
					living.attackEntityFrom(DamageSource.inFire, 3.0F);
					living.setFire(8);
				}

				tile.setActivityCooldown(ProjectEConfig.ignitePedCooldown);
			}
			else
			{
				tile.decrementActivityCooldown();
			}
		}
	}

	@Override
	public List<String> getPedestalDescription()
	{
		List<String> list = Lists.newArrayList();
		if (ProjectEConfig.ignitePedCooldown != -1)
		{
			list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.ignition.pedestal1"));
			list.add(EnumChatFormatting.BLUE + String.format(
				StatCollector.translateToLocal("pe.ignition.pedestal2"), MathUtils.tickToSecFormatted(ProjectEConfig.ignitePedCooldown)));
		}
		return list;
	}

	@Override
	public boolean shootProjectile(EntityPlayer player, ItemStack stack)
	{
		World world = player.worldObj;

		if(world.isRemote) return false;

		EntityFireProjectile fire = new EntityFireProjectile(world, player);
		world.spawnEntityInWorld(fire);

		return true;
	}

	@Override
	public boolean canProtectAgainstFire(ItemStack stack, EntityPlayerMP player)
	{
		return true;
	}
}
