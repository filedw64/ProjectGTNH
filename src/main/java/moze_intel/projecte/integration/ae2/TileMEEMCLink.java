package moze_intel.projecte.integration.ae2;

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
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.networking.security.IActionHost", modid = "appliedenergistics2")
})
public class TileMEEMCLink extends TileEntity implements IGridHost, IGridBlock, ICellContainer, IActionHost, IInventory {

	private final EMCInventoryHandler inventoryHandler;
	private IGridNode gridNode;

	private UUID ownerUUID;
	private String ownerName = "";
	private int accessMode = 0;
	private int priority = 0;
	private int filterMode = 0;
	private int filterPrecision = 0;
	private final ItemStack[] filterSlots = new ItemStack[16];

	private double lastTrackedEmc = -1.0;
	private int lastTrackedKnowledge = -1;
	private boolean isNotifying = false;

	public TileMEEMCLink() {
		this.inventoryHandler = new EMCInventoryHandler(this);
	}

	public void notifyGrid() {
		if (isNotifying) return;
		isNotifying = true;
		try {
			IGridNode node = getGridNode(ForgeDirection.UNKNOWN);
			if (node != null && node.getGrid() != null) {
				node.getGrid().postEvent(new MENetworkCellArrayUpdate());
				try {
					appeng.api.networking.storage.IStorageGrid storageGrid = node.getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
					if (storageGrid != null) {
						appeng.api.storage.data.IItemList<appeng.api.storage.data.IAEItemStack> current = AEApi.instance().storage().createItemList();
						inventoryHandler.getAvailableItems(current);
						if (current != null && !current.isEmpty()) {
							storageGrid.postAlterationOfStoredItems(StorageChannel.ITEMS, current, new appeng.api.networking.security.MachineSource(this));
						}
					}
				} catch (Throwable ignored) {}
			}
		} finally {
			isNotifying = false;
		}
	}

	public void setOwner(EntityPlayer player) {
		if (player != null) {
			this.ownerUUID = player.getUniqueID();
			this.ownerName = player.getCommandSenderName();
			this.inventoryHandler.setOwner(ownerUUID, ownerName);
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
		this.accessMode = mode;
		if (mode == 0) this.inventoryHandler.setAccess(AccessRestriction.READ_WRITE);
		else if (mode == 1) this.inventoryHandler.setAccess(AccessRestriction.READ);
		else this.inventoryHandler.setAccess(AccessRestriction.WRITE);
		markDirty();
		notifyGrid();
	}
	public void setPriority(int priority) { this.priority = priority; this.inventoryHandler.setPriority(priority); markDirty(); notifyGrid(); }
	public void setFilterMode(int mode) { this.filterMode = mode; markDirty(); notifyGrid(); }
	public void setFilterPrecision(int precision) { this.filterPrecision = precision; markDirty(); notifyGrid(); }

	@Override
	public void updateEntity() {
		super.updateEntity();
		if (worldObj != null && !worldObj.isRemote) {
			if (gridNode == null) {
				getGridNode(ForgeDirection.UNKNOWN);
				AE2Integration.registerTile(this);
			}
			// GTNH 优化：降低检测频率到 20 tick (1秒)，防止 AE2 持续卡服
			if (worldObj.getTotalWorldTime() % 20L == 0L) {
				EntityPlayer player = inventoryHandler.getPlayer();
				if (player != null) {
					double currentEmc = Transmutation.getEmc(player);
					List<ItemStack> knowledge = Transmutation.getKnowledge(player);
					int currentKnowledge = knowledge != null ? knowledge.size() : 0;

					// 仅当学会新物品，或 EMC 变动较大时才通知 AE2（防止刷石机导致每秒发包）
					if (Math.abs(currentEmc - lastTrackedEmc) > 1.0 || currentKnowledge != lastTrackedKnowledge) {
						lastTrackedEmc = currentEmc;
						lastTrackedKnowledge = currentKnowledge;
						notifyGrid();
					}
				}
			}
		}
	}

	// --- IGridHost & IGridBlock ---
	@Override public IGridNode getGridNode(ForgeDirection dir) {
		if (gridNode == null && worldObj != null && !worldObj.isRemote) {
			gridNode = AEApi.instance().createGridNode(this);
			gridNode.updateState();
			AE2Integration.registerTile(this);
		}
		return gridNode;
	}
	@Override public AECableType getCableConnectionType(ForgeDirection dir) { return AECableType.SMART; }
	@Override public void securityBreak() {}
	@Override public double getIdlePowerUsage() { return 5.0; }
	@Override public EnumSet<GridFlags> getFlags() { return EnumSet.of(GridFlags.REQUIRE_CHANNEL); }
	@Override public boolean isWorldAccessible() { return true; }
	@Override public DimensionalCoord getLocation() { return new DimensionalCoord(this); }
	@Override public AEColor getGridColor() { return AEColor.Transparent; }
	@Override public void onGridNotification(GridNotification notification) {}
	@Override public void setNetworkStatus(IGrid grid, int channelsInUse) {}
	@Override public EnumSet<ForgeDirection> getConnectableSides() { return EnumSet.allOf(ForgeDirection.class); }
	@Override public IGridHost getMachine() { return this; }
	@Override public void gridChanged() {}
	@Override public ItemStack getMachineRepresentation() { return new ItemStack(AE2Integration.blockMEEMCLink); }
	@Override public IGridNode getActionableNode() { return getGridNode(ForgeDirection.UNKNOWN); }

	// --- ICellContainer ---
	@Override public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
		if (channel == StorageChannel.ITEMS) {
			inventoryHandler.setOwner(ownerUUID, ownerName);
			inventoryHandler.setPriority(priority);
			return Collections.<IMEInventoryHandler>singletonList(inventoryHandler);
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
	@Override public ItemStack decrStackSize(int slot, int amount) {
		if (slot >= 0 && slot < filterSlots.length && filterSlots[slot] != null) {
			ItemStack stack = filterSlots[slot]; filterSlots[slot] = null;
			markDirty(); notifyGrid(); return stack;
		}
		return null;
	}
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
	@Override public boolean isUseableByPlayer(EntityPlayer player) { return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D; }
	@Override public void openInventory() {}
	@Override public void closeInventory() {}
	@Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return true; }

	@Override public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
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
		if (tag.hasKey("OwnerUUID")) { try { this.ownerUUID = UUID.fromString(tag.getString("OwnerUUID")); } catch (Exception ignored) {} }
		if (tag.hasKey("OwnerName")) this.ownerName = tag.getString("OwnerName");
		this.accessMode = tag.getInteger("AccessMode");
		this.priority = tag.getInteger("Priority");
		this.filterMode = tag.getInteger("FilterMode");
		this.filterPrecision = tag.getInteger("FilterPrecision");

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
		setAccessMode(accessMode);
		AE2Integration.registerTile(this);
	}

	@Override public net.minecraft.network.Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound(); writeToNBT(tag);
		return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
	}
	@Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
		if (pkt != null && pkt.func_148857_g() != null) readFromNBT(pkt.func_148857_g());
	}
}
