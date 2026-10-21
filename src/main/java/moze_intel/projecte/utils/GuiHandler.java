package moze_intel.projecte.utils;

import cpw.mods.fml.common.network.IGuiHandler;
import moze_intel.projecte.gameObjs.container.ArcaneTransmutationContainer;
import moze_intel.projecte.gameObjs.gui.GUIArcaneTransmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import moze_intel.projecte.gameObjs.container.AlchBagContainer;
import moze_intel.projecte.gameObjs.container.AlchChestContainer;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import moze_intel.projecte.gameObjs.container.CollectorMK2Container;
import moze_intel.projecte.gameObjs.container.CollectorMK3Container;
import moze_intel.projecte.gameObjs.container.CondenserContainer;
import moze_intel.projecte.gameObjs.container.CondenserMK2Container;
import moze_intel.projecte.gameObjs.container.DMFurnaceContainer;
import moze_intel.projecte.gameObjs.container.EternalDensityContainer;
import moze_intel.projecte.gameObjs.container.MercurialEyeContainer;
import moze_intel.projecte.gameObjs.container.PedestalContainer;
import moze_intel.projecte.gameObjs.container.PhilosStoneContainer;
import moze_intel.projecte.gameObjs.container.RMFurnaceContainer;
import moze_intel.projecte.gameObjs.container.RelayMK1Container;
import moze_intel.projecte.gameObjs.container.RelayMK2Container;
import moze_intel.projecte.gameObjs.container.RelayMK3Container;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.AlchBagInventory;
import moze_intel.projecte.gameObjs.container.inventory.EternalDensityInventory;
import moze_intel.projecte.gameObjs.container.inventory.MercurialEyeInventory;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.gui.GUIAlchChest;
import moze_intel.projecte.gameObjs.gui.GUICollectorMK1;
import moze_intel.projecte.gameObjs.gui.GUICollectorMK2;
import moze_intel.projecte.gameObjs.gui.GUICollectorMK3;
import moze_intel.projecte.gameObjs.gui.GUICondenser;
import moze_intel.projecte.gameObjs.gui.GUICondenserMK2;
import moze_intel.projecte.gameObjs.gui.GUIDMFurnace;
import moze_intel.projecte.gameObjs.gui.GUIEternalDensity;
import moze_intel.projecte.gameObjs.gui.GUIMercurialEye;
import moze_intel.projecte.gameObjs.gui.GUIPedestal;
import moze_intel.projecte.gameObjs.gui.GUIPhilosStone;
import moze_intel.projecte.gameObjs.gui.GUIRMFurnace;
import moze_intel.projecte.gameObjs.gui.GUIRelayMK1;
import moze_intel.projecte.gameObjs.gui.GUIRelayMK2;
import moze_intel.projecte.gameObjs.gui.GUIRelayMK3;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.gameObjs.tiles.CollectorMK1Tile;
import moze_intel.projecte.gameObjs.tiles.CollectorMK2Tile;
import moze_intel.projecte.gameObjs.tiles.CollectorMK3Tile;
import moze_intel.projecte.gameObjs.tiles.CondenserMK2Tile;
import moze_intel.projecte.gameObjs.tiles.CondenserTile;
import moze_intel.projecte.gameObjs.tiles.DMFurnaceTile;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.gameObjs.tiles.RMFurnaceTile;
import moze_intel.projecte.gameObjs.tiles.RelayMK1Tile;
import moze_intel.projecte.gameObjs.tiles.RelayMK2Tile;
import moze_intel.projecte.gameObjs.tiles.RelayMK3Tile;
import moze_intel.projecte.gameObjs.container.transport.ContainerMatterInterface;
import moze_intel.projecte.gameObjs.gui.transport.GUIMatterInterface;
import moze_intel.projecte.gameObjs.gui.transport.nei.GUIMatterInterfaceNei;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import moze_intel.projecte.integration.Integration;
import moze_intel.projecte.transport.TransportType;

public class GuiHandler implements IGuiHandler {
	/** NEI 适配子类加载失败过一次就不再尝试，也不再重复刷日志 */
	private static boolean neiAdapterFailed;

	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		TileEntity tile = world.getTileEntity(x, y, z);

