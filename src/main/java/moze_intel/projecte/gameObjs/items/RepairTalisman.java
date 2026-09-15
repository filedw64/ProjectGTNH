package moze_intel.projecte.gameObjs.items;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import com.cricketcraft.chisel.api.IChiselItem;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IAlchBagItem;
import moze_intel.projecte.api.item.IAlchChestItem;
import moze_intel.projecte.api.item.IItemEmc;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.utils.MathUtils;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RepairTalisman extends ItemPE implements IAlchBagItem, IAlchChestItem, IBauble, IPedestalItem
{
	public RepairTalisman() {
		this.setUnlocalizedName("repair_talisman");
		this.setMaxStackSize(1);
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5) {
		if (!stack.hasTagCompound())
			stack.stackTagCompound = new NBTTagCompound();

		if (world.isRemote || !(entity instanceof EntityPlayer player))
			return;

		PlayerTimers.activateRepair(player);

		if (PlayerTimers.canRepair(player))
			repairAllItems(player);
	}

	public void repairAllItems(EntityPlayer player) {
		IInventory inv = player.inventory;
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack invStack = inv.getStackInSlot(i);
			if (invStack == null || (invStack.equals(player.getCurrentEquippedItem()) && player.isSwingInProgress))
				continue;
			tryRepair(invStack, 1);
		}

		if (Loader.isModLoaded("Baubles"))
			baubleRepair(player);
	}

	/**
	 * @param stack the ItemStack need to be repaired.
	 * @return True when repair the item successfully, otherwise false.
	 */
	private boolean tryRepair(ItemStack stack, int amount) {
		if (stack == null || amount <= 0) return false;
		Item item = stack.getItem();
		if (item == null || item instanceof IModeChanger || item instanceof IItemEmc) return false;
		if (Loader.isModLoaded("chisel") && chiselCheck(item)) return false;
		NBTTagCompound nbt = stack.stackTagCompound;
		if (nbt != null) {
			// 匠魂工具
			if (nbt.hasKey("InfiTool")) {
				NBTTagCompound infi = nbt.getCompoundTag("InfiTool");
				if (infi.getBoolean("Broken"))
					infi.setBoolean("Broken", false);

				final int damage = infi.getInteger("Damage");
				if (damage > amount)
					infi.setInteger("Damage", damage - amount);
				else infi.setInteger("Damage", 0);
				return true;
			}
			// gt工具
			if (nbt.hasKey("GT.ToolStats")) {
				NBTTagCompound gt = nbt.getCompoundTag("GT.ToolStats");
				final long damage = gt.getLong("Damage");
				final long toRepair = amount * 100L;
				if (damage > toRepair)
					gt.setLong("Damage", damage - toRepair);
				else gt.setLong("Damage", 0);
				return true;
			}
		}

		if (item.getMaxDamage(stack) > 0 && !item.getHasSubtypes()) {
			final int damage = item.getDamage(stack);
			if (damage > amount)
				item.setDamage(stack, damage - amount);
			else item.setDamage(stack, 0);
			return true;
		}
		return false;
	}

	@Optional.Method(modid = "chisel")
	public boolean chiselCheck(Item item) {
		return item instanceof IChiselItem;
	}

	@Optional.Method(modid = "Baubles")
	public void baubleRepair(EntityPlayer player) {
		IInventory bInv = BaublesApi.getBaubles(player);
		for (int i = 0; i < bInv.getSizeInventory(); i++) {
			ItemStack bInvStack = bInv.getStackInSlot(i);
			tryRepair(bInvStack, 1);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		this.itemIcon = register.registerIcon(this.getTexture("repair_talisman"));
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public baubles.api.BaubleType getBaubleType(ItemStack itemstack) {
		return BaubleType.BELT;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase player) {
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
	public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
		return true;
	}

	@Override
	@Optional.Method(modid = "Baubles")
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
		return true;
	}

	@Override
	public void updateInPedestal(World world, int x, int y, int z) {
		if (world.isRemote || ProjectEConfig.repairPedCooldown == -1) return;

		DMPedestalTile tile = (DMPedestalTile) world.getTileEntity(x, y, z);
		if (tile.getActivityCooldown() != 0) {
			tile.decrementActivityCooldown();
			return;
		}

		List<EntityPlayerMP> list = world.getEntitiesWithinAABB(EntityPlayerMP.class, tile.getEffectBounds());
		for (EntityPlayerMP player : list)
			repairAllItems(player);
		tile.setActivityCooldown(ProjectEConfig.repairPedCooldown);
	}

	@Override
	public List<String> getPedestalDescription()
	{
		List<String> list = new ArrayList<>();
		if (ProjectEConfig.repairPedCooldown != -1)
		{
			list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("pe.repairtalisman.pedestal1"));
			list.add(EnumChatFormatting.BLUE + String.format(StatCollector.translateToLocal("pe.repairtalisman.pedestal2"),
				MathUtils.tickToSecFormatted(ProjectEConfig.repairPedCooldown)));
		}
		return list;
	}

	@Override
	public void updateInAlchChest(World world, int x, int y, int z, ItemStack stack)
	{
		if (world.isRemote) return;

		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());

		AlchChestTile tile = (AlchChestTile) world.getTileEntity(x, y, z);
		final byte coolDown = stack.stackTagCompound.getByte("Cooldown");

		if (coolDown > 0) {
			stack.stackTagCompound.setByte("Cooldown", (byte) (coolDown - 1));
			return;
		}

		boolean hasAction = false;

		for (int i = 0; i < tile.getSizeInventory(); i++) {
			ItemStack invStack = tile.getStackInSlot(i);
			if (tryRepair(invStack, 1)) {
				tile.setInventorySlotContents(i, invStack);
				hasAction = true;
			}
		}

		if (hasAction) {
			stack.stackTagCompound.setByte("Cooldown", (byte) 19);
			tile.markDirty();
		}
	}

	@Override
	public boolean updateInAlchBag(ItemStack[] inv, EntityPlayer player, ItemStack stack)
	{
		if (player.worldObj.isRemote) return false;

		if (!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());

		final byte coolDown = stack.stackTagCompound.getByte("Cooldown");

		if (coolDown > 0) {
			stack.stackTagCompound.setByte("Cooldown", (byte) (coolDown - 1));
			return false;
		}

		boolean hasAction = false;
		for (ItemStack invStack : inv)
			if (tryRepair(invStack, 1))
				hasAction = true;

		if (hasAction) {
			stack.stackTagCompound.setByte("Cooldown", (byte) 19);
			return true;
		}
		return false;
	}
}
