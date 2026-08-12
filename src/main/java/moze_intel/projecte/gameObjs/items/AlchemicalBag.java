package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.item.IAlchBagItem;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.AlchBagContainer;
import moze_intel.projecte.playerData.AlchemicalBags;
import moze_intel.projecte.utils.AchievementHandler;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.ItemHelper;

import java.util.List;

public class AlchemicalBag extends ItemPE
{
	private final String[] colors = new String[] {"white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	private final String[] unlocalizedColors = new String[] {
		"item.fireworksCharge.white", "item.fireworksCharge.orange",
		"item.fireworksCharge.magenta", "item.fireworksCharge.lightBlue",
		"item.fireworksCharge.yellow", "item.fireworksCharge.lime",
		"item.fireworksCharge.pink", "item.fireworksCharge.gray",
		"item.fireworksCharge.silver", "item.fireworksCharge.cyan",
		"item.fireworksCharge.purple", "item.fireworksCharge.blue",
		"item.fireworksCharge.brown", "item.fireworksCharge.green",
		"item.fireworksCharge.red", "item.fireworksCharge.black"};

	@SideOnly(Side.CLIENT)
	private IIcon[] icons;

	public AlchemicalBag()
	{
		this.setUnlocalizedName("alchemical_bag");
		this.hasSubtypes = true;
		this.setMaxStackSize(1);
		this.setMaxDamage(0);
	}

	public static byte getPage(ItemStack stack)
	{
		if (stack.hasTagCompound()) {
			byte p = stack.getTagCompound().getByte("BagPage");
			// 如果调小了页数，强制回滚到第 0 页
			if (p >= ProjectEConfig.alchBagPages) p = 0;
			return p;
		}
		return 0;
	}

	public static void setPage(ItemStack stack, byte page)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		stack.getTagCompound().setByte("BagPage", page);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			// 如果启用了多页，并且玩家在潜行，则打开 66 号选页 GUI
			if (player.isSneaking() && ProjectEConfig.alchBagPages > 1)
			{
				player.openGui(PECore.instance, 66, world, (int) player.posX, (int) player.posY, (int) player.posZ);
			}
			else
			{
				// 右键直接打开对应页数的GUI
				player.openGui(PECore.instance, Constants.ALCH_BAG_GUI, world, (int) player.posX, (int) player.posY, (int) player.posZ);
			}
		}

		return stack;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5)
	{
		if (!(entity instanceof EntityPlayer)) return;

		EntityPlayer player = (EntityPlayer) entity;
		byte color = (byte) stack.getItemDamage();
		byte page = getPage(stack);

		// 拿取当前页数的数据
		ItemStack[] inv = AlchemicalBags.get(player, color, page);

		if (player.openContainer instanceof AlchBagContainer)
		{
			ItemStack[] openContainerInv = ((AlchBagContainer) player.openContainer).inventory.getInventory();
			for (int i = 0; i < openContainerInv.length; i++)
			{
				ItemStack current = openContainerInv[i];
				if (current != null && current.getItem() instanceof IAlchBagItem)
				{
					((IAlchBagItem) current.getItem()).updateInAlchBag(openContainerInv, player, current);
				}
			}
		}
		else
		{
			boolean hasChanged = false;
			for (int i = 0; i < inv.length; i++)
			{
				ItemStack current = inv[i];
				if (current != null && current.getItem() instanceof IAlchBagItem)
				{
					if (((IAlchBagItem) current.getItem()).updateInAlchBag(inv, player, current))
					{
						hasChanged = true;
					}
				}
			}

			if (!world.isRemote && hasChanged)
			{
				AlchemicalBags.set(player, color, page, inv);
				AlchemicalBags.syncPartial(player, color, page);
			}
		}
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack)
	{
		return 1;
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack)
	{
		String name = super.getItemStackDisplayName(stack);
		int i = stack.getItemDamage();

		if (i > 15) return name + " (" + StatCollector.translateToLocal("pe.debug.metainvalid.name") + ")";

		String color = " (" + StatCollector.translateToLocal(unlocalizedColors[i]) + ")";
		if (ProjectEConfig.alchBagPages > 1) {
			return name + color + EnumChatFormatting.GRAY + " [Page " + (getPage(stack) + 1) + "]";
		}
		return name + color;
	}

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player)
	{
		super.onCreated(stack, world, player);
		if (!world.isRemote) player.addStat(AchievementHandler.ALCH_BAG, 1);
	}

	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs cTab, List<ItemStack> list)
	{
		for (int i = 0; i < 16; ++i) list.add(new ItemStack(item, 1, i));
	}

	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int par1)
	{
		return icons[MathHelper.clamp_int(par1, 0, 15)];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		icons = new IIcon[16];
		for (int i = 0; i < 16; i++) icons[i] = register.registerIcon(this.getTexture("alchemy_bags", colors[i]));
	}

	public static ItemStack getFirstBagWithSuctionItem(EntityPlayer player, ItemStack[] inventory)
	{
		for (ItemStack stack : inventory)
		{
			if (stack == null) continue;

			if (stack.getItem() == ObjHandler.alchBag)
			{
				ItemStack[] inv = AlchemicalBags.get(player, (byte) stack.getItemDamage(), getPage(stack));
				if (ItemHelper.invContainsItem(inv, new ItemStack(ObjHandler.blackHole, 1, 1))
					|| ItemHelper.invContainsItem(inv, new ItemStack(ObjHandler.voidRing, 1, 1)))
					return stack;
			}
		}
		return null;
	}
}
