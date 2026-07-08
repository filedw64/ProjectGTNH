package moze_intel.projecte.gameObjs.items.itemEntities;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import moze_intel.projecte.gameObjs.items.ItemPE;

public class WaterOrb extends ItemPE
{
	public WaterOrb()
	{
		this.setCreativeTab(null);
		this.setUnlocalizedName("water_orb");
		this.setMaxStackSize(1);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("entities", "water_orb"));
	}
}
