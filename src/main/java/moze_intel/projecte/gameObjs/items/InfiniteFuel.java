package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.utils.NetworkEmcHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.List;

public class InfiniteFuel extends ItemPE
{
	public InfiniteFuel()
	{
		this.setUnlocalizedName("infinite_fuel");
		this.setMaxStackSize(1);
		this.setMaxDamage(0);
		this.setNoRepair();
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (player.isSneaking()) {
			if (!world.isRemote) {
				if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
				stack.stackTagCompound.setString("OwnerUUID", player.getUniqueID().toString());
				stack.stackTagCompound.setString("OwnerName", player.getCommandSenderName());
				player.addChatComponentMessage(new ChatComponentText("§a无限燃料已绑定至您的个人 EMC 网络"));
			}
			return stack;
		}
		return super.onItemRightClick(stack, world, player);
	}

	@Override
	public boolean hasContainerItem(ItemStack stack)
	{
		return true; // 始终尝试返回自身，真正的扣费拦截在 getBurnTime
	}

	@Override
	public ItemStack getContainerItem(ItemStack stack)
	{
		ItemStack copy = stack.copy();
		if (copy.hasTagCompound() && copy.stackTagCompound.hasKey("OwnerUUID")) {
			String uuid = copy.stackTagCompound.getString("OwnerUUID");
			NetworkEmcHelper.deductNetworkEMC(uuid, 32); // 每次燃烧后扣除 32 EMC
		}
		return copy;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		if (stack.hasTagCompound() && stack.stackTagCompound.hasKey("OwnerName")) {
			list.add("§7已绑定: §b" + stack.stackTagCompound.getString("OwnerName"));
			list.add("§7燃烧时扣除绑定者 32 EMC");
		} else {
			list.add("§c未绑定 (Shift+右键绑定)");
		}
	}

	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("infinite_fuel"));
	}
}
