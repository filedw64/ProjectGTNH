package moze_intel.projecte.integration.ae2;

import moze_intel.projecte.math.ExactEMC;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@cpw.mods.fml.common.Optional.InterfaceList({
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.networking.IGridHost", modid = "appliedenergistics2"),
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.networking.IGridBlock", modid = "appliedenergistics2"),
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.storage.ICellContainer", modid = "appliedenergistics2"),
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.networking.security.IActionHost", modid = "appliedenergistics2"),
    @cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.networking.crafting.ICraftingProvider", modid = "appliedenergistics2")
})
public class TileMEEMCLink extends TileEntity implements IGridHost, IGridBlock, ICellContainer, IActionHost, IInventory, appeng.api.networking.crafting.ICraftingProvider {

	private final EMCInventoryHandler inventoryHandler;
	private IGridNode gridNode;

	private UUID ownerUUID;
	private String ownerName = "";
	private int accessMode = 0;
	private int priority = 0;
	private int filterMode = 0;
	private int filterPrecision = 0;
	private final ItemStack[] filterSlots = new ItemStack[16];

	private ExactEMC lastTrackedEmc;
	private int lastTrackedKnowledge = -1;
	private boolean isNotifying = false;

	public TileMEEMCLink() {
		this.inventoryHandler = new EMCInventoryHandler(this);
	}

    public void notifyGrid() {
        if (worldObj != null && !worldObj.isRemote) {
            AE2Integration.notifyHandlersForPlayer(ownerUUID);
            EMCKnowledgeGridCache cache = emcCache();
            if (cache != null) cache.invalidatePatterns();
        }
    }

    public void collectGrid(java.util.Set<IGrid> grids) {
        if (!isInvalid() && gridNode != null && gridNode.getGrid() != null) grids.add(gridNode.getGrid());
    }

    private NBTTagCompound pendingNodeData = new NBTTagCompound();

