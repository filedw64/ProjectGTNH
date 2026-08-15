package moze_intel.projecte.gameObjs.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.IGrowable;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fluids.BlockFluidBase;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;
import java.util.Set;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class TimeWatch extends ItemCharge implements IModeChanger, IBauble, IPedestalItem
{
	private static Set<String> internalBlacklist = Sets.newHashSet(
		"moze_intel.projecte.gameObjs.tiles.DMPedestalTile",
		"Reika.ChromatiCraft.TileEntity.AOE.TileEntityAccelerator",
		"com.sci.torcherino.tile.TileTorcherino",
		"com.sci.torcherino.tile.TileCompressedTorcherino"
	);

	@SideOnly(Side.CLIENT)
	private IIcon ringOff;
	@SideOnly(Side.CLIENT)
	private IIcon ringOn;

	// 记录上一次处理到的索引，用于轮询防止机器饿死
	private static int lastProcessedIndex = 0;
	private static int lastRandomTickIndex = 0;

	public TimeWatch()
	{
		super("time_watch", (byte)2);
		this.setNoRepair();
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			if (!ProjectEConfig.enableTimeWatch)
			{
				player.addChatComponentMessage(new ChatComponentTranslation("pe.timewatch.disabled"));
				return stack;
			}

			if (!stack.hasTagCompound())
			{
				stack.stackTagCompound = new NBTTagCompound();
			}

			byte current = getTimeBoost(stack);
			setTimeBoost(stack, (byte) (current == 2 ? 0 : current + 1));
			player.addChatComponentMessage(new ChatComponentTranslation("pe.timewatch.mode_switch", new ChatComponentTranslation(getTimeName(stack)).getUnformattedTextForChat()));
		}

		return stack;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int invSlot, boolean isHeld)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		if (!(entity instanceof EntityPlayer) || invSlot > 8 || !ProjectEConfig.enableTimeWatch) return;

		byte timeControl = getTimeBoost(stack);

		if (world.getGameRules().getGameRuleBooleanValue("doDaylightCycle")) {
			if (timeControl == 1)
			{
				if (world.getWorldTime() + ((getCharge(stack) + 1) * 4) > Long.MAX_VALUE)
					world.setWorldTime(Long.MAX_VALUE);
				else
					world.setWorldTime((world.getWorldTime() + ((getCharge(stack) + 1) * 4)));
			}
			else if (timeControl == 2)
			{
				if (world.getWorldTime() - ((getCharge(stack) + 1) * 4) < 0)
					world.setWorldTime(0);
				else
					world.setWorldTime((world.getWorldTime() - ((getCharge(stack) + 1) * 4)));
			}
		}

		if (world.isRemote || stack.getItemDamage() == 0) return;

		EntityPlayer player = (EntityPlayer) entity;
		double reqEmc = getEmcPerTick(this.getCharge(stack));

		if (!consumeFuel(player, stack, reqEmc, true)) return;

		int charge = this.getCharge(stack);
		int bonusTicks = charge == 0 ? 8 : (charge == 1 ? 12 : 16);
		float mobSlowdown = charge == 0 ? 0.25F : (charge == 1 ? 0.16F : 0.12F);

		AxisAlignedBB bBox = player.boundingBox.expand(8, 8, 8);

		// 限定整个加速逻辑的运行时间不得超过 1 毫秒
		long stopTime = System.nanoTime() + 1_000_000L;

		if (speedUpTileEntities(world, bonusTicks, bBox, stopTime)) return;
		if (speedUpRandomTicks(world, bonusTicks, bBox, stopTime)) return;
		slowMobs(world, bBox, mobSlowdown);
	}

	private void slowMobs(World world, AxisAlignedBB bBox, float mobSlowdown)
	{
		if (bBox == null) return;

		for (Object obj : world.getEntitiesWithinAABB(EntityLiving.class, bBox))
		{
			Entity ent = (Entity) obj;
			if (ent.motionX != 0) ent.motionX *= mobSlowdown;
			if (ent.motionZ != 0) ent.motionZ *= mobSlowdown;
		}
	}

	/**
	 * @return true if time limit reached, false otherwise
	 */
	private boolean speedUpTileEntities(World world, int bonusTicks, AxisAlignedBB bBox, long stopTime)
	{
		if (bBox == null || bonusTicks == 0) return false;

		List<TileEntity> list = WorldHelper.getTileEntitiesWithinAABB(world, bBox);
		if (list.isEmpty()) return false;

		int count = 0;
		int size = list.size();

		for (int i = 0; i < bonusTicks; i++)
		{
			for (int j = 0; j < size; j++)
			{
				int index = (lastProcessedIndex + j) % size;
				TileEntity tile = list.get(index);

				// 每处理 16 个机器检查一次时间，避免 nanoTime 本身带来开销
				if ((++count & 15) == 0 && System.nanoTime() > stopTime) {
					lastProcessedIndex = (index + 1) % size;
					return true;
				}

				if (!tile.isInvalid() && !internalBlacklist.contains(tile.getClass().getName()))
				{
					tile.updateEntity();
				}
			}
		}
		return false;
	}

	/**
	 * @return true if time limit reached, false otherwise
	 */
	private boolean speedUpRandomTicks(World world, int bonusTicks, AxisAlignedBB bBox, long stopTime)
	{
		if (bBox == null || bonusTicks == 0) return false;

		int minX = (int) bBox.minX;
		int maxX = (int) bBox.maxX;
		int minY = (int) bBox.minY;
		int maxY = (int) bBox.maxY;
		int minZ = (int) bBox.minZ;
		int maxZ = (int) bBox.maxZ;

		int sizeX = maxX - minX + 1;
		int sizeZ = maxZ - minZ + 1;
		int totalColumns = sizeX * sizeZ;

		if (totalColumns <= 0) return false;

		// 采用偏移量轮询遍历 X 和 Z
		for (int i = 0; i < totalColumns; i++)
		{
			int index = (lastRandomTickIndex + i) % totalColumns;
			int x = minX + (index % sizeX);
			int z = minZ + (index / sizeX);

			// 在列级别检查超时
			if (System.nanoTime() > stopTime) {
				lastRandomTickIndex = (index + 1) % totalColumns;
				return true;
			}

			if (!world.blockExists(x, 0, z)) continue;

			for (int y = minY; y <= maxY; y++)
			{
				Block block = world.getBlock(x, y, z);

				if (block.getTickRandomly()
					&& !(block instanceof BlockLiquid)
					&& !(block instanceof BlockFluidBase)
					&& !(block instanceof IGrowable)
					&& !(block instanceof IPlantable))
				{
					for (int b = 0; b < bonusTicks; b++)
					{
						block.updateTick(world, x, y, z, itemRand);
					}
				}
			}
		}
		return false;
	}

	private String getTimeName(ItemStack stack)
	{
		byte mode = getTimeBoost(stack);
		return switch (mode) {
			case 0 -> "pe.timewatch.off";
			case 1 -> "pe.timewatch.ff";
			case 2 -> "pe.timewatch.rw";
			default -> "ERROR_INVALID_MODE";
		};
	}

	private byte getTimeBoost(ItemStack stack)
	{
		return stack.stackTagCompound.getByte("TimeMode");
	}

	private void setTimeBoost(ItemStack stack, byte time)
	{
		stack.stackTagCompound.setByte("TimeMode", (byte) MathHelper.clamp_int(time, 0, 2));
	}

	public double getEmcPerTick(int charge)
	{
		int actualCharge = charge + 1;
		return (10.0D * actualCharge) / 20.0D;
	}

	@Override
	public byte getMode(ItemStack stack)
	{
		return (byte) stack.getItemDamage();
	}

	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		if (stack.getItemDamage() == 0)
		{
			stack.setItemDamage(1);
			playChargeSound(player);
		}
		else
		{
			stack.setItemDamage(0);
			playUnChargeSound(player);
		}
	}

	public void playChargeSound(EntityPlayer player)
	{
		player.worldObj.playSoundAtEntity(player, "projecte:clock", 0.8F, 1.25F);
	}

	public void playUnChargeSound(EntityPlayer player)
	{
		player.worldObj.playSoundAtEntity(player, "projecte:clock", 0.8F, 0.85F);
	}

	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int dmg)
	{
		if (dmg == 0) return ringOff;
		return ringOn;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		ringOff = register.registerIcon(this.getTexture("rings", "time_watch_off"));
		ringOn = register.registerIcon(this.getTexture("rings", "time_watch_on"));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean bool)
	{
		list.add(StatCollector.translateToLocal("pe.timewatch.tooltip1"));
		list.add(StatCollector.translateToLocal("pe.timewatch.tooltip2"));

		if (stack.hasTagCompound())
		{
			list.add(String.format(StatCollector.translateToLocal("pe.timewatch.mode"),
				StatCollector.translateToLocal(getTimeName(stack))));
		}
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public baubles.api.BaubleType getBaubleType(ItemStack itemstack)
	{
		return BaubleType.BELT;
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
		if (!world.isRemote && ProjectEConfig.enableTimeWatch)
		{
			AxisAlignedBB bBox = ((DMPedestalTile) world.getTileEntity(x, y, z)).getEffectBounds();

			// 台座模式同样限定 1 毫秒超时
			long stopTime = System.nanoTime() + 1_000_000L;

			if (ProjectEConfig.timePedBonus > 0) {
				if (speedUpTileEntities(world, ProjectEConfig.timePedBonus, bBox, stopTime)) return;
				speedUpRandomTicks(world, ProjectEConfig.timePedBonus, bBox, stopTime);
			}

			if (ProjectEConfig.timePedMobSlowness < 1.0F) {
				slowMobs(world, bBox, ProjectEConfig.timePedMobSlowness);
			}
		}
	}

	@Override
	public List<String> getPedestalDescription()
	{
		List<String> list = Lists.newArrayList();
		if (ProjectEConfig.timePedBonus > 0) {
			list.add(EnumChatFormatting.BLUE +
				String.format(StatCollector.translateToLocal("pe.timewatch.pedestal1"), ProjectEConfig.timePedBonus));
		}
		if (ProjectEConfig.timePedMobSlowness < 1.0F)
		{
			list.add(EnumChatFormatting.BLUE +
				String.format(StatCollector.translateToLocal("pe.timewatch.pedestal2"), ProjectEConfig.timePedMobSlowness));
		}
		return list;
	}

	public static void blacklist(Class<? extends TileEntity> clazz)
	{
		internalBlacklist.add(clazz.getName());
	}
}
