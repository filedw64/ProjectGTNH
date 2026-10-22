package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import moze_intel.projecte.rendering.TomeFontRenderers;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import moze_intel.projecte.gameObjs.customRecipes.RecipeTome;
import moze_intel.projecte.gameObjs.ObjHandler;

import java.util.List;

public class Tome extends ItemPE
{
	public Tome()
	{
		this.setUnlocalizedName("tome");
		this.setCreativeTab(ObjHandler.tab);
		this.setMaxStackSize(1);
		this.setMaxDamage(0);
	}

	@Override
	public boolean doesContainerItemLeaveCraftingGrid(ItemStack itemstack)
	{
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("tome"));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public FontRenderer getFontRenderer(ItemStack stack)
	{
		return TomeFontRenderers.current();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack itemStack, EntityPlayer player, List<String> list, boolean par4)
	{
		if (isOwnedStack(itemStack, player)) {
			list.add(StatCollector.translateToLocal("pe.tome.tooltip1"));
			list.add(StatCollector.translateToLocal("pe.tome.tooltip2"));
			return;
		}
		if (GuiScreen.isShiftKeyDown())
			RecipeTome.addTooltips(list);
		else
			list.add(StatCollector.translateToLocal("pe.tome.recipe.header"));
	}

	/** Check the hovered stack itself so NEI previews do not inherit ownership. */
	private static boolean isOwnedStack(ItemStack stack, EntityPlayer player)
	{
		if (player == null) return false;
		if (player.inventory.getItemStack() == stack) return true;
		for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
			if (player.inventory.getStackInSlot(slot) == stack) return true;
		}
		return false;
	}
}
