package moze_intel.projecte.gameObjs.items;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import com.cricketcraft.chisel.api.IChiselItem;
import com.google.common.collect.Lists;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IAlchBagItem;
import moze_intel.projecte.api.item.IAlchChestItem;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.rings.RingToggle;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.utils.MathUtils;

import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RepairTalisman extends ItemPE implements IAlchBagItem, IAlchChestItem, IBauble, IPedestalItem
{
	public RepairTalisman()
	{
		this.setUnlocalizedName("repair_talisman");
		this.setMaxStackSize(1);
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5)
	{
		if (!stack.hasTagCompound())
		{
			stack.stackTagCompound = new NBTTagCompound();
		}

		if (world.isRemote || !(entity instanceof EntityPlayer))
		{
			return;
		}

		EntityPlayer player = (EntityPlayer) entity;
		PlayerTimers.activateRepair(player);

		if (PlayerTimers.canRepair(player))
		{
			repairAllItems(player);
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote && player.isSneaking())
		{
			// Config 拦截
			if (!ProjectEConfig.enableRepairShiftRMB) return stack;

			boolean repairedAny = false;
			IInventory inv = player.inventory;

			for (int i = 0; i < inv.getSizeInventory(); i++)
			{
				ItemStack invStack = inv.getStackInSlot(i);
				if (invStack != null && !invStack.equals(stack) && canRepair(invStack))
				{
					fullyRepair(invStack);
					repairedAny = true;
				}
			}

			if (Loader.isModLoaded("Baubles"))
			{
				IInventory bInv = BaublesApi.getBaubles(player);
				for (int i = 0; i < bInv.getSizeInventory(); i++)
				{
					ItemStack bInvStack = bInv.getStackInSlot(i);
					if (bInvStack != null && canRepair(bInvStack))
					{
						fullyRepair(bInvStack);
						repairedAny = true;
					}
				}
			}

			if (repairedAny)
			{
				world.playSoundAtEntity(player, "projecte:item.peheal", 1.0F, 1.0F);
			}
		}

		return stack;
	}

	public void repairAllItems(EntityPlayer player)
	{
		IInventory inv = player.inventory;

		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			ItemStack invStack = inv.getStackInSlot(i);

			if (invStack == null || (invStack.equals(player.getCurrentEquippedItem()) && player.isSwingInProgress))
			{
				continue;
			}

			if (canRepair(invStack))
			{
				doRepair(invStack, 1);
			}
		}

		if (Loader.isModLoaded("Baubles")) baubleRepair(player);
	}

	private boolean canRepair(ItemStack stack)
	{
		if (stack.getItem() instanceof IModeChanger || stack.getItem() instanceof IItemEmc) return false;
		if (Loader.isModLoaded("chisel") && chiselCheck(stack)) return false;

		if (stack.hasTagCompound())
		{
			NBTTagCompound nbt = stack.getTagCompound();

			// 匠魂
			if (nbt.hasKey("InfiTool"))
			{
				NBTTagCompound infi = nbt.getCompoundTag("InfiTool");
				return infi.getBoolean("Broken") || infi.getInteger("Damage") > 0;
			}

			// 格雷
			if (nbt.hasKey("GT.ToolStats"))
			{
				NBTTagCompound gt = nbt.getCompoundTag("GT.ToolStats");
				if (gt.getBoolean("Electric")) return false; // 拒绝修理电动工具
				return gt.getLong("Damage") > 0;
			}
		}

		return stack.getItem().isRepairable() && stack.isItemDamaged();
	}

	private void doRepair(ItemStack stack, int amount)
	{
		if (stack.hasTagCompound())
		{
			NBTTagCompound nbt = stack.getTagCompound();

			if (nbt.hasKey("InfiTool"))
			{
				NBTTagCompound infi = nbt.getCompoundTag("InfiTool");
				int damage = infi.getInteger("Damage");

				if (damage > 0)
				{
					infi.setInteger("Damage", Math.max(0, damage - amount));
				}

				if (infi.getBoolean("Broken") && infi.getInteger("Damage") <= 0)
				{
					infi.setBoolean("Broken", false);
				}
				return;
			}

			if (nbt.hasKey("GT.ToolStats"))
			{
				NBTTagCompound gt = nbt.getCompoundTag("GT.ToolStats");
				long damage = gt.getLong("Damage");
				if (damage > 0)
				{
					gt.setLong("Damage", Math.max(0L, damage - (amount * 100L)));
				}
				return;
			}
		}

		if (stack.isItemDamaged())
		{
			stack.setItemDamage(Math.max(0, stack.getItemDamage() - amount));
		}
	}

	private void fullyRepair(ItemStack stack)
	{
		if (stack.hasTagCompound())
		{
			NBTTagCompound nbt = stack.getTagCompound();

			if (nbt.hasKey("InfiTool"))
			{
				NBTTagCompound infi = nbt.getCompoundTag("InfiTool");
				infi.setInteger("Damage", 0);
				if (infi.getBoolean("Broken"))
				{
					infi.setBoolean("Broken", false);
				}
				return;
			}

			if (nbt.hasKey("GT.ToolStats"))
			{
				NBTTagCompound gt = nbt.getCompoundTag("GT.ToolStats");
				gt.setLong("Damage", 0L);
				return;
			}
		}

		if (stack.isItemDamaged())
		{
			stack.setItemDamage(0);
		}
	}

	@Optional.Method(modid = "chisel")
	public boolean chiselCheck(ItemStack is)
	{
		return is.getItem() instanceof IChiselItem;
	}

	@Optional.Method(modid = "Baubles")
	public void baubleRepair(EntityPlayer player)
	{
		IInventory bInv = BaublesApi.getBaubles(player);

		for (int i = 0; i < bInv.getSizeInventory(); i++)
		{
			ItemStack bInvStack = bInv.getStackInSlot(i);
			if (bInvStack != null && canRepair(bInvStack))
			{
				doRepair(bInvStack, 1);
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("repair_talisman"));
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
		if (!world.isRemote && ProjectEConfig.repairPedCooldown != -1)
		{
			DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
			if (tile.getActivityCooldown() == 0)
			{
				List<EntityPlayerMP> list = world.getEntitiesWithinAABB(EntityPlayerMP.class, tile.getEffectBounds());
				for (EntityPlayerMP player : list)
				{
					repairAllItems(player);
				}
				tile.setActivityCooldown(ProjectEConfig.repairPedCooldown);
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
		if (ProjectEConfig.repairPedCooldown != -1)
		{
			list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.repairtalisman.pedestal1"));
			list.add(EnumChatFormatting.BLUE +
				String.format(StatCollector.translateToLocal("pe.repairtalisman.pedestal2"), MathUtils.tickToSecFormatted(ProjectEConfig.repairPedCooldown)));
		}
		return list;
	}

	@Override
	public void updateInAlchChest(World world, int x, int y, int z, ItemStack stack)
	{
		if (world.isRemote) return;

		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

		AlchChestTile tile = ((AlchChestTile) world.getTileEntity(x, y, z));
		byte coolDown = stack.stackTagCompound.getByte("Cooldown");

		if (coolDown > 0)
		{
			stack.stackTagCompound.setByte("Cooldown", (byte) (coolDown - 1));
		}
		else
		{
			boolean hasAction = false;

			for (int i = 0; i < tile.getSizeInventory(); i++)
			{
				ItemStack invStack = tile.getStackInSlot(i);

				if (invStack != null && canRepair(invStack))
				{
					doRepair(invStack, 1);
					tile.setInventorySlotContents(i, invStack);
					hasAction = true;
				}
			}

			if (hasAction)
			{
				stack.stackTagCompound.setByte("Cooldown", (byte) 19);
				tile.markDirty();
			}
		}
	}

	@Override
	public boolean updateInAlchBag(ItemStack[] inv, EntityPlayer player, ItemStack stack)
	{
		if (player.worldObj.isRemote) return false;

		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

		byte coolDown = stack.stackTagCompound.getByte("Cooldown");

		if (coolDown > 0)
		{
			stack.stackTagCompound.setByte("Cooldown", (byte) (coolDown - 1));
		}
		else
		{
			boolean hasAction = false;

			for (ItemStack invStack : inv)
			{
				if (invStack != null && canRepair(invStack))
				{
					doRepair(invStack, 1);
					hasAction = true;
				}
			}

			if (hasAction)
			{
				stack.stackTagCompound.setByte("Cooldown", (byte) 19);
				return true;
			}
		}
		return false;
	}
}
