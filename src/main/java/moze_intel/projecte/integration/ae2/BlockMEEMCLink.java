package moze_intel.projecte.integration.ae2;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.ObjHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockMEEMCLink extends BlockContainer {

	@SideOnly(Side.CLIENT) private IIcon iconTop;
	@SideOnly(Side.CLIENT) private IIcon iconBottom;
	@SideOnly(Side.CLIENT) private IIcon iconSide;

	public BlockMEEMCLink() {
		super(Material.iron);
		setHardness(3.0F);
		setResistance(10.0F);
		setStepSound(soundTypeMetal);
		setCreativeTab(ObjHandler.tab);
		setBlockName("pe_me_emc_link");
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta) {
		return new TileMEEMCLink();
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
		super.onBlockPlacedBy(world, x, y, z, entity, stack);
		TileEntity te = world.getTileEntity(x, y, z);
		if (te instanceof TileMEEMCLink && entity instanceof EntityPlayer) {
			((TileMEEMCLink) te).setOwner((EntityPlayer) entity);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if (player != null && player.isSneaking()) {
			TileEntity te = world.getTileEntity(x, y, z);
			if (te instanceof TileMEEMCLink) ((TileMEEMCLink) te).setOwner(player);
			return true;
		}
		if (!world.isRemote) {
			// 假设分配的 GUI ID 是 120，后文我们需要在 GuiHandler 中注册它
			if (player != null) {
				player.openGui(PECore.instance, 120, world, x, y, z);
			}
		}
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister register) {
		iconTop = register.registerIcon("projecte:me_emc_link_top");
		iconBottom = register.registerIcon("projecte:me_emc_link_bottom");
		iconSide = register.registerIcon("projecte:me_emc_link_side");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta) {
		if (side == 0) return iconBottom != null ? iconBottom : blockIcon;
		if (side == 1) return iconTop != null ? iconTop : blockIcon;
		return iconSide != null ? iconSide : blockIcon;
	}
}
