package projectgtnh.gameObjs.blocks;


import cpw.mods.fml.common.network.NetworkRegistry;
import projectgtnh.PECore;
import projectgtnh.api.item.IPedestalItem;
import projectgtnh.gameObjs.ObjHandler;
import projectgtnh.gameObjs.tiles.DMPedestalTile;
import projectgtnh.gameObjs.tiles.TileEmc;
import projectgtnh.network.PacketHandler;
import projectgtnh.network.packets.SyncPedestalPKT;
import projectgtnh.utils.Constants;
import projectgtnh.utils.PELogger;
import projectgtnh.utils.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class Pedestal extends Block {

    public Pedestal() {
        super(Material.rock);
        this.setCreativeTab(ObjHandler.cTab);
        this.setHardness(1.0F);
        this.setBlockBounds(0.1875F, 0.0F, 0.1875F, 0.8125F, 0.75F, 0.8125F);
        this.setBlockTextureName(PECore.MODID.toLowerCase() + ":dm");
        setBlockName("pe_dmPedestal");
    }

    public void breakBlock(World world, int x, int y, int z, Block block, int meta)
    {
        DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
        if (tile.getItemStack() != null)
        {
            WorldHelper.spawnEntityItem(world, tile.getItemStack().copy(), x, y, z);
        }
        tile.invalidate();
        super.breakBlock(world, x, y, z, block, meta);
    }

    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ)
    {
        if (!world.isRemote)
        {
            DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
            if (player.isSneaking())
            {
                player.openGui(PECore.instance, Constants.PEDESTAL_GUI, world, x, y, z);
            }
            else
            {
                if (tile.getItemStack() != null && tile.getItemStack().getItem() instanceof IPedestalItem)
                {
                    tile.setActive(!tile.getActive());
                }
                PELogger.logDebug("Pedestal: " + (tile.getActive() ? "ON" : "OFF"));
            }
            PacketHandler.sendToAllAround(new SyncPedestalPKT(tile), new NetworkRegistry.TargetPoint(world.provider.dimensionId, x, y, z, 32));
        }
        return true;
    }

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase ent, ItemStack stack)
	{
		TileEntity tile = world.getTileEntity(x, y, z);
		if (stack.hasTagCompound() && stack.stackTagCompound.getBoolean("ProjectGTNHBlock") && tile instanceof TileEmc)
		{
			stack.stackTagCompound.setInteger("x", x);
			stack.stackTagCompound.setInteger("y", y);
			stack.stackTagCompound.setInteger("z", z);

			tile.readFromNBT(stack.stackTagCompound);
		}
	}

	@Override
    public boolean renderAsNormalBlock()
    {
        return false;
    }

    @Override
    public boolean isOpaqueCube()
    {
        return false;
    }

    @Override
    public int getRenderType()
    {
        return Constants.PEDESTAL_RENDER_ID;
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z)
    {
        return 12;
    }

    @Override
    public boolean hasTileEntity(int meta)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new DMPedestalTile();
    }
}
