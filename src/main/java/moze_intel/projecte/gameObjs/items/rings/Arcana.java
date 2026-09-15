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
	private final IIcon[] icons = new IIcon[4];
	private final IIcon[] iconsOn = new IIcon[4];

	public Arcana() {
		super();
		setUnlocalizedName("arcana_ring");
		setMaxStackSize(1);
		setNoRepair();
		setContainerItem(this);
	}

	@Override
	public boolean doesContainerItemLeaveCraftingGrid(ItemStack stack) {
		return false;
	}

	@Override
	public byte getMode(ItemStack stack) {
		return (byte)stack.getItemDamage();
	}

	// Shift+切换键 控制开关，单按切换键 循环模式
	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		if (stack.stackTagCompound == null)
			stack.stackTagCompound = new NBTTagCompound();

		if (player.isSneaking()) {
			boolean active = stack.stackTagCompound.getBoolean("Active");
			stack.stackTagCompound.setBoolean("Active", !active);
			player.worldObj.playSoundAtEntity(player, !active ? "projecte:item.peheal" : "projecte:item.peuncharge", 1.0F, 1.0F);
		}
		else stack.setItemDamage((stack.getItemDamage() + 1) % 4);
	}

	// 被动优化
	private void tick(ItemStack stack, World world, EntityPlayerMP player)
	{
		if (stack.stackTagCompound.getBoolean("Active"))
		{
			AxisAlignedBB box = player.boundingBox.expand(5, 5, 5);
			switch (stack.getItemDamage())
			{
				case 0: // Zero
					if (ProjectEConfig.zeroRingPlaceSnow)
						WorldHelper.freezeInBoundingBox(world, box, player, true);
					for (EntityLivingBase ent : world.getEntitiesWithinAABB(EntityLivingBase.class, box))
						if (ent instanceof IMob && (!ent.isPotionActive(Potion.moveSlowdown) || ent.getActivePotionEffect(Potion.moveSlowdown).getDuration() < 20))
							ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 2));
					break;
				case 1: // Ignition
					if (ProjectEConfig.ignitionRingIgniteBlocks)
						WorldHelper.igniteNearby(world, player);
					for (EntityLivingBase ent : world.getEntitiesWithinAABB(EntityLivingBase.class, box))
						if (ent instanceof IMob)
							ent.setFire(3);
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
		if (stack.stackTagCompound == null)
			stack.stackTagCompound = new NBTTagCompound();
		if (world.isRemote || slot > 8 || !(entity instanceof EntityPlayerMP entityPlayerMP)) return;
		tick(stack, world, entityPlayerMP);
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public BaubleType getBaubleType(ItemStack stack) {
		return BaubleType.RING;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase entity)
	{
		if (stack.stackTagCompound == null)
			stack.stackTagCompound = new NBTTagCompound();
		if (entity.worldObj.isRemote || !(entity instanceof EntityPlayerMP entityPlayerMP)) return;
		tick(stack, entity.worldObj, entityPlayerMP);
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
	public IIcon getIcon(ItemStack stack, int pass) {
		return getIconIndex(stack);
	}

	@Override
	public IIcon getIconIndex(ItemStack stack) {
		boolean active = stack.stackTagCompound != null && stack.stackTagCompound.getBoolean("Active");
		return (active ? iconsOn : icons)[MathHelper.clamp_int(stack.getItemDamage(), 0, 3)];
	}

	@Override
	public void registerIcons(IIconRegister register) {
		for (int i = 0; i < 4; i++)
			icons[i] = register.registerIcon(this.getTexture("rings", "arcana_" + i));
		for (int i = 0; i < 4; i++)
			iconsOn[i] = register.registerIcon(this.getTexture("rings", "arcana_" + i + "_on"));
		itemIcon = icons[0];
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b)
	{
		if (stack.stackTagCompound != null) {
			if (!stack.stackTagCompound.getBoolean("Active"))
				list.add(EnumChatFormatting.RED + StatCollector.translateToLocal("pe.arcana.inactive"));
			else list.add(StatCollector.translateToLocal("pe.arcana.mode") + EnumChatFormatting.AQUA + StatCollector.translateToLocal("pe.arcana.mode." + stack.getItemDamage()));
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote) {
			if (stack.stackTagCompound == null) {
				stack.stackTagCompound = new NBTTagCompound();
				stack.stackTagCompound.setBoolean("Active", true);
			}
			else stack.stackTagCompound.setBoolean("Active", !stack.stackTagCompound.getBoolean("Active"));
		}
		return stack;
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player)
	{
		World world = player.worldObj;
		if (world.isRemote || !(player instanceof EntityPlayerMP entityPlayerMP)) return;

		if (stack.getItemDamage() != 1) return;

		final int dir = MathHelper.floor_double(player.rotationYaw / 90.0 + 0.5) & 3;

		// ignition
		if (dir == 0 || dir == 2) {
			for (int x = (int) (player.posX - 30); x <= player.posX + 30; x++)
				for (int z = (int) (player.posZ - 3); z <= player.posZ + 3; z++)
					for (int y = (int) (player.posY - 5); y <= player.posY + 5; y++)
						if (world.isAirBlock(x, y, z))
							PlayerHelper.checkedPlaceBlock(entityPlayerMP, x, y, z, Blocks.fire, 0);
		}
		else {
			for (int x = (int) (player.posX - 3); x <= player.posX + 3; x++)
				for (int z = (int) (player.posZ - 30); z <= player.posZ + 30; z++)
					for (int y = (int) (player.posY - 5); y <= player.posY + 5; y++)
						if (world.isAirBlock(x, y, z))
							PlayerHelper.checkedPlaceBlock(entityPlayerMP, x, y, z, Blocks.fire, 0);
		}
	}

	@Override
	public boolean shootProjectile(EntityPlayer player, ItemStack stack)
	{
		World world = player.worldObj;
		if (world.isRemote) return false;

		switch (stack.getItemDamage())
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
