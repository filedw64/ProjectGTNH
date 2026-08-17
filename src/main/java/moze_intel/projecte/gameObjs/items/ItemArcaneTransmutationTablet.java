package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.PECore;
import moze_intel.projecte.utils.Constants;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemArcaneTransmutationTablet extends ItemPE {

	public ItemArcaneTransmutationTablet() {
		this.setUnlocalizedName("arcane_transmutation_tablet");
		this.setMaxStackSize(1);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (!world.isRemote) {
			// 传入玩家当前坐标，虽然作为便携 GUI，坐标并不参与 TileEntity 的获取，但保持规范
			player.openGui(PECore.instance, Constants.ARCANE_TABLET_GUI, world, (int) player.posX, (int) player.posY, (int) player.posZ);
		}
		return stack;
	}

	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("arcane_transmutation_tablet"));
	}
}
