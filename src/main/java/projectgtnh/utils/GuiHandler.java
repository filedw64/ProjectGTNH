package projectgtnh.utils;

import cpw.mods.fml.common.network.IGuiHandler;
import projectgtnh.gameObjs.container.AlchBagContainer;
import projectgtnh.gameObjs.container.AlchChestContainer;
import projectgtnh.gameObjs.container.CollectorMK1Container;
import projectgtnh.gameObjs.container.CollectorMK2Container;
import projectgtnh.gameObjs.container.CollectorMK3Container;
import projectgtnh.gameObjs.container.CondenserContainer;
import projectgtnh.gameObjs.container.CondenserMK2Container;
import projectgtnh.gameObjs.container.DMFurnaceContainer;
import projectgtnh.gameObjs.container.EternalDensityContainer;
import projectgtnh.gameObjs.container.MercurialEyeContainer;
import projectgtnh.gameObjs.container.PedestalContainer;
import projectgtnh.gameObjs.container.PhilosStoneContainer;
import projectgtnh.gameObjs.container.RMFurnaceContainer;
import projectgtnh.gameObjs.container.RelayMK1Container;
import projectgtnh.gameObjs.container.RelayMK2Container;
import projectgtnh.gameObjs.container.RelayMK3Container;
import projectgtnh.gameObjs.container.TransmutationContainer;
import projectgtnh.gameObjs.container.inventory.AlchBagInventory;
import projectgtnh.gameObjs.container.inventory.EternalDensityInventory;
import projectgtnh.gameObjs.container.inventory.MercurialEyeInventory;
import projectgtnh.gameObjs.container.inventory.TransmutationInventory;
import projectgtnh.gameObjs.gui.GUIAlchChest;
import projectgtnh.gameObjs.gui.GUICollectorMK1;
import projectgtnh.gameObjs.gui.GUICollectorMK2;
import projectgtnh.gameObjs.gui.GUICollectorMK3;
import projectgtnh.gameObjs.gui.GUICondenser;
import projectgtnh.gameObjs.gui.GUICondenserMK2;
import projectgtnh.gameObjs.gui.GUIDMFurnace;
import projectgtnh.gameObjs.gui.GUIEternalDensity;
import projectgtnh.gameObjs.gui.GUIMercurialEye;
import projectgtnh.gameObjs.gui.GUIPedestal;
import projectgtnh.gameObjs.gui.GUIPhilosStone;
import projectgtnh.gameObjs.gui.GUIRMFurnace;
import projectgtnh.gameObjs.gui.GUIRelayMK1;
import projectgtnh.gameObjs.gui.GUIRelayMK2;
import projectgtnh.gameObjs.gui.GUIRelayMK3;
import projectgtnh.gameObjs.gui.GUITransmutation;
import projectgtnh.gameObjs.tiles.AlchChestTile;
import projectgtnh.gameObjs.tiles.CollectorMK1Tile;
import projectgtnh.gameObjs.tiles.CollectorMK2Tile;
import projectgtnh.gameObjs.tiles.CollectorMK3Tile;
import projectgtnh.gameObjs.tiles.CondenserMK2Tile;
import projectgtnh.gameObjs.tiles.CondenserTile;
import projectgtnh.gameObjs.tiles.DMFurnaceTile;
import projectgtnh.gameObjs.tiles.DMPedestalTile;
import projectgtnh.gameObjs.tiles.RMFurnaceTile;
import projectgtnh.gameObjs.tiles.RelayMK1Tile;
import projectgtnh.gameObjs.tiles.RelayMK2Tile;
import projectgtnh.gameObjs.tiles.RelayMK3Tile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class GuiHandler implements IGuiHandler
{
	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z)
	{
		TileEntity tile = world.getTileEntity(x, y, z);
		
		switch (ID)
		{
			case Constants.ALCH_CHEST_GUI:
				if (tile != null && tile instanceof AlchChestTile)
					return new AlchChestContainer(player.inventory, (AlchChestTile) tile);
				break;
			case Constants.ALCH_BAG_GUI:
				return new AlchBagContainer(player.inventory, new AlchBagInventory(player, player.getHeldItem()));
			case Constants.CONDENSER_GUI:
				if (tile != null && tile instanceof CondenserTile)
					return new CondenserContainer(player.inventory, (CondenserTile) tile);
				break;
			case Constants.RM_FURNACE_GUI:
				if (tile != null && tile instanceof RMFurnaceTile)
					return new RMFurnaceContainer(player.inventory, (RMFurnaceTile) tile);
				break;
			case Constants.DM_FURNACE_GUI:
				if (tile != null && tile instanceof DMFurnaceTile)
					return new DMFurnaceContainer(player.inventory, (DMFurnaceTile) tile);
				break;
			case Constants.COLLECTOR1_GUI:
				if (tile != null && tile instanceof CollectorMK1Tile)
					return new CollectorMK1Container(player.inventory, (CollectorMK1Tile) tile);
				break;
			case Constants.COLLECTOR2_GUI:
				if (tile != null && tile instanceof CollectorMK2Tile)
					return new CollectorMK2Container(player.inventory, (CollectorMK2Tile) tile);
				break;
			case Constants.COLLECTOR3_GUI:
				if (tile != null && tile instanceof CollectorMK3Tile)
					return new CollectorMK3Container(player.inventory, (CollectorMK3Tile) tile);
				break;
			case Constants.RELAY1_GUI:
				if (tile != null && tile instanceof RelayMK1Tile)
					return new RelayMK1Container(player.inventory, (RelayMK1Tile) tile);
				break;
			case Constants.RELAY2_GUI:
				if (tile != null && tile instanceof RelayMK2Tile)
					return new RelayMK2Container(player.inventory, (RelayMK2Tile) tile);
				break;
			case Constants.RELAY3_GUI:
				if (tile != null && tile instanceof RelayMK3Tile)
					return new RelayMK3Container(player.inventory, (RelayMK3Tile) tile);
				break;
			case Constants.MERCURIAL_GUI:
				return new MercurialEyeContainer(player.inventory, new MercurialEyeInventory(player.getHeldItem()));
			case Constants.PHILOS_STONE_GUI:
				return new PhilosStoneContainer(player.inventory);
			case Constants.TRANSMUTATION_GUI:
				return new TransmutationContainer(player.inventory, new TransmutationInventory(player));
			case Constants.ETERNAL_DENSITY_GUI:
				return new EternalDensityContainer(player.inventory, new EternalDensityInventory(player.getHeldItem(), player));
			case Constants.CONDENSER_MK2_GUI:
				return new CondenserMK2Container(player.inventory, (CondenserMK2Tile) tile);
			case Constants.PEDESTAL_GUI:
				return new PedestalContainer(player.inventory, ((DMPedestalTile) tile));
		}
		
		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) 
	{
		TileEntity tile = world.getTileEntity(x, y, z);
		
		switch (ID)
		{
			case Constants.ALCH_CHEST_GUI:
				if (tile != null && tile instanceof AlchChestTile)
					return new GUIAlchChest(player.inventory, (AlchChestTile) tile);
				break;
			case Constants.ALCH_BAG_GUI:
				return new GUIAlchChest(player.inventory, new AlchBagInventory(player, player.getHeldItem()));
			case Constants.CONDENSER_GUI:
				if (tile != null && tile instanceof CondenserTile)
					return new GUICondenser(player.inventory, (CondenserTile) tile);
				break;
			case Constants.RM_FURNACE_GUI:
				if (tile != null && tile instanceof RMFurnaceTile)
					return new GUIRMFurnace(player.inventory, (RMFurnaceTile) tile);
				break;
			case Constants.DM_FURNACE_GUI:
				if (tile != null && tile instanceof DMFurnaceTile)
					return new GUIDMFurnace(player.inventory, (DMFurnaceTile) tile);
				break;
			case Constants.COLLECTOR1_GUI:
				if (tile != null && tile instanceof CollectorMK1Tile)
					return new GUICollectorMK1(player.inventory, (CollectorMK1Tile) tile);
				break;
			case Constants.COLLECTOR2_GUI:
				if (tile != null && tile instanceof CollectorMK2Tile)
					return new GUICollectorMK2(player.inventory, (CollectorMK2Tile) tile);
				break;
			case Constants.COLLECTOR3_GUI:
				if (tile != null && tile instanceof CollectorMK3Tile)
					return new GUICollectorMK3(player.inventory, (CollectorMK3Tile) tile);
				break;
			case Constants.RELAY1_GUI:
				if (tile != null && tile instanceof RelayMK1Tile)
					return new GUIRelayMK1(player.inventory, (RelayMK1Tile) tile);
				break;
			case Constants.RELAY2_GUI:
				if (tile != null && tile instanceof RelayMK2Tile)
					return new GUIRelayMK2(player.inventory, (RelayMK2Tile) tile);
				break;
			case Constants.RELAY3_GUI:
				if (tile != null && tile instanceof RelayMK3Tile)
					return new GUIRelayMK3(player.inventory, (RelayMK3Tile) tile);
				break;
			case Constants.MERCURIAL_GUI:
				return new GUIMercurialEye(player.inventory, new MercurialEyeInventory(player.getHeldItem()));
			case Constants.PHILOS_STONE_GUI:
				return new GUIPhilosStone(player.inventory);
			case Constants.TRANSMUTATION_GUI:
				return new GUITransmutation(player.inventory, new TransmutationInventory(player));
			case Constants.ETERNAL_DENSITY_GUI:
				player.getHeldItem();
				return new GUIEternalDensity(player.inventory, new EternalDensityInventory(player.getHeldItem(), player));
			case Constants.CONDENSER_MK2_GUI:
				return new GUICondenserMK2(player.inventory, (CondenserMK2Tile) tile);
			case Constants.PEDESTAL_GUI:
				return new GUIPedestal(player.inventory, ((DMPedestalTile) tile));
		}
		
		return null;
	}
}
