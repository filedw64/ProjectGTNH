package projectgtnh.gameObjs.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import projectgtnh.PECore;
import projectgtnh.gameObjs.ObjHandler;
import projectgtnh.gameObjs.tiles.CondenserMK2Tile;
import projectgtnh.utils.Constants;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.Random;

public class CondenserMK2 extends Condenser
{
	public CondenserMK2()
	{
		super();
		this.setBlockName("pe_condenser_mk2");
	}

	@Override
	public Item getItemDropped(int par1, Random random, int par2)
	{
		return Item.getItemFromBlock(ObjHandler.condenserMk2);
	}

	@Override
	public int getRenderType()
	{
		return Constants.CONDENSER_MK2_RENDER_ID;
	}

	@Override
	public boolean hasTileEntity(int meta)
	{
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta)
	{
		return new CondenserMK2Tile();
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ)
	{
		if (!world.isRemote)
		{
			player.openGui(PECore.instance, Constants.CONDENSER_MK2_GUI, world, x, y, z);
		}

		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister register)
	{
		this.blockIcon = register.registerIcon("projecte:condenser_mk2");
	}
}
