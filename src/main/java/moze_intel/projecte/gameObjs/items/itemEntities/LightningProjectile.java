package moze_intel.projecte.gameObjs.items.itemEntities;

import net.minecraft.client.renderer.texture.IIconRegister;
import moze_intel.projecte.gameObjs.items.ItemPE;

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
