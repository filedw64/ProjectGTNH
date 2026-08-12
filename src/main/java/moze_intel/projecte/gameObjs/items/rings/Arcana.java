package moze_intel.projecte.gameObjs.items.rings;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IExtraFunction;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.api.item.IProjectileShooter;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.entity.EntityFireProjectile;
import moze_intel.projecte.gameObjs.entity.EntitySWRGProjectile;
import moze_intel.projecte.gameObjs.items.IFireProtector;
import moze_intel.projecte.gameObjs.items.IFlightProvider;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.utils.PlayerHelper;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class Arcana extends ItemPE implements IBauble, IModeChanger, IFlightProvider, IFireProtector, IExtraFunction, IProjectileShooter
{
	private IIcon[] icons = new IIcon[4];
	private IIcon[] iconsOn = new IIcon[4];

	public Arcana()
	{
		super();
		setUnlocalizedName("arcana_ring");
		setMaxStackSize(1);
		setNoRepair();
		setContainerItem(this);
	}

	@Override
	public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack)
	{
		return false;
	}

	@Override
	public byte getMode(ItemStack stack)
	{
		return (byte)stack.getItemDamage();
	}

	// Shift+切换键 控制开关，单按切换键 循环模式
	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

		if (player.isSneaking())
		{
			boolean active = stack.getTagCompound().getBoolean("Active");
			stack.getTagCompound().setBoolean("Active", !active);
			player.worldObj.playSoundAtEntity(player, !active ? "projecte:item.peheal" : "projecte:item.peuncharge", 1.0F, 1.0F);
		}
		else
		{
			stack.setItemDamage((stack.getItemDamage() + 1) % 4);
		}
	}

	// 被动优化
	private void tick(ItemStack stack, World world, EntityPlayerMP player)
	{
		if(stack.getTagCompound().getBoolean("Active"))
		{
			AxisAlignedBB box = player.boundingBox.expand(5, 5, 5);
			switch(stack.getItemDamage())
			{
				case 0: // Zero
					if (ProjectEConfig.zeroRingPlaceSnow) WorldHelper.freezeInBoundingBox(world, box, player, true);
					for (EntityLivingBase ent : (List<EntityLivingBase>) world.getEntitiesWithinAABB(EntityLivingBase.class, box))
					{
						if (ent instanceof IMob && (!ent.isPotionActive(Potion.moveSlowdown) || ent.getActivePotionEffect(Potion.moveSlowdown).getDuration() < 10))
						{
							ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 2));
						}
					}
					break;
				case 1: // Ignition
					if (ProjectEConfig.ignitionRingIgniteBlocks) WorldHelper.igniteNearby(world, player);
					for (EntityLivingBase ent : (List<EntityLivingBase>) world.getEntitiesWithinAABB(EntityLivingBase.class, box))
					{
						if (ent instanceof IMob && !ent.isBurning()) ent.setFire(3);
					}
					break;
				case 2: // Harvest
					WorldHelper.growNearbyRandomly(true, world, player.posX, player.posY, player.posZ, player);
					break;
				case 3: // SWRG
					WorldHelper.repelEntitiesInAABBFromPoint(world, box, player.posX, player.posY, player.posZ, true);
					break;
			}
		}
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held)
	{
		if(stack.stackTagCompound == null) stack.setTagCompound(new NBTTagCompound());
		if(world.isRemote || slot > 8 || !(entity instanceof EntityPlayerMP)) return;
		tick(stack, world, (EntityPlayerMP)entity);
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public BaubleType getBaubleType(ItemStack stack)
	{
		return BaubleType.RING;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase entity)
	{
		if(stack.stackTagCompound == null) stack.setTagCompound(new NBTTagCompound());
		if(entity.worldObj.isRemote || !(entity instanceof EntityPlayerMP)) return;
		tick(stack, entity.worldObj, (EntityPlayerMP)entity);
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onEquipped(ItemStack stack, EntityLivingBase player) {}
	@Override
	@Optional.Method(modid = "Baubles")
	public void onUnequipped(ItemStack stack, EntityLivingBase player) {}
	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canEquip(ItemStack stack, EntityLivingBase player) { return true; }
	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canUnequip(ItemStack stack, EntityLivingBase player) { return true; }

	@Override
	public IIcon getIcon(ItemStack stack, int pass)
	{
		return getIconIndex(stack);
	}

	@Override
	public IIcon getIconIndex(ItemStack stack)
	{
		boolean active = stack.hasTagCompound() && stack.getTagCompound().getBoolean("Active");
		return (active ? iconsOn : icons)[MathHelper.clamp_int(stack.getItemDamage(), 0, 3)];
	}

	@Override
	public void registerIcons(IIconRegister register)
	{
		for(int i = 0; i < 4; i++) icons[i] = register.registerIcon(this.getTexture("rings", "arcana_" + i));
		for(int i = 0; i < 4; i++) iconsOn[i] = register.registerIcon(this.getTexture("rings", "arcana_" + i + "_on"));
		itemIcon = icons[0];
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b)
	{
		if(stack.hasTagCompound())
		{
			if(!stack.stackTagCompound.getBoolean("Active"))
			{
				list.add(EnumChatFormatting.RED + StatCollector.translateToLocal("pe.arcana.inactive"));
			}
			else
			{
				list.add(StatCollector.translateToLocal("pe.arcana.mode") + EnumChatFormatting.AQUA + StatCollector.translateToLocal("pe.arcana.mode." + stack.getItemDamage()));
			}
		}
	}

	// Shift+右键
	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if(!world.isRemote && stack.hasTagCompound() && stack.getTagCompound().getBoolean("Active") && player.isSneaking())
		{
			// Config 拦截
			if (!ProjectEConfig.enableArcanaShiftRMB) return stack;

			int mode = stack.getItemDamage();
			AxisAlignedBB bigBox = player.boundingBox.expand(10, 10, 10);
			List<EntityLivingBase> hostiles = world.getEntitiesWithinAABB(EntityLivingBase.class, bigBox);

			switch(mode)
			{
				case 0: // Zero
					terraform(world, bigBox, 0);
					for (EntityLivingBase ent : hostiles)
					{
						if (ent instanceof IMob)
						{
							if (ent.getEntityData().getBoolean("PE_InnerFire")) {
								ent.setDead(); // 即死
							} else {
								ent.getEntityData().setBoolean("PE_InnerIce", true);
								ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, Integer.MAX_VALUE, 9)); // 永久缓慢 10
							}
						}
					}
					world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
					break;

				case 1: // Ignition
					terraform(world, bigBox, 1);
					for (EntityLivingBase ent : hostiles)
					{
						if (ent instanceof IMob)
						{
							if (ent.getEntityData().getBoolean("PE_InnerIce")) {
								ent.setDead(); // 冰火交汇：即死！
							} else {
								ent.getEntityData().setBoolean("PE_InnerFire", true);
							}
						}
					}
					world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
					break;

				case 2: // Harvest
					player.getFoodStats().addStats(20, 20.0F);
					world.playSoundAtEntity(player, "projecte:item.peheal", 1.0F, 1.0F);
					break;

				case 3: // SWRG
					for (EntityLivingBase ent : hostiles)
					{
						if (ent instanceof IMob)
						{
							world.addWeatherEffect(new EntityLightningBolt(world, ent.posX, ent.posY, ent.posZ));
							ent.motionY += 2.5D; // 卷上高空摔死
							ent.velocityChanged = true;
						}
					}
					world.playSoundAtEntity(player, "projecte:item.pewindmagic", 1.0F, 1.0F);
					break;
			}
		}
		return stack;
	}

	private void terraform(World world, AxisAlignedBB box, int type)
	{
		int minX = MathHelper.floor_double(box.minX), minY = MathHelper.floor_double(box.minY), minZ = MathHelper.floor_double(box.minZ);
		int maxX = MathHelper.floor_double(box.maxX), maxY = MathHelper.floor_double(box.maxY), maxZ = MathHelper.floor_double(box.maxZ);

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (!world.blockExists(x, y, z)) continue;
					Block block = world.getBlock(x, y, z);

					if (type == 0 && (block == Blocks.water || block == Blocks.flowing_water)) {
						world.setBlock(x, y, z, Blocks.ice, 0, 3);
					}
					else if (type == 1) {
						Material mat = block.getMaterial();
						if (block == Blocks.grass) world.setBlock(x, y, z, Blocks.dirt, 0, 3);
						else if (block == Blocks.cobblestone) world.setBlock(x, y, z, Blocks.stone, 0, 3);
						else if (block.isWood(world, x, y, z) || mat == Material.plants || mat == Material.leaves || mat == Material.vine) {
							world.setBlockToAir(x, y, z);
						}
					}
				}
			}
		}
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player)
	{
		World world = player.worldObj;
		if(world.isRemote) return;

		switch(stack.getItemDamage())
		{
			case 1: // ignition
				switch(MathHelper.floor_double((double)(player.rotationYaw * 4.0F / 360.0F) + 0.5) & 3)
				{
					case 0: case 2:
					for(int x = (int) (player.posX - 30); x <= player.posX + 30; x++)
						for(int y = (int) (player.posY - 5); y <= player.posY + 5; y++)
							for(int z = (int) (player.posZ - 3); z <= player.posZ + 3; z++)
								if(world.isAirBlock(x, y, z)) PlayerHelper.checkedPlaceBlock(((EntityPlayerMP) player), x, y, z, Blocks.fire, 0);
					break;
					case 1: case 3:
					for(int x = (int) (player.posX - 3); x <= player.posX + 3; x++)
						for(int y = (int) (player.posY - 5); y <= player.posY + 5; y++)
							for(int z = (int) (player.posZ - 30); z <= player.posZ + 30; z++)
								if(world.isAirBlock(x, y, z)) PlayerHelper.checkedPlaceBlock(((EntityPlayerMP) player), x, y, z, Blocks.fire, 0);
					break;
				}
				break;
		}
	}

	@Override
	public boolean shootProjectile(EntityPlayer player, ItemStack stack)
	{
		World world = player.worldObj;
		if(world.isRemote) return false;

		switch(stack.getItemDamage())
		{
			case 0:
				world.spawnEntityInWorld(new EntitySnowball(world, player));
				world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F);
				break;
			case 1:
				world.spawnEntityInWorld(new EntityFireProjectile(world, player));
				world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
				break;
			case 3:
				world.spawnEntityInWorld(new EntitySWRGProjectile(world, player));
				break;
		}
		return true;
	}

	@Override
	public boolean canProtectAgainstFire(ItemStack stack, EntityPlayerMP player) { return true; }
	@Override
	public boolean canProvideFlight(ItemStack stack, EntityPlayerMP player) { return true; }
}
