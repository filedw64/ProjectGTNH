package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.utils.NetworkEmcHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.List;

public class InfiniteSteak extends ItemFood
{
	public InfiniteSteak()
	{
		super(8, 0.8F, true);
		this.setUnlocalizedName("pe_infinite_steak");
		this.setCreativeTab(ObjHandler.cTab);
		this.setMaxStackSize(1);
		this.setAlwaysEdible();
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (player.isSneaking()) {
			if (!world.isRemote) {
				if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
				stack.stackTagCompound.setString("OwnerUUID", player.getUniqueID().toString());
				stack.stackTagCompound.setString("OwnerName", player.getCommandSenderName());
				player.addChatComponentMessage(new ChatComponentText("§a无限牛排已绑定至您的个人 EMC 网络"));
			}
			return stack;
		}
		return super.onItemRightClick(stack, world, player);
	}

	@Override
	public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote && stack.hasTagCompound() && stack.stackTagCompound.hasKey("OwnerUUID")) {
			String uuid = stack.stackTagCompound.getString("OwnerUUID");
			if (NetworkEmcHelper.deductNetworkEMC(uuid, 64)) { // 扣除 64 EMC
				player.getFoodStats().addStats(8, 0.8F);
				world.playSoundAtEntity(player, "random.burp", 0.5F, world.rand.nextFloat() * 0.1F + 0.9F);
				this.onFoodEaten(stack, world, player);
			}
		}
		return stack; // 不消耗物品本身
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		if (stack.hasTagCompound() && stack.stackTagCompound.hasKey("OwnerName")) {
			list.add("§7已绑定: §b" + stack.stackTagCompound.getString("OwnerName"));
			list.add("§7食用时扣除绑定者 64 EMC");
		} else {
			list.add("§c未绑定 (Shift+右键绑定)");
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon("projecte:infinite_steak");
	}

}
