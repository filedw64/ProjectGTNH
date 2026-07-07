package projectgtnh.gameObjs.items.itemEntities;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import projectgtnh.gameObjs.items.ItemPE;
import net.minecraft.client.renderer.texture.IIconRegister;

public class LightningProjectile extends ItemPE
{
	public LightningProjectile()
	{
		setCreativeTab(null);
		setUnlocalizedName("wind_projectile");
		setMaxStackSize(1);
	}

	@Override
	public void registerIcons(IIconRegister register)
	{
		itemIcon = register.registerIcon(getTexture("entities", "lightning"));
	}
}
