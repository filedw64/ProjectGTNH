package moze_intel.projecte.integration.waila;

import cpw.mods.fml.common.Optional;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import moze_intel.projecte.api.tile.IEmcStorage;
import moze_intel.projecte.gameObjs.tiles.CollectorMK1Tile;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.text.DecimalFormat;
import java.util.List;

@Optional.Interface(iface = "mcp.mobius.waila.api.IWailaDataProvider", modid = "Waila")
public class PEWailaDataProvider implements IWailaDataProvider {

	public static final PEWailaDataProvider INSTANCE = new PEWailaDataProvider();
	private static final DecimalFormat EMC_FORMAT = new DecimalFormat("#,###.##");

	public static void register(IWailaRegistrar registrar) {
		// 通用注册：只要实现了 IEmcStorage 接口的 TileEntity 都能显示 EMC
		registrar.registerBodyProvider(INSTANCE, IEmcStorage.class);
		registrar.registerNBTProvider(INSTANCE, IEmcStorage.class);
	}

	@Override
	public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
		return null;
	}

	@Override
	public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
		return currenttip;
	}

	@Override
	public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
		NBTTagCompound tag = accessor.getNBTData();

		// 1. 显示生成速率 (收集器等)
		if (tag.hasKey("GenRate")) {
			double genRate = tag.getDouble("GenRate");
			if (genRate > 0) {
				currenttip.add(EnumChatFormatting.GREEN + "Generation: " + EnumChatFormatting.GOLD + "+" + EMC_FORMAT.format(genRate) + " EMC/s");
			}
		}

		// 2. 显示 EMC 缓存 (所有 IEmcStorage)
		if (tag.hasKey("StoredEMC") && tag.hasKey("MaxEMC")) {
			double stored = tag.getDouble("StoredEMC");
			double max = tag.getDouble("MaxEMC");
			if (max > 0) {
				currenttip.add(EnumChatFormatting.GRAY + "EMC: " + EnumChatFormatting.YELLOW + EMC_FORMAT.format(stored) + EnumChatFormatting.GRAY + " / " + EnumChatFormatting.YELLOW + EMC_FORMAT.format(max));
			}
		}

		return currenttip;
	}

	@Override
	public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
		return currenttip;
	}

	@Override
	public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y, int z) {
		// 核心性能优化：坚决不调用 te.writeToNBT(tag)!
		// 仅通过内存强转读取必需数据，将发包大小从 N KB 缩减到几十 Byte，且节省 CPU 序列化开销。

		if (te instanceof IEmcStorage) {
			IEmcStorage storage = (IEmcStorage) te;
			tag.setDouble("StoredEMC", storage.getStoredEmc());
			tag.setDouble("MaxEMC", storage.getMaximumEmc());
		}

		if (te instanceof CollectorMK1Tile) {
			CollectorMK1Tile collector = (CollectorMK1Tile) te;
			// 每秒生成速率 = (当前光照等级 * 基础发电量 / 16.0f) * 20 ticks
			float genPerTick = collector.getSunLevel() * collector.getEmcGen() / 16.0f;
			tag.setDouble("GenRate", genPerTick * 20.0f);
		}

		return tag;
	}
}
