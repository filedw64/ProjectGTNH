package moze_intel.projecte.gameObjs.items.rings;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.ItemCharge;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.utils.MathUtils;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class Zero extends ItemCharge implements IModeChanger, IBauble, IPedestalItem
{
	@SideOnly(Side.CLIENT)
	private IIcon ringOff;
	@SideOnly(Side.CLIENT)
	private IIcon ringOn;

	public Zero()
	{
		super("zero_ring", (byte)4);
		this.setContainerItem(this);
		this.setNoRepair();
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5)
	{
		super.onUpdate(stack, world, entity, par4, par5);

		if (world.isRemote || !(entity instanceof EntityPlayer) || par4 > 8 || stack.getItemDamage() == 0)
		{
			return;
		}

		EntityPlayer player = (EntityPlayer) entity;
		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(player.posX - 3, player.posY - 3, player.posZ - 3, player.posX + 3, player.posY + 3, player.posZ + 3);

		// 根据 Config 决定是否在周围铺雪
		if (ProjectEConfig.zeroRingPlaceSnow)
		{
			WorldHelper.freezeInBoundingBox(world, box, player, true);
		}

		// 给周围敌对生物附加缓慢 III
		List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
		for (EntityLivingBase ent : entities)
		{
			if (ent instanceof IMob)
			{
				// 只有当没有缓慢效果或剩余时间小于10tick时才重新添加
				if (!ent.isPotionActive(Potion.moveSlowdown) || ent.getActivePotionEffect(Potion.moveSlowdown).getDuration() < 10)
				{
					ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 2));
				}
			}
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			int offset = 3 + this.getCharge(stack);
			AxisAlignedBB box = player.boundingBox.expand(offset, offset, offset);

			if (player.isSneaking() && stack.getItemDamage() != 0)
			{
				// Config 拦截
				if (!ProjectEConfig.enableRingShiftRMB) return stack;

				// Shift+右键大范围水源结冰，敌对生物永久缓慢 V
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
							if (block == Blocks.water || block == Blocks.flowing_water)
							{
								world.setBlock(x, y, z, Blocks.ice, 0, 3);
							}
						}
					}
				}

				List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
				for (EntityLivingBase ent : entities)
				{
					if (ent instanceof IMob)
					{
						// Integer.MAX_VALUE 时间的缓慢 5 (Amplifier 4)
						ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, Integer.MAX_VALUE, 4));
					}
				}
				world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
			}
			else
			{
				// 不潜行时，释放原版冻结
				world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
				WorldHelper.freezeInBoundingBox(world, box, player, false);
			}
		}

		return stack;
	}

	@Override
	public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack)
	{
		return false;
	}

	@Override
	public byte getMode(ItemStack stack)
	{
		return (byte) stack.getItemDamage();
	}

	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		stack.setItemDamage(stack.getItemDamage() == 0 ? 1 : 0);
	}

	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int dmg)
	{
		return dmg == 0 ? ringOff : ringOn;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		ringOn = register.registerIcon(this.getTexture("rings", "zero_on"));
		ringOff = register.registerIcon(this.getTexture("rings", "zero_off"));
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
		if (!world.isRemote && ProjectEConfig.zeroPedCooldown != -1)
		{
			DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
			if (tile.getActivityCooldown() == 0) {
				AxisAlignedBB aabb = tile.getEffectBounds();
				WorldHelper.freezeInBoundingBox(world, aabb, null, false);
				List<Entity> list = world.getEntitiesWithinAABB(Entity.class, aabb);
				for (Entity ent : list)
				{
					if (ent.isBurning())
					{
						ent.extinguish();
					}
				}
				tile.setActivityCooldown(ProjectEConfig.zeroPedCooldown);
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
		if (ProjectEConfig.zeroPedCooldown != -1) {
			list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.zero.pedestal1"));
			list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.zero.pedestal2"));
			list.add(EnumChatFormatting.BLUE + String.format(
				StatCollector.translateToLocal("pe.zero.pedestal3"), MathUtils.tickToSecFormatted(ProjectEConfig.zeroPedCooldown)));
		}
		return list;
	}
}
