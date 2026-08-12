package moze_intel.projecte.gameObjs.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.item.IAlchBagItem;
import moze_intel.projecte.api.item.IAlchChestItem;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PEKeybind;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.utils.WorldHelper;

import java.util.ArrayList;
import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class GemEternalDensity extends ItemPE implements IAlchBagItem, IAlchChestItem, IModeChanger, IBauble
{
	@SideOnly(Side.CLIENT)
	private IIcon gemOff;
	@SideOnly(Side.CLIENT)
	private IIcon gemOn;

	public GemEternalDensity()
	{
		this.setUnlocalizedName("gem_density");
		this.setMaxStackSize(1);
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld)
	{
		if (!stack.hasTagCompound())
		{
			stack.setTagCompound(new NBTTagCompound());
		}

		if (world.isRemote || !(entity instanceof EntityPlayer))
		{
			return;
		}

		// 扫描降频为每 20 Ticks (1秒) 扫描一次
		if (world.getTotalWorldTime() % 20 == 0)
		{
			condense(stack, ((EntityPlayer) entity).inventory.mainInventory);
		}
	}

	/**
	 * @return Whether the inventory was changed
	 */
	public static boolean condense(ItemStack gem, ItemStack[] inv)
	{
		if (gem.getItemDamage() == 0 || ItemPE.getEmc(gem) >= Constants.TILE_MAX_EMC)
		{
			return false;
		}

		boolean hasChanged = false;
		boolean isWhitelist = isWhitelistMode(gem);
		List<ItemStack> whitelist = getWhitelist(gem);

		ItemStack target = getTarget(gem);
		double targetEmc = EMCHelper.getEmcValue(target);

		// 将 NBT 读取提到循环外部，一次性加载到内存列表
		List<ItemStack> consumed = getItems(gem);
		double addedEmc = 0;

		for (int i = 0; i < inv.length; i++)
		{
			ItemStack s = inv[i];

			if (s == null || !EMCHelper.doesItemHaveEmc(s) || s.getMaxStackSize() == 1 || EMCHelper.getEmcValue(s) >= targetEmc)
			{
				continue;
			}

			if ((isWhitelist && listContains(whitelist, s)) || (!isWhitelist && !listContains(whitelist, s)))
			{
				ItemStack copy = s.copy();

				// 一次吃掉整组物品，彻底清理槽位，而不是原版的只吃一点
				addToList(consumed, copy);
				inv[i] = null;

				addedEmc += EMCHelper.getEmcValue(copy) * copy.stackSize;
				hasChanged = true;
				// 去除了 break;，一次循环直接吃光所有符合条件的槽位
			}
		}

		if (addedEmc > 0)
		{
			ItemPE.addEmcToStack(gem, addedEmc);
		}

		if (!EMCHelper.doesItemHaveEmc(target))
		{
			if (hasChanged) setItems(gem, consumed);
			return hasChanged;
		}

		// 批量生成目标物品
		while (getEmc(gem) >= targetEmc)
		{
			ItemStack remain = ItemHelper.pushStackInInv(inv, ItemStack.copyItemStack(target));

			if (remain != null)
			{
				break; // 背包满了
			}

			ItemPE.removeEmc(gem, targetEmc);
			consumed.clear(); // 产出物品后清空吞噬记录
			hasChanged = true;
		}

		// 在所有计算（吞噬和产出）全部完成后，统一进行一次 NBT 写入
		if (hasChanged)
		{
			setItems(gem, consumed);
		}

		return hasChanged;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			if (player.isSneaking())
			{
				if (stack.getItemDamage() == 1)
				{
					List<ItemStack> items = getItems(stack);

					if (!items.isEmpty())
					{
						WorldHelper.createLootDrop(items, world, player.posX, player.posY, player.posZ);

						setItems(stack, new ArrayList<>());
						ItemPE.setEmc(stack, 0);
					}

					stack.setItemDamage(0);
				}
				else
				{
					stack.setItemDamage(1);
				}
			}
			else
			{
				player.openGui(PECore.instance, Constants.ETERNAL_DENSITY_GUI, world, (int) player.posX, (int) player.posY, (int) player.posZ);
			}
		}

		return stack;
	}

	private String getTargetName(ItemStack stack)
	{
		return switch (stack.stackTagCompound.getByte("Target")) {
			case 0 -> "item.ingotIron.name";
			case 1 -> "item.ingotGold.name";
			case 2 -> "item.diamond.name";
			case 3 -> "item.pe_matter_dark.name";
			case 4 -> "item.pe_matter_red.name";
			default -> "INVALID";
		};
	}

	private static ItemStack getTarget(ItemStack stack)
	{
		return switch (stack.stackTagCompound.getByte("Target")) {
			case 0 -> new ItemStack(Items.iron_ingot);
			case 1 -> new ItemStack(Items.gold_ingot);
			case 2 -> new ItemStack(Items.diamond);
			case 3 -> new ItemStack(ObjHandler.matter, 1, 0);
			case 4 -> new ItemStack(ObjHandler.matter, 1, 1);
			default -> {
				PELogger.logFatal("Invalid target for gem of eternal density: " + stack.stackTagCompound.getByte("Target"));
				yield null;
			}
		};
	}

	private static void setItems(ItemStack stack, List<ItemStack> list)
	{
		NBTTagList tList = new NBTTagList();

		for (ItemStack s : list)
		{
			NBTTagCompound nbt = new NBTTagCompound();
			s.writeToNBT(nbt);
			tList.appendTag(nbt);
		}

		stack.stackTagCompound.setTag("Consumed", tList);
	}

	private static List<ItemStack> getItems(ItemStack stack)
	{
		List<ItemStack> list = Lists.newArrayList();
		NBTTagList tList = stack.stackTagCompound.getTagList("Consumed", NBT.TAG_COMPOUND);

		for (int i = 0; i < tList.tagCount(); i++)
		{
			list.add(ItemStack.loadItemStackFromNBT(tList.getCompoundTagAt(i)));
		}

		return list;
	}

	// 重载方法使其支持直接传入内存中的 List，避免频繁解包封包
	private static void addToList(List<ItemStack> list, ItemStack stack)
	{
		boolean hasFound = false;

		for (ItemStack s : list)
		{
			if (s.stackSize < s.getMaxStackSize() && ItemHelper.areItemStacksEqual(s, stack))
			{
				int remain = s.getMaxStackSize() - s.stackSize;

				if (stack.stackSize <= remain)
				{
					s.stackSize += stack.stackSize;
					hasFound = true;
					break;
				}
				else
				{
					s.stackSize += remain;
					stack.stackSize -= remain;
				}
			}
		}

		if (!hasFound)
		{
			list.add(stack);
		}
	}

	private static boolean isWhitelistMode(ItemStack stack)
	{
		return stack.stackTagCompound.getBoolean("Whitelist");
	}

	private static List<ItemStack> getWhitelist(ItemStack stack)
	{
		List<ItemStack> result = Lists.newArrayList();
		NBTTagList list = stack.stackTagCompound.getTagList("Items", NBT.TAG_COMPOUND);

		for (int i = 0; i < list.tagCount(); i++)
		{
			result.add(ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i)));
		}

		return result;
	}

	private static boolean listContains(List<ItemStack> list, ItemStack stack)
	{
		for (ItemStack s : list)
		{
			if (ItemHelper.areItemStacksEqual(s, stack))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public byte getMode(ItemStack stack)
	{
		if (stack.hasTagCompound())
		{
			return stack.stackTagCompound.getByte("Target");
		}

		return 0;
	}

	@Override
	public void changeMode(EntityPlayer player, ItemStack stack)
	{
		byte oldMode = getMode(stack);

		if (oldMode == 4)
		{
			stack.stackTagCompound.setByte("Target", (byte) 0);
		}
		else
		{
			stack.stackTagCompound.setByte("Target", (byte) (oldMode + 1));
		}

		player.addChatComponentMessage(new ChatComponentTranslation("pe.gemdensity.mode_switch", new ChatComponentTranslation(getTargetName(stack))));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean par4)
	{
		list.add(StatCollector.translateToLocal("pe.gemdensity.tooltip1"));

		if (stack.hasTagCompound())
		{
			list.add(String.format(StatCollector.translateToLocal("pe.gemdensity.tooltip2"), StatCollector.translateToLocal(getTargetName(stack))));
		}
		list.add(String.format(StatCollector.translateToLocal("pe.gemdensity.tooltip3"), ClientKeyHelper.getKeyName(PEKeybind.MODE)));
		list.add(StatCollector.translateToLocal("pe.gemdensity.tooltip4"));
		list.add(StatCollector.translateToLocal("pe.gemdensity.tooltip5"));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int dmg)
	{
		return dmg == 0 ? gemOff : gemOn;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		gemOn = register.registerIcon(this.getTexture("dense_gem_on"));
		gemOff = register.registerIcon(this.getTexture("dense_gem_off"));
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
	public void updateInAlchChest(World world, int x, int y, int z, ItemStack stack)
	{
		// 扫描降频为每 20 Ticks (1秒)，并且仅在真正发生改变时才执行 markDirty() 减少方块更新开销
		if (!world.isRemote && stack.getItemDamage() == 1 && world.getTotalWorldTime() % 20 == 0)
		{
			AlchChestTile tile = ((AlchChestTile) world.getTileEntity(x, y, z));
			if (condense(stack, tile.getBackingInventoryArray()))
			{
				tile.markDirty();
			}
		}
	}

	@Override
	public boolean updateInAlchBag(ItemStack[] inv, EntityPlayer player, ItemStack stack)
	{
		// 降频至每 20 Ticks (1秒) 处理一次
		return !player.worldObj.isRemote && player.worldObj.getTotalWorldTime() % 20 == 0 && condense(stack, inv);
	}
}
