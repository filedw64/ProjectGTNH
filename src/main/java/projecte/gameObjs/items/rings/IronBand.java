package projecte.gameObjs.items.rings;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import projecte.gameObjs.items.ItemPE;

public class IronBand extends ItemPE
{
	public IronBand()
	{
		this.setUnlocalizedName("ring_iron_band");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("rings", "iron_band"));//"ee2:rings/iron_band");
	}
}
