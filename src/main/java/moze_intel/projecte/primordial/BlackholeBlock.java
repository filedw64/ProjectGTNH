package moze_intel.projecte.primordial;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import moze_intel.projecte.gameObjs.ObjHandler;

public final class BlackholeBlock extends BlockContainer {
    public BlackholeBlock() {
        super(Material.iron); setBlockName("pe_primordial_blackhole"); setBlockTextureName("projecte:blackhole");
        setCreativeTab(ObjHandler.tab); setHardness(3); setResistance(600);
    }
    @Override public int getRenderType() { return 0; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BlackholeTile(); }
    @Override public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity tile = w.getTileEntity(x, y, z);
        if (!w.isRemote && tile instanceof BlackholeTile) ((BlackholeTile) tile).setOwner(placer.getUniqueID());
    }
    @Override public void breakBlock(World w, int x, int y, int z, Block block, int meta) {
        TileEntity tile = w.getTileEntity(x, y, z);
        if (tile instanceof BlackholeTile) ((BlackholeTile) tile).settle();
        super.breakBlock(w, x, y, z, block, meta);
    }
}
