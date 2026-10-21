package moze_intel.projecte.gameObjs.blocks.transport;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterfaceFluid;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterfaceItem;
import moze_intel.projecte.transport.MatterNetworkManager;
import moze_intel.projecte.transport.TransportType;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.MatterColorHelper;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 物质接口方块。metadata 0..15 = 染料颜色索引（与管道同一套信道颜色）。
 * 六个面各自可配「抽出方向 / 推入方向」（在 GUI 里点格子选方向），模式由方向推导：
 * 只设抽出 = 输入面，只设推入 = 输出面，两个都设 = 同面双向，都没设 = 关闭；
 * 贴着同类型同色管道即可组网。方块本身没有正面概念，正面 / 侧面图标只用于物品栏预览。
 */
public class BlockMatterInterface extends Block
{
	private final TransportType transportType;

	@SideOnly(Side.CLIENT)
	private IIcon[] sideIcons;
	@SideOnly(Side.CLIENT)
	private IIcon[] frontIcons;

	public BlockMatterInterface(TransportType transportType)
	{
		super(Material.iron);
		this.transportType = transportType;
		this.setBlockName("pe_matterInterface_" + transportType.getName());
		this.setHardness(2.0F);
		this.setResistance(10.0F);
		this.setStepSound(soundTypeMetal);
		// 16 色接口与管道一起放在物质物流页，ProjectE 主页不再出现
		this.setCreativeTab(ObjHandler.tabLogistics);
	}

	public TransportType getTransportType()
	{
		return transportType;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerBlockIcons(IIconRegister register)
	{
		String prefix = "projecte:transport/" + transportType.getName() + "_interface_";
		sideIcons = new IIcon[16];
		frontIcons = new IIcon[16];

		for (int i = 0; i < 16; i++)
		{
			sideIcons[i] = register.registerIcon(prefix + i + "_side");
			frontIcons[i] = register.registerIcon(prefix + i + "_front");
		}

		this.blockIcon = sideIcons[0];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(int side, int meta)
	{
		// 物品栏预览：北面显示正脸
		return side == ForgeDirection.NORTH.ordinal() ? frontIcons[meta & 15] : sideIcons[meta & 15];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side)
	{
		// 六个面已无正面之分，世界内一律用侧图标，状态靠 GUI 的格子底色表达
		return sideIcons[world.getBlockMetadata(x, y, z) & 15];
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ)
	{
		ItemStack held = player.getHeldItem();
		int color = MatterColorHelper.getDyeColor(held);

		if (color >= 0)
		{
			if (world.isRemote)
				return true;

			if ((world.getBlockMetadata(x, y, z) & 15) != color)
			{
				world.setBlockMetadataWithNotify(x, y, z, color, 3);
				MatterNetworkManager.INSTANCE.requestRebuild(world);

				if (!player.capabilities.isCreativeMode)
					--held.stackSize;
			}

			return true;
		}

		// 记录「玩家点开 GUI 的那一面」：GUI 的 3x3 主网格以它为「前」，随点击面旋转。
		// 客户端就地写一份好让 GUI 立刻有值，服务端写的才是权威值（随同步包下发）。
		TileEntity tile = world.getTileEntity(x, y, z);

		if (tile instanceof TileMatterInterface matterInterface)
		{
			matterInterface.setClickSide(side);

			if (!world.isRemote)
				matterInterface.syncToClients();
		}

		if (!world.isRemote)
			player.openGui(PECore.instance, getGuiId(), world, x, y, z);

		return true;
	}

	private int getGuiId()
	{
		return transportType == TransportType.ITEM ? Constants.MATTER_INTERFACE_ITEM_GUI : Constants.MATTER_INTERFACE_FLUID_GUI;
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

	// 满方块 + 不透明：走原版标准渲染，正面/侧面仅靠图标区分，不存在破面问题
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
		return transportType == TransportType.ITEM ? new TileMatterInterfaceItem() : new TileMatterInterfaceFluid();
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
		super.onNeighborBlockChange(world, x, y, z, neighbor);

		TileEntity tile = world.getTileEntity(x, y, z);

		if (tile instanceof TileMatterInterface)
			((TileMatterInterface) tile).invalidateTarget();
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta)
	{
		TileEntity tile = world.getTileEntity(x, y, z);

		if (tile instanceof IInventory inventory)
		{
			// 过滤槽是幽灵模板（只存标记、不消耗玩家物品），不可掉落；其后的真实存储槽仍照常掉落
			for (int i = TileMatterInterface.FILTER_SIZE; i < inventory.getSizeInventory(); i++)
			{
				ItemStack stack = inventory.getStackInSlot(i);

				if (stack != null)
					WorldHelper.spawnEntityItem(world, stack, x, y, z);
			}
		}

		super.breakBlock(world, x, y, z, block, meta);
		MatterNetworkManager.INSTANCE.requestRebuild(world);
	}

	/** 16 种颜色共用同一个方块名，metadata 直接作为颜色索引 */
	public static class ItemMatterInterface extends ItemBlock
	{
		private final Block interfaceBlock;

		public ItemMatterInterface(Block block)
		{
			super(block);
			this.interfaceBlock = block;
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
			return this.interfaceBlock.getUnlocalizedName();
		}

		/** 『物品物质接口（红）』：名字跟随 metadata 的染色信道 */
		@Override
		public String getItemStackDisplayName(ItemStack stack)
		{
			return MatterColorHelper.getColorizedName(super.getItemStackDisplayName(stack), stack.getItemDamage());
		}
	}
}
