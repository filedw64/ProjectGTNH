package moze_intel.projecte.gameObjs.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.tiles.TileAEGU;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockAEGU extends BlockContainer
{
	@SideOnly(Side.CLIENT)
	private IIcon iconError;
	@SideOnly(Side.CLIENT)
	private IIcon iconIdle;
	@SideOnly(Side.CLIENT)
	private IIcon iconActive;

	public BlockAEGU()
	{
		super(Material.iron);
		this.setBlockName("pe_aegu");
		this.setCreativeTab(ObjHandler.cTab);
		this.setHardness(3.0F);
		this.setResistance(10.0F);
		this.setLightOpacity(0); // 保证不阻挡收集器吸收阳光
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta)
	{
		return new TileAEGU();
	}

	@Override
	public boolean isOpaqueCube()
	{
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister register)
	{
		this.iconError = register.registerIcon("projecte:aegu_error");
		this.iconIdle = register.registerIcon("projecte:aegu_idle");
		this.iconActive = register.registerIcon("projecte:aegu_active");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta)
	{
		// 根据元数据返回不同的贴图，6个面一致
		if (meta == 1) return iconIdle;
		if (meta == 2) return iconActive;
		return iconError; // meta 为 0 或其他异常值时返回报错贴图（希望不会出Bug……）
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ)
	{
		if (world.isRemote) return true;

		if (player.getHeldItem() != null && player.getHeldItem().getItem() == ObjHandler.philosStone)
		{
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile instanceof TileAEGU)
			{
				if (!((TileAEGU) tile).hasCollectorBelow()) {
					player.addChatMessage(new ChatComponentTranslation("pe.aegu.nocollector"));
					return true;
				}

				player.getHeldItem().stackTagCompound.setInteger("aegu_bind_x", x);
				player.getHeldItem().stackTagCompound.setInteger("aegu_bind_y", y);
				player.getHeldItem().stackTagCompound.setInteger("aegu_bind_z", z);
				player.getHeldItem().stackTagCompound.setInteger("aegu_bind_dim", world.provider.dimensionId);

				player.addChatMessage(new ChatComponentTranslation("pe.aegu.selected"));
			}
			return true;
		}
		return false;
	}

	// 瞬间响应环境变化（不用等Tick）
	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor)
	{
		if (!world.isRemote)
		{
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile instanceof TileAEGU)
			{
				((TileAEGU) tile).validateEnvironment(); // 邻居方块改变时立刻校验，刷新贴图
			}
		}
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entityLiving, ItemStack stack)
	{
		if (!world.isRemote)
		{
			TileEntity tile = world.getTileEntity(x, y, z);
			if (tile instanceof TileAEGU)
			{
				((TileAEGU) tile).validateEnvironment(); // 放置时立刻校验一次以显示正确的贴图
			}
		}
	}
}
