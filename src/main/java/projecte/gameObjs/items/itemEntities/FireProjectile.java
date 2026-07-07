package projecte.gameObjs.items.itemEntities;

import net.minecraft.client.renderer.texture.IIconRegister;
import projecte.gameObjs.items.ItemPE;

public class FireProjectile extends ItemPE
{
	public FireProjectile()
	{
		setCreativeTab(null);
		setUnlocalizedName("fire_projectile");
		setMaxStackSize(1);
	}

	@Override
	public void registerIcons(IIconRegister register)
	{
		itemIcon = register.registerIcon(getTexture("entities", "fireball"));
	}
}
