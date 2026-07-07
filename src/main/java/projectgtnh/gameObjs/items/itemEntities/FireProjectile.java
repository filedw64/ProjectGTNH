package projectgtnh.gameObjs.items.itemEntities;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import projectgtnh.gameObjs.items.ItemPE;
import net.minecraft.client.renderer.texture.IIconRegister;

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
