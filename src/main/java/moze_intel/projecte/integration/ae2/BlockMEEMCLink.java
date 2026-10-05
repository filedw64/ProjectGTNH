package moze_intel.projecte.integration.ae2;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.ObjHandler;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public class BlockMEEMCLink extends BlockContainer {
    public BlockMEEMCLink() {
        super(Material.iron);
        setBlockName("pe_me_emc_link");
        setCreativeTab(ObjHandler.tab);
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileMEEMCLink(); }
    @Override public int getRenderType() { return 0; }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileMEEMCLink) ((TileMEEMCLink) tile).setOwner((EntityPlayer) placer);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof TileMEEMCLink)) return false;
        TileMEEMCLink tile = (TileMEEMCLink) raw;
        if (tile.getOwnerUUID() == null) tile.setOwner(player);
        if (!tile.isUseableByPlayer(player)) {
            player.addChatMessage(new ChatComponentTranslation("pe.ae2.owner_only"));
            return true;
        }
        player.openGui(PECore.instance, AE2Integration.GUI_ID, world, x, y, z);
        return true;
    }


    // AppliedE core refinement v2
    // AppliedE core hardening v3: contents are exported by the break hook, not by getDrops.
    // Keeping getDrops side-effect-free prevents inspection/fortune probes duplicating queues.
    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        if (!world.isRemote) {
            TileEntity raw = world.getTileEntity(x, y, z);
            if (raw instanceof TileMEEMCLink) ((TileMEEMCLink) raw).releaseEMCOutputsOnBreak();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon("projecte:dm");
    }
}
