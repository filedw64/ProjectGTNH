package moze_intel.projecte.gameObjs.blocks.transport;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterPipe;
import moze_intel.projecte.transport.MatterNetworkManager;
import moze_intel.projecte.transport.TransportType;
import moze_intel.projecte.utils.MatterColorHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 物质管道方块。metadata 0..15 = 染料颜色索引（频率信道），
 * 只有同类型 + 同颜色的相邻管道才连成一张网。
 */
public class BlockMatterPipe extends Block
{
	/** 顺序 = tier：0 暗物质、1 红物质、2 Gem；同时决定注册名后缀与贴图前缀 */
	private static final String[] TIER_NAMES = new String[] {"dark", "red", "gem"};

	private final TransportType transportType;
	private final int tier;

	@SideOnly(Side.CLIENT)
	private IIcon[] plainIcons;
	@SideOnly(Side.CLIENT)
	private IIcon[] connectedIcons;

	public BlockMatterPipe(TransportType transportType, int tier)
	{
		super(Material.glass);
		this.transportType = transportType;
		this.tier = tier;
		this.setBlockName("pe_matterPipe_" + transportType.getName() + "_" + getTierName());
		this.setHardness(0.5F);
		this.setStepSound(soundTypeMetal);
		// 16 色管道单独占一页，ProjectE 主页不再出现
		this.setCreativeTab(ObjHandler.tabLogistics);
	}

	public TransportType getTransportType()
	{
		return transportType;
	}

	public int getTier()
	{
		return tier;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister register)
	{
		String prefix = "projecte:transport/" + getTexturePrefix() + "_";
		plainIcons = new IIcon[16];
		connectedIcons = new IIcon[16];

		for (int i = 0; i < 16; i++)
		{
			plainIcons[i] = register.registerIcon(prefix + i + "_plain");
			connectedIcons[i] = register.registerIcon(prefix + i + "_connected");
		}

		this.blockIcon = plainIcons[0];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta)
	{
		return plainIcons[meta & 15];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side)
	{
		int meta = world.getBlockMetadata(x, y, z) & 15;
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		Block neighbor = world.getBlock(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);

		if (neighbor instanceof BlockMatterPipe && ((BlockMatterPipe) neighbor).transportType == transportType)
			return connectedIcons[meta];

		return plainIcons[meta];
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ)
	{
		ItemStack held = player.getHeldItem();
		int color = MatterColorHelper.getDyeColor(held);

		if (color < 0)
			return false;

		if (world.isRemote)
			return true;

		if ((world.getBlockMetadata(x, y, z) & 15) != color)
		{
			world.setBlockMetadataWithNotify(x, y, z, color, 3);

			TileEntity tile = world.getTileEntity(x, y, z);

			if (tile instanceof TileMatterPipe)
				((TileMatterPipe) tile).refresh();

			MatterNetworkManager.INSTANCE.requestRebuild(world);

			if (!player.capabilities.isCreativeMode)
				--held.stackSize;
		}

		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void getSubBlocks(Item item, CreativeTabs tab, List list)
	{
		for (int i = 0; i < 16; i++)
			list.add(new ItemStack(item, 1, i));
	}

	@Override
	public int damageDropped(int meta)
	{
		return meta & 15;
	}

	@Override
	public boolean isOpaqueCube()
	{
		return true;
	}

	@Override
	public boolean renderAsNormalBlock()
	{
		return true;
	}

	@Override
	public boolean hasTileEntity(int meta)
	{
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, int meta)
	{
		return new TileMatterPipe();
	}

	@Override
	public void onBlockAdded(World world, int x, int y, int z)
	{
		super.onBlockAdded(world, x, y, z);
		MatterNetworkManager.INSTANCE.requestRebuild(world);
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor)
	{
		// 玩家把设备贴到已有管道旁边时只有这里能被叫到：立刻要求重扫，
		// 不必等 EMC 设备缓存的 20 tick 兜底
		if (!world.isRemote && transportType == TransportType.EMC)
			MatterNetworkManager.INSTANCE.requestRebuild(world);
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta)
	{
		super.breakBlock(world, x, y, z, block, meta);
		MatterNetworkManager.INSTANCE.requestRebuild(world);
	}

	/** tier 超出表长时退回最低档，避免拿不到贴图 */
	private String getTierName()
	{
		return TIER_NAMES[tier < 0 ? 0 : Math.min(tier, TIER_NAMES.length - 1)];
	}

	private String getTexturePrefix()
	{
		return transportType.getName() + "_" + getTierName();
	}

	/** 16 种颜色共用同一个方块名，metadata 直接作为颜色索引 */
	public static class ItemMatterPipe extends ItemBlock
	{
		private final Block pipeBlock;

		public ItemMatterPipe(Block block)
		{
			super(block);
			this.pipeBlock = block;
			this.setMaxDamage(0);
			this.hasSubtypes = true;
		}

		@Override
		public int getMetadata(int meta)
		{
			return meta;
		}

		@Override
		public String getUnlocalizedName(ItemStack stack)
		{
			return this.pipeBlock.getUnlocalizedName();
		}

		/** 『暗物质物品管道（红）』：名字跟随 metadata 的染色信道 */
		@Override
		public String getItemStackDisplayName(ItemStack stack)
		{
			return MatterColorHelper.getColorizedName(super.getItemStackDisplayName(stack), stack.getItemDamage());
		}
	}
}
