package moze_intel.projecte.integration.ae2;

import appeng.api.AEApi;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import moze_intel.projecte.gameObjs.ObjHandler;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class AE2Integration {

	public static ItemMEEMCCell itemMEEMCCell;
	public static BlockMEEMCLink blockMEEMCLink;

	private static final Set<TileMEEMCLink> ACTIVE_TILES = Collections.newSetFromMap(new WeakHashMap<TileMEEMCLink, Boolean>());

	public static void registerTile(TileMEEMCLink tile) {
		if (tile != null) ACTIVE_TILES.add(tile);
	}

	public static void unregisterTile(TileMEEMCLink tile) {
		if (tile != null) ACTIVE_TILES.remove(tile);
	}

	public static void notifyHandlersForPlayer(UUID uuid) {
		if (uuid == null) return;

		for (TileMEEMCLink tile : ACTIVE_TILES) {
			if (tile != null && uuid.equals(tile.getOwnerUUID())) {
				tile.notifyGrid();
			}
		}

		if (itemMEEMCCell != null) {
			itemMEEMCCell.notifyGridForPlayer(uuid);
		}
	}

	public static void preInit() {
		itemMEEMCCell = new ItemMEEMCCell();
		GameRegistry.registerItem(itemMEEMCCell, "me_emc_cell");

		blockMEEMCLink = new BlockMEEMCLink();
		GameRegistry.registerBlock(blockMEEMCLink, "me_emc_link");
		GameRegistry.registerTileEntity(TileMEEMCLink.class, "projecte:tile_me_emc_link");
	}

	public static void init() {
		try {
			if (AEApi.instance() != null && AEApi.instance().registries() != null) {
				if (AEApi.instance().registries().cell() != null && itemMEEMCCell != null) {
					AEApi.instance().registries().cell().addCellHandler(itemMEEMCCell);
				}
			}
			addRecipes();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static void addRecipes() {
		if (AEApi.instance() == null || AEApi.instance().definitions() == null) return;

		ItemStack emptyHousing = AEApi.instance().definitions().materials().emptyStorageCell().maybeStack(1).orNull();
		ItemStack redMatter = new ItemStack(ObjHandler.matter, 1, 1);

		if (emptyHousing != null && redMatter != null) {
			GameRegistry.addRecipe(new ShapedOreRecipe(
				new ItemStack(itemMEEMCCell),
				"RMR",
				"MHM",
				"RMR",
				'H', emptyHousing,
				'M', redMatter,
				'R', new ItemStack(ObjHandler.matter, 1, 0)
			));
		}

		ItemStack ifaceBlock = AEApi.instance().definitions().blocks().iface().maybeStack(1).orNull();
		if (ifaceBlock != null) {
			GameRegistry.addRecipe(new ShapedOreRecipe(
				new ItemStack(blockMEEMCLink),
				"RIR", "RCR", "RIR",
				'I', ifaceBlock,
				'C', new ItemStack(ObjHandler.condenserMk2),
				'R', redMatter
			));
		}
	}
}
