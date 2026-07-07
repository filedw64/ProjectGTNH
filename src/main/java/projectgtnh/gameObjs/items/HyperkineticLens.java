package projectgtnh.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import projectgtnh.api.item.IProjectileShooter;
import projectgtnh.gameObjs.entity.EntityLensProjectile;
import projectgtnh.utils.Constants;
import projectgtnh.utils.PlayerHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class HyperkineticLens extends ItemCharge implements IProjectileShooter
{
	public HyperkineticLens() 
	{
		super("hyperkinetic_lens", (byte)3);
		this.setNoRepair();
	}
	
	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (world.isRemote) return stack;
		
		if (shootProjectile(player, stack))
		{
			PlayerHelper.swingItem(player);
		}
		
		return stack;
	}
	
	@Override
	public boolean shootProjectile(EntityPlayer player, ItemStack stack) 
	{
		World world = player.worldObj;
		int requiredEmc = Constants.EXPLOSIVE_LENS_COST[this.getCharge(stack)];
		
		if (!consumeFuel(player, stack, requiredEmc, true))
		{
			return false;
		}

		world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
		world.spawnEntityInWorld(new EntityLensProjectile(world, player, this.getCharge(stack)));
		return true;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		this.itemIcon = register.registerIcon(this.getTexture("hyper_lens"));
	}
}