		switch (ID) {
			case Constants.ALCH_CHEST_GUI:
				if (tile instanceof AlchChestTile)
					return new AlchChestContainer(player.inventory, (AlchChestTile) tile);
				break;
			case Constants.ALCH_BAG_GUI:
				if (player.getHeldItem() != null) // 安全校验
					return new AlchBagContainer(player.inventory, new AlchBagInventory(player, player.getHeldItem()));
				break;
			case Constants.CONDENSER_GUI:
				if (tile instanceof CondenserTile)
					return new CondenserContainer(player.inventory, (CondenserTile) tile);
				break;
			case Constants.RM_FURNACE_GUI:
				if (tile instanceof RMFurnaceTile)
					return new RMFurnaceContainer(player.inventory, (RMFurnaceTile) tile);
				break;
			case Constants.DM_FURNACE_GUI:
				if (tile instanceof DMFurnaceTile)
					return new DMFurnaceContainer(player.inventory, (DMFurnaceTile) tile);
				break;
			case Constants.COLLECTOR1_GUI:
				if (tile instanceof CollectorMK1Tile)
					return new CollectorMK1Container(player.inventory, (CollectorMK1Tile) tile);
				break;
			case Constants.COLLECTOR2_GUI:
				if (tile instanceof CollectorMK2Tile)
					return new CollectorMK2Container(player.inventory, (CollectorMK2Tile) tile);
				break;
			case Constants.COLLECTOR3_GUI:
				if (tile instanceof CollectorMK3Tile)
					return new CollectorMK3Container(player.inventory, (CollectorMK3Tile) tile);
				break;
			case Constants.RELAY1_GUI:
				if (tile instanceof RelayMK1Tile)
					return new RelayMK1Container(player.inventory, (RelayMK1Tile) tile);
				break;
			case Constants.RELAY2_GUI:
				if (tile instanceof RelayMK2Tile)
					return new RelayMK2Container(player.inventory, (RelayMK2Tile) tile);
				break;
			case Constants.RELAY3_GUI:
				if (tile instanceof RelayMK3Tile)
					return new RelayMK3Container(player.inventory, (RelayMK3Tile) tile);
				break;
			case Constants.MERCURIAL_GUI:
				if (player.getHeldItem() != null)
					return new MercurialEyeContainer(player.inventory, new MercurialEyeInventory(player.getHeldItem()));
				break;
			case Constants.PHILOS_STONE_GUI:
				return new PhilosStoneContainer(player.inventory);
			case Constants.TRANSMUTATION_GUI:
				return new TransmutationContainer(player.inventory, new TransmutationInventory(player), false);
			case Constants.ETERNAL_DENSITY_GUI:
				if (player.getHeldItem() != null)
					return new EternalDensityContainer(player.inventory, new EternalDensityInventory(player.getHeldItem(), player));
				break;
			case Constants.CONDENSER_MK2_GUI:
				// 补充强制类型转换前的 instanceof 检查
				if (tile instanceof CondenserMK2Tile condenserMK2Tile)
					return new CondenserMK2Container(player.inventory, condenserMK2Tile);
				break;
			case Constants.PEDESTAL_GUI:
				// 补充强制类型转换前的 instanceof 检查
				if (tile instanceof DMPedestalTile dmPedestalTile)
					return new PedestalContainer(player.inventory, dmPedestalTile);
				break;
			case Constants.TRANSMUTATION_PORTABLE_GUI:
				return new TransmutationContainer(player.inventory, new TransmutationInventory(player), true);
			case Constants.ARCANE_TABLET_GUI:
				return new ArcaneTransmutationContainer(player.inventory, player);
			case Constants.MATTER_INTERFACE_ITEM_GUI:
				if (tile instanceof TileMatterInterface itemInterface && itemInterface.getTransportType() == TransportType.ITEM)
					return new ContainerMatterInterface(player.inventory, itemInterface);
				break;
			case Constants.MATTER_INTERFACE_FLUID_GUI:
				if (tile instanceof TileMatterInterface fluidInterface && fluidInterface.getTransportType() == TransportType.FLUID)
					return new ContainerMatterInterface(player.inventory, fluidInterface);
				break;
		}

		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		TileEntity tile = world.getTileEntity(x, y, z);

