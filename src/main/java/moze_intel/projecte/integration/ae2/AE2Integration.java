package moze_intel.projecte.integration.ae2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

/** Loaded only after the appliedenergistics2 mod-presence check. */
public final class AE2Integration {
    public static final int GUI_ID = 20;
    public static BlockMEEMCLink blockMEEMCLink;
    public static ItemMEEMCCell itemMEEMCCell;
    public static ItemEMCResource itemEMCResource;
    // AppliedE core hardening v3
    public static ItemEMCTransmutationPattern itemEMCTransmutationPattern;
    public static ItemEMCRecoveryBundle itemEMCRecoveryBundle;

    // AppliedE persistent recovery v4
    public static void drainPersistentRecovery() { EMCRecoveryLedger.get().drainKnowledge(); }
    private static void drainKnowledge() { drainPersistentRecovery(); }
    public static void recoveryServerStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandEMCRecovery());
    }
    public static void recoveryServerStopping() {
        // Every mutation was already durable; this drain only tries to deliver knowledge, never refunds.
        EMCRecoveryLedger.get().drainKnowledge();
    }
    private static final Set<TileMEEMCLink> TILES = Collections.newSetFromMap(new WeakHashMap<TileMEEMCLink, Boolean>());
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<UUID, ExactEMC> BALANCES = new HashMap<>();
    private static boolean initialized;
    private int ticks;

    private AE2Integration() {}

    public static void preInit() {
        if (initialized) return;
        blockMEEMCLink = new BlockMEEMCLink();
        itemMEEMCCell = new ItemMEEMCCell();
        itemEMCResource = new ItemEMCResource();
        itemEMCTransmutationPattern = new ItemEMCTransmutationPattern();
        itemEMCRecoveryBundle = new ItemEMCRecoveryBundle();
        GameRegistry.registerItem(itemEMCRecoveryBundle, "emc_recovery_bundle");
        GameRegistry.registerItem(itemEMCResource, "emc_resource");
        GameRegistry.registerItem(itemEMCTransmutationPattern, "emc_transmutation_pattern");
        AEApi.instance().registries().gridCache().registerGridCache(EMCKnowledgeGridCache.class, EMCKnowledgeGridCache.class);
        GameRegistry.registerBlock(blockMEEMCLink, "me_emc_link");
        GameRegistry.registerTileEntity(TileMEEMCLink.class, "ProjectE:MEEMCLink");
        GameRegistry.registerItem(itemMEEMCCell, "me_emc_cell");
        AEApi.instance().registries().cell().addCellHandler(itemMEEMCCell);
        AE2Integration events = new AE2Integration();
        FMLCommonHandler.instance().bus().register(events);
        MinecraftForge.EVENT_BUS.register(events);
        initialized = true;
    }

    public static void initRecipes() {
        // Resolve the AE2 ingredient by registry name rather than assuming a modern definition API.
        ItemStack processor = GameRegistry.findItemStack("appliedenergistics2", "item.ItemMultiMaterial", 1);
        if (processor == null) throw new IllegalStateException("AE2 695 material registry entry not found");
        processor.setItemDamage(43); // Formation core; verified against local MaterialType.
        GameRegistry.addShapedRecipe(new ItemStack(blockMEEMCLink), "IPI", "MTM", "IPI",
            'I', Items.iron_ingot, 'P', processor, 'M', new ItemStack(ObjHandler.matter, 1, 0),
            'T', ObjHandler.transmuteStone);
        GameRegistry.addShapedRecipe(new ItemStack(itemMEEMCCell), "IGI", "GLG", "IGI",
            'I', Items.iron_ingot, 'G', Items.glowstone_dust, 'L', blockMEEMCLink);
    }

    public static void registerTile(TileMEEMCLink tile) {
        if (tile != null && tile.getWorldObj() != null && !tile.getWorldObj().isRemote && !tile.isInvalid())
            TILES.add(tile);
    }

    public static void unregisterTile(TileMEEMCLink tile) { TILES.remove(tile); }

    /** Never re-enter network storage during a running injection/extraction. */
    public static void notifyHandlersForPlayer(UUID uuid) {
        if (uuid != null) DIRTY.add(uuid);
    }

    @SubscribeEvent
    public void knowledgeChanged(PlayerKnowledgeChangeEvent event) {
        notifyHandlersForPlayer(event.playerUUID);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        drainKnowledge();
        itemMEEMCCell.refreshHostedSnapshots();
        if (++ticks % 20 != 0) return;
        Set<UUID> owners = new HashSet<>();
        for (TileMEEMCLink tile : new ArrayList<>(TILES)) {
            if (!tile.isInvalid() && tile.getOwnerUUID() != null) owners.add(tile.getOwnerUUID());
        }
        owners.addAll(itemMEEMCCell.getTrackedOwners());
        for (UUID uuid : owners) {
            EntityPlayer player = EMCInventoryHandler.resolveOnlinePlayer(uuid);
            ExactEMC current = player == null ? null : Transmutation.getEmcExact(player);
            if (!java.util.Objects.equals(current, BALANCES.get(uuid))) DIRTY.add(uuid);
            // Include the first observation even if the owner is offline.
            if (!BALANCES.containsKey(uuid)) DIRTY.add(uuid);
            BALANCES.put(uuid, current);
        }
        BALANCES.keySet().retainAll(owners);
        Set<UUID> pending = new HashSet<>(DIRTY);
        DIRTY.clear();
        Set<IGrid> grids = new HashSet<>();
        for (TileMEEMCLink tile : new ArrayList<>(TILES)) {
            if (pending.contains(tile.getOwnerUUID())) tile.collectGrid(grids);
        }
        for (UUID uuid : pending) itemMEEMCCell.collectGrids(uuid, grids);
        for (IGrid grid : grids) {
            EMCKnowledgeGridCache cache = grid.getCache(EMCKnowledgeGridCache.class);
            if (cache != null) cache.invalidatePatterns();
            grid.postEvent(new MENetworkCellArrayUpdate());
        }
    }

    public static void clear() {
        EMCRecoveryLedger.clear();
        TILES.clear();
        DIRTY.clear();
        BALANCES.clear();
        if (itemMEEMCCell != null) itemMEEMCCell.clearCaches();
    }
}