	public void setOwner(EntityPlayer player) {
		if (player != null && worldObj != null && !worldObj.isRemote
                && (ownerUUID == null || ownerUUID.equals(player.getUniqueID()))) {
			this.ownerUUID = player.getUniqueID();
			this.ownerName = player.getCommandSenderName();
			this.inventoryHandler.setOwner(ownerUUID, ownerName);
            IGridNode node = getGridNode(ForgeDirection.UNKNOWN);
            if (node != null) node.setPlayerID(AEApi.instance().registries().players().getID(player));
			AE2Integration.registerTile(this);
			markDirty();
			if (worldObj != null) {
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				if (!worldObj.isRemote) {
					worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "random.orb", 0.8F, 1.2F);
					player.addChatMessage(new net.minecraft.util.ChatComponentText(
						net.minecraft.util.EnumChatFormatting.GREEN + "[ProjectE] " + net.minecraft.util.EnumChatFormatting.GRAY + "ME EMC Link claimed by " + net.minecraft.util.EnumChatFormatting.YELLOW + player.getCommandSenderName()
					));
				}
			}
			notifyGrid();
		}
	}

	public UUID getOwnerUUID() { return ownerUUID; }
	public String getOwnerName() { return ownerName != null ? ownerName : ""; }
	public int getAccessMode() { return accessMode; }
	public int getPriority() { return priority; }
	public int getFilterMode() { return filterMode; }
	public int getFilterPrecision() { return filterPrecision; }
	public ItemStack[] getFilterSlots() { return filterSlots; }

	public void setAccessMode(int mode) {
		mode = Math.max(0, Math.min(2, mode));
		this.accessMode = mode;
		if (mode == 0) this.inventoryHandler.setAccess(AccessRestriction.READ_WRITE);
		else if (mode == 1) this.inventoryHandler.setAccess(AccessRestriction.READ);
		else this.inventoryHandler.setAccess(AccessRestriction.WRITE);
		markDirty();
		notifyGrid();
	}
	public void setPriority(int priority) { this.priority = priority; this.inventoryHandler.setPriority(priority); markDirty(); notifyGrid(); }
	public void setFilterMode(int mode) { this.filterMode = Math.max(0, Math.min(2, mode)); markDirty(); notifyGrid(); }
	public void setFilterPrecision(int precision) { this.filterPrecision = Math.max(0, Math.min(2, precision)); markDirty(); notifyGrid(); }

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote && !isInvalid()) {
            getGridNode(ForgeDirection.UNKNOWN);
            AE2Integration.registerTile(this);
            flushEMCOutputs();
        }
    }



    // AppliedE core refinement v2
    // AppliedE core hardening v3
    private static final long MAX_PENDING_EMC_OUTPUTS = 65536;
    private static final long EMC_OUTPUTS_PER_TICK = 4096;
    private static final int EMC_OUTPUT_ENTRIES_PER_TICK = 16;
    private final java.util.List<appeng.api.storage.data.IAEItemStack> emcOutputs = new java.util.ArrayList<>();
    private boolean releasingEMCOutputs;

    private int emcOutputCursor;
    // AppliedE persistent recovery v4
    private boolean recoveryAttached;
    private String recoveryIdentity = "";
    public void attachRecoveryLedger() {
        if (worldObj == null || worldObj.isRemote || ownerUUID == null)
            throw new IllegalStateException("Recovery queue requires a server-side owner");
        if (recoveryAttached) {
            NBTTagCompound current = EMCRecoveryLedger.get().attach(this, ownerUUID, new NBTTagList());
            if (!recoveryIdentity.equals(current.getString("Id")))
                throw new IllegalStateException("Recovery queue identity changed");
            return;
        }
        NBTTagCompound legacy = new NBTTagCompound(); saveEMCOutputs(legacy);
        NBTTagCompound q = EMCRecoveryLedger.get().attach(this, ownerUUID, legacy.getTagList("AppliedEOutputsV1", 10));
        recoveryIdentity = q.getString("Id"); recoveryAttached = true;
        reloadRecoveryLedger();
    }
    public void reloadRecoveryLedger() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setTag("AppliedEOutputsV1", EMCRecoveryLedger.get().queue(this));
        loadEMCOutputs(tag); markDirty();
    }
    private NBTTagList recoveryQueueTag() {
        NBTTagCompound tag = new NBTTagCompound(); saveEMCOutputs(tag); return tag.getTagList("AppliedEOutputsV1", 10);
    }
    private boolean recoveryHeld() {
        if (worldObj == null || worldObj.isRemote || ownerUUID == null) return true;
        attachRecoveryLedger(); return EMCRecoveryLedger.get().held(this);
    }
    private EMCKnowledgeGridCache emcCache() {
        return gridNode == null || gridNode.getGrid() == null ? null
            : gridNode.getGrid().getCache(EMCKnowledgeGridCache.class);
    }
    public boolean acceptsTransmutationItem(ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.getItem() != AE2Integration.itemEMCResource
            && stack.getItem() != AE2Integration.itemEMCTransmutationPattern
            && stack.getItem() != AE2Integration.itemEMCRecoveryBundle && inventoryHandler.filterMatches(stack);
    }
    @Override public void provideCrafting(appeng.api.networking.crafting.ICraftingProviderHelper helper) {
        EMCKnowledgeGridCache cache = emcCache();
        if (cache != null) cache.provide(this, helper);
    }
    private long pendingEMCOutputCount() {
        long total = 0;
        for (appeng.api.storage.data.IAEItemStack output : emcOutputs) {
            long count = output.getStackSize();
            if (count <= 0 || count > MAX_PENDING_EMC_OUTPUTS - total)
                throw new IllegalStateException("Invalid AppliedE output queue; refusing to discard stored outputs");
            total += count;
        }
        return total;
    }
    @Override public boolean isBusy() {
        return recoveryHeld() || releasingEMCOutputs || emcOutputs.size() >= 256 || pendingEMCOutputCount() >= MAX_PENDING_EMC_OUTPUTS
            || gridNode == null || !gridNode.isActive();
    }
    private void mergeEMCOutput(appeng.api.storage.data.IAEItemStack addition) {
        for (appeng.api.storage.data.IAEItemStack existing : emcOutputs) {
            if (existing.isSameType(addition) && addition.getStackSize() <= Long.MAX_VALUE - existing.getStackSize()) {
                existing.setStackSize(existing.getStackSize() + addition.getStackSize());
                return;
            }
        }
        emcOutputs.add(addition.copy());
    }
    @Override public boolean pushPattern(appeng.api.networking.crafting.ICraftingPatternDetails details,
            net.minecraft.inventory.InventoryCrafting table) {
        if (worldObj == null || worldObj.isRemote || isInvalid() || isBusy()
                || !(details instanceof EMCTransmutationPattern)) return false;
        EMCTransmutationPattern pattern = (EMCTransmutationPattern) details;
        EMCKnowledgeGridCache cache = emcCache();
        if (!pattern.matchesTable(table) || cache == null || !cache.authorizes(this, pattern)) return false;
        appeng.api.storage.data.IAEItemStack[] outputs = pattern.getCondensedOutputs();
        long remaining = MAX_PENDING_EMC_OUTPUTS - pendingEMCOutputCount();
        for (appeng.api.storage.data.IAEItemStack output : outputs) {
            if (output == null || output.getStackSize() <= 0 || output.getStackSize() > remaining) return false;
            remaining -= output.getStackSize();
        }
        // CPU has already acquired the inputs. Do not withdraw from player EMC here.
        for (appeng.api.storage.data.IAEItemStack output : outputs) mergeEMCOutput(output);
        EMCRecoveryLedger.get().storeQueue(this, recoveryQueueTag());
        markDirty();
        // Never inject synchronously: CPU registers waitingFor only after this returns.
        return true;
    }
    private void flushEMCOutputs() {
        if (recoveryHeld() || releasingEMCOutputs || emcOutputs.isEmpty() || gridNode == null || !gridNode.isActive()
                || gridNode.getGrid() == null) return;
        appeng.api.networking.storage.IStorageGrid storageGrid = gridNode.getGrid()
            .getCache(appeng.api.networking.storage.IStorageGrid.class);
        appeng.api.storage.IMEInventory<appeng.api.storage.data.IAEItemStack> storage = storageGrid.getItemInventory();
        appeng.api.networking.security.MachineSource source = new appeng.api.networking.security.MachineSource(this);
        long budget = EMC_OUTPUTS_PER_TICK;
        int attempts = Math.min(EMC_OUTPUT_ENTRIES_PER_TICK, emcOutputs.size());
        // Round-robin prevents a permanently blocked output from starving later types.
        while (attempts-- > 0 && budget > 0 && !emcOutputs.isEmpty()) {
            if (emcOutputCursor >= emcOutputs.size()) emcOutputCursor = 0;
            appeng.api.storage.data.IAEItemStack output = emcOutputs.get(emcOutputCursor);
            appeng.api.storage.data.IAEItemStack offered = output.copy();
            long offeredCount = Math.min(budget, output.getStackSize());
            offered.setStackSize(offeredCount);
            budget -= offeredCount;
            NBTTagCompound offerAudit = new NBTTagCompound();
            ItemStack offerItem = offered.getItemStack(); offerItem.stackSize = 1; offerItem.writeToNBT(offerAudit);
            offerAudit.setLong("EMCOutputCount", offeredCount);
            EMCRecoveryLedger.get().beginDelivery(this, offerAudit);
            appeng.api.storage.data.IAEItemStack remainder = storage.injectItems(offered,
                appeng.api.config.Actionable.MODULATE, source);
            long returned = remainder == null ? 0 : remainder.getStackSize();
            if (returned < 0 || returned > offeredCount || remainder != null && !output.isSameType(remainder))
                throw new IllegalStateException("Invalid AE inventory remainder for AppliedE output");
            long accepted = offeredCount - returned;
            if (accepted > 0) {
                output.setStackSize(output.getStackSize() - accepted);
                if (output.getStackSize() == 0) emcOutputs.remove(emcOutputCursor);
                else emcOutputCursor++;
                markDirty();
            } else emcOutputCursor++;
            EMCRecoveryLedger.get().endDelivery(this, recoveryQueueTag(), accepted);
        }
    }
    private void saveEMCOutputs(NBTTagCompound tag) {
        pendingEMCOutputCount();
        NBTTagList list = new NBTTagList();
        for (appeng.api.storage.data.IAEItemStack output : emcOutputs) {
            NBTTagCompound entry = new NBTTagCompound();
            ItemStack template = output.getItemStack(); template.stackSize = 1;
            template.writeToNBT(entry);
            entry.setLong("EMCOutputCount", output.getStackSize());
            list.appendTag(entry);
        }
        tag.setTag("AppliedEOutputsV1", list);
    }
    private void loadEMCOutputs(NBTTagCompound tag) {
        java.util.List<appeng.api.storage.data.IAEItemStack> restored = new java.util.ArrayList<>();
        NBTTagList list = tag.getTagList("AppliedEOutputsV1", Constants.NBT.TAG_COMPOUND);
        long total = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            long count = entry.getLong("EMCOutputCount");
            // Original v1 may have a truncated/zero Count byte. The separate long is authoritative.
            NBTTagCompound itemTag = (NBTTagCompound) entry.copy(); itemTag.setByte("Count", (byte) 1);
            ItemStack stack = ItemStack.loadItemStackFromNBT(itemTag);
            if (stack == null || count <= 0 || count > MAX_PENDING_EMC_OUTPUTS - total)
                throw new IllegalStateException("Invalid AppliedE saved output; repair the backup instead of silently discarding it");
            appeng.api.storage.data.IAEItemStack output = AEApi.instance().storage().createItemStack(stack);
            if (output == null) throw new IllegalStateException("Unregistered AppliedE output item");
            output.setStackSize(count); restored.add(output); total += count;
        }
        // Do not replace the live queue until the entire saved queue has passed validation.
        emcOutputs.clear(); emcOutputCursor = 0;
        for (appeng.api.storage.data.IAEItemStack output : restored) mergeEMCOutput(output);
    }


    /** Receipt payload is authoritative on the server, never in the item's NBT. */
    public boolean restoreEMCRecoveryBundle(ItemStack bundle, EntityPlayer player) {
        if (worldObj == null || worldObj.isRemote || releasingEMCOutputs || !isUseableByPlayer(player)
                || bundle == null || bundle.getItem() != AE2Integration.itemEMCRecoveryBundle) return false;
        attachRecoveryLedger();
        if (!EMCRecoveryLedger.get().redeem(this, ownerUUID, bundle)) return false;
        reloadRecoveryLedger(); return true;
    }
    /** Queue moves to the receipt atomically; rejected/destroyed token entities do not own the payload. */
    public void releaseEMCOutputsOnBreak() {
        if (worldObj == null || worldObj.isRemote || releasingEMCOutputs || ownerUUID == null) return;
        attachRecoveryLedger();
        if (emcOutputs.isEmpty()) return;
        // Held queues stay in ledger at their location, including if an unusual removal destroys the tile.
        if (EMCRecoveryLedger.get().held(this)) {
            moze_intel.projecte.utils.PELogger.logWarn("Removed EMC link has a quarantined queue; recreate link at same location and review ledger");
            return;
        }
        releasingEMCOutputs = true;
        try {
            ItemStack bundle = EMCRecoveryLedger.get().export(this, ownerUUID);
            reloadRecoveryLedger();
            net.minecraft.entity.item.EntityItem entity = new net.minecraft.entity.item.EntityItem(worldObj,
                xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, bundle);
            entity.delayBeforeCanPickup = 10;
            if (!worldObj.spawnEntityInWorld(entity))
                moze_intel.projecte.utils.PELogger.logWarn("Receipt token spawn rejected; owner may use /emcrecovery list and token");
        } finally { releasingEMCOutputs = false; }
    }
	// --- IGridHost & IGridBlock ---
	@Override public IGridNode getGridNode(ForgeDirection dir) {
		if (gridNode == null && worldObj != null && !worldObj.isRemote && !isInvalid()) {
			gridNode = AEApi.instance().createGridNode(this);
			gridNode.loadFromNBT("AE2Node", pendingNodeData);
            pendingNodeData = new NBTTagCompound();
			gridNode.updateState();
			AE2Integration.registerTile(this);
		}
		return gridNode;
	}
	@Override public AECableType getCableConnectionType(ForgeDirection dir) { return AECableType.SMART; }
	@Override public void securityBreak() { if (worldObj != null && !worldObj.isRemote) worldObj.func_147480_a(xCoord, yCoord, zCoord, true); }
	@Override public double getIdlePowerUsage() { return 5.0; }
	@Override public EnumSet<GridFlags> getFlags() { return EnumSet.of(GridFlags.REQUIRE_CHANNEL); }
	@Override public boolean isWorldAccessible() { return true; }
	@Override public DimensionalCoord getLocation() { return new DimensionalCoord(this); }
	@Override public AEColor getGridColor() { return AEColor.Transparent; }
	@Override public void onGridNotification(GridNotification notification) {}
	@Override public void setNetworkStatus(IGrid grid, int channelsInUse) {}
	@Override public EnumSet<ForgeDirection> getConnectableSides() { return EnumSet.of(ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST); }
	@Override public IGridHost getMachine() { return this; }
	@Override public void gridChanged() {}
	@Override public ItemStack getMachineRepresentation() { return new ItemStack(AE2Integration.blockMEEMCLink); }
	@Override public IGridNode getActionableNode() { return getGridNode(ForgeDirection.UNKNOWN); }

	// --- ICellContainer ---
	@Override public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
		if (channel == StorageChannel.ITEMS) {
			inventoryHandler.setOwner(ownerUUID, ownerName);
			inventoryHandler.setPriority(priority);
            EMCKnowledgeGridCache cache = emcCache();
            EMCResourceInventoryHandler storage = cache == null ? null : cache.storageFor(this);
            if (storage != null) return Collections.<IMEInventoryHandler>singletonList(storage);
		}
		return Collections.emptyList();
	}
	@Override public void blinkCell(int slot) {}
	@Override public void saveChanges(IMEInventory cellInventory) { markDirty(); }

	@Override public void invalidate() {
		super.invalidate();
		AE2Integration.unregisterTile(this);
		if (gridNode != null) { gridNode.destroy(); gridNode = null; }
	}
	@Override public void onChunkUnload() {
		super.onChunkUnload();
		AE2Integration.unregisterTile(this);
		if (gridNode != null) { gridNode.destroy(); gridNode = null; }
	}

	// --- IInventory ---
	@Override public int getSizeInventory() { return filterSlots.length; }
	@Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < filterSlots.length ? filterSlots[slot] : null; }
    @Override public ItemStack decrStackSize(int slot, int amount) { return null; }
	@Override public ItemStack getStackInSlotOnClosing(int slot) { return null; }
	@Override public void setInventorySlotContents(int slot, ItemStack stack) {
		if (slot >= 0 && slot < filterSlots.length) {
			if (stack != null) {
				ItemStack copy = stack.copy(); copy.stackSize = 1; filterSlots[slot] = copy;
			} else filterSlots[slot] = null;
			markDirty(); notifyGrid();
		}
	}
	@Override public String getInventoryName() { return "container.pe.me_emc_link"; }
	@Override public boolean hasCustomInventoryName() { return false; }
	@Override public int getInventoryStackLimit() { return 1; }
    @Override public boolean isUseableByPlayer(EntityPlayer player) {
        return player != null && ownerUUID != null && ownerUUID.equals(player.getUniqueID())
            && worldObj != null && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }
	@Override public void openInventory() {}
	@Override public void closeInventory() {}
	@Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return false; }

	@Override public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
        saveEMCOutputs(tag);
        tag.setString("AppliedEQueueIdentityV4", recoveryIdentity);
        if (gridNode != null) gridNode.saveToNBT("AE2Node", tag);
        else if (pendingNodeData.hasKey("AE2Node")) tag.setTag("AE2Node", pendingNodeData.getCompoundTag("AE2Node").copy());
		if (ownerUUID != null) tag.setString("OwnerUUID", ownerUUID.toString());
		if (ownerName != null) tag.setString("OwnerName", ownerName);
		tag.setInteger("AccessMode", accessMode);
		tag.setInteger("Priority", priority);
		tag.setInteger("FilterMode", filterMode);
		tag.setInteger("FilterPrecision", filterPrecision);

		NBTTagList items = new NBTTagList();
		for (int i = 0; i < filterSlots.length; i++) {
			if (filterSlots[i] != null) {
				NBTTagCompound itemTag = new NBTTagCompound();
				itemTag.setByte("Slot", (byte) i);
				filterSlots[i].writeToNBT(itemTag);
				items.appendTag(itemTag);
			}
		}
		tag.setTag("FilterSlots", items);
	}

	@Override public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
        recoveryAttached = false;
        recoveryIdentity = tag.getString("AppliedEQueueIdentityV4");
        loadEMCOutputs(tag);
        if (gridNode == null) {
            pendingNodeData = new NBTTagCompound();
            if (tag.hasKey("AE2Node")) pendingNodeData.setTag("AE2Node", tag.getCompoundTag("AE2Node").copy());
        }
        ownerUUID = null;
        ownerName = "";
		if (tag.hasKey("OwnerUUID")) { try { this.ownerUUID = UUID.fromString(tag.getString("OwnerUUID")); } catch (Exception ignored) {} }
		if (tag.hasKey("OwnerName")) this.ownerName = tag.getString("OwnerName");
		this.accessMode = Math.max(0, Math.min(2, tag.getInteger("AccessMode")));
		this.priority = tag.getInteger("Priority");
		this.filterMode = Math.max(0, Math.min(2, tag.getInteger("FilterMode")));
		this.filterPrecision = Math.max(0, Math.min(2, tag.getInteger("FilterPrecision")));

		Arrays.fill(filterSlots, null);
		if (tag.hasKey("FilterSlots", Constants.NBT.TAG_LIST)) {
			NBTTagList items = tag.getTagList("FilterSlots", Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < items.tagCount(); i++) {
				NBTTagCompound itemTag = items.getCompoundTagAt(i);
				int slot = itemTag.getByte("Slot") & 255;
				if (slot < filterSlots.length) filterSlots[slot] = ItemStack.loadItemStackFromNBT(itemTag);
			}
		}
		this.inventoryHandler.setOwner(ownerUUID, ownerName);
		this.inventoryHandler.setPriority(priority);
		this.inventoryHandler.setAccess(accessMode == 0 ? AccessRestriction.READ_WRITE : accessMode == 1 ? AccessRestriction.READ : AccessRestriction.WRITE);
	}

	@Override public net.minecraft.network.Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound(); writeToNBT(tag);
		return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
	}
	@Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
		if (pkt != null && pkt.func_148857_g() != null) readFromNBT(pkt.func_148857_g());
	}
}