		switch (ID) {
			case Constants.ALCH_CHEST_GUI:
				if (tile instanceof AlchChestTile)
					return new GUIAlchChest(player.inventory, (AlchChestTile) tile);
				break;
			case Constants.ALCH_BAG_GUI:
				if (player.getHeldItem() != null)
					return new GUIAlchChest(player.inventory, new AlchBagInventory(player, player.getHeldItem()));
				break;
			case Constants.CONDENSER_GUI:
				if (tile instanceof CondenserTile)
					return new GUICondenser(player.inventory, (CondenserTile) tile);
				break;
			case Constants.RM_FURNACE_GUI:
				if (tile instanceof RMFurnaceTile)
					return new GUIRMFurnace(player.inventory, (RMFurnaceTile) tile);
				break;
			case Constants.DM_FURNACE_GUI:
				if (tile instanceof DMFurnaceTile)
					return new GUIDMFurnace(player.inventory, (DMFurnaceTile) tile);
				break;
			case Constants.COLLECTOR1_GUI:
				if (tile instanceof CollectorMK1Tile)
					return new GUICollectorMK1(player.inventory, (CollectorMK1Tile) tile);
				break;
			case Constants.COLLECTOR2_GUI:
				if (tile instanceof CollectorMK2Tile)
					return new GUICollectorMK2(player.inventory, (CollectorMK2Tile) tile);
				break;
			case Constants.COLLECTOR3_GUI:
				if (tile instanceof CollectorMK3Tile)
					return new GUICollectorMK3(player.inventory, (CollectorMK3Tile) tile);
				break;
			case Constants.RELAY1_GUI:
				if (tile instanceof RelayMK1Tile)
					return new GUIRelayMK1(player.inventory, (RelayMK1Tile) tile);
				break;
			case Constants.RELAY2_GUI:
				if (tile instanceof RelayMK2Tile)
					return new GUIRelayMK2(player.inventory, (RelayMK2Tile) tile);
				break;
			case Constants.RELAY3_GUI:
				if (tile instanceof RelayMK3Tile)
					return new GUIRelayMK3(player.inventory, (RelayMK3Tile) tile);
				break;
			case Constants.MERCURIAL_GUI:
				if (player.getHeldItem() != null)
					return new GUIMercurialEye(player.inventory, new MercurialEyeInventory(player.getHeldItem()));
				break;
			case Constants.PHILOS_STONE_GUI:
				return new GUIPhilosStone(player.inventory);
			case Constants.TRANSMUTATION_GUI:
				return new GUITransmutation(player.inventory, new TransmutationInventory(player), false);
			case Constants.ETERNAL_DENSITY_GUI:
				if (player.getHeldItem() != null)
					return new GUIEternalDensity(player.inventory, new EternalDensityInventory(player.getHeldItem(), player));
				break;
			case Constants.CONDENSER_MK2_GUI:
				// 补充强制类型转换前的 instanceof 检查
				if (tile instanceof CondenserMK2Tile condenserMK2Tile)
					return new GUICondenserMK2(player.inventory, condenserMK2Tile);
				break;
			case Constants.PEDESTAL_GUI:
				// 补充强制类型转换前的 instanceof 检查
				if (tile instanceof DMPedestalTile dmPedestalTile)
					return new GUIPedestal(player.inventory, dmPedestalTile);
				break;
			case Constants.TRANSMUTATION_PORTABLE_GUI:
				return new GUITransmutation(player.inventory, new TransmutationInventory(player), true);
			case Constants.ARCANE_TABLET_GUI:
				return new GUIArcaneTransmutation(player.inventory, player);
			case Constants.MATTER_INTERFACE_ITEM_GUI:
				if (tile instanceof TileMatterInterface itemInterface && itemInterface.getTransportType() == TransportType.ITEM)
					return createMatterInterfaceGui(player.inventory, itemInterface);
				break;
			case Constants.MATTER_INTERFACE_FLUID_GUI:
				if (tile instanceof TileMatterInterface fluidInterface && fluidInterface.getTransportType() == TransportType.FLUID)
					return createMatterInterfaceGui(player.inventory, fluidInterface);
				break;
		}

		return null;
	}

	/**
	 * 物质接口 GUI 的条件构造：只有 NEI 在场才挂 NEI 适配子类，否则返回普通 GUI。
	 *
	 * 为什么分支必须写在这里而不是 GUIMatterInterface 里：那个分支的字节码必须引用 GUIMatterInterfaceNei，
	 * 而后者 implements INEIGuiHandler —— 一旦 GUIMatterInterface 自己引用了它，无 NEI 的整合包又会类加载失败。
	 * NEI 判定复用项目统一的开关 Integration.NEI（Integration.modChecks 里 Loader.isModLoaded 的收口），
	 * 并用 catch (Throwable) 兜住 NoClassDefFoundError / LinkageError：加载失败也只是少一个拖放功能。
	 *
	 * 返回类型刻意是 Object（调用方 getClientGuiElement 本来就返回 Object）：这样这里不需要对
	 * GUIMatterInterfaceNei 做赋值校验，类加载推迟到真正 new 的那一刻，异常才能被本方法捕获。
	 */
	private static Object createMatterInterfaceGui(InventoryPlayer invPlayer, TileMatterInterface tile) {
		if (Integration.NEI && !neiAdapterFailed) {
			try {
				return new GUIMatterInterfaceNei(invPlayer, tile);
			} catch (Throwable t) {
				neiAdapterFailed = true;
				PELogger.logDebug("NEI GUI adapter unavailable for GUIMatterInterface, falling back to the plain GUI: %s", t.toString());
			}
		}

		return new GUIMatterInterface(invPlayer, tile);
	}
}
