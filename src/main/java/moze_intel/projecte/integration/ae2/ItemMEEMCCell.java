package moze_intel.projecte.integration.ae2;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import moze_intel.projecte.gameObjs.ObjHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@cpw.mods.fml.common.Optional.InterfaceList({
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.storage.ICellHandler", modid = "appliedenergistics2"),
	@cpw.mods.fml.common.Optional.Interface(iface = "appeng.api.storage.ICellWorkbenchItem", modid = "appliedenergistics2")
})
public class ItemMEEMCCell extends Item implements ICellHandler, ICellWorkbenchItem {

	@SideOnly(Side.CLIENT)
	private IIcon iconTop;

	// AppliedE core hardening v3
    private final Map<ISaveProvider, Map<UUID, EMCInventoryHandler>> handlerCache = new java.util.WeakHashMap<>();
    public void refreshHostedSnapshots() {
        for (Map<UUID, EMCInventoryHandler> handlers : new java.util.ArrayList<>(handlerCache.values()))
            for (EMCInventoryHandler handler : new java.util.ArrayList<>(handlers.values())) handler.refreshLegacySnapshot();
    }
	private final Map<UUID, Set<ISaveProvider>> providerCache = new HashMap<>();

	public ItemMEEMCCell() {
		setCreativeTab(ObjHandler.tab);
		setUnlocalizedName("pe_me_emc_cell");
		setMaxStackSize(1);
	}

    public java.util.Set<UUID> getTrackedOwners() { return new java.util.HashSet<>(providerCache.keySet()); }

    public void collectGrids(UUID uuid, java.util.Set<appeng.api.networking.IGrid> grids) {
        Set<ISaveProvider> providers = providerCache.get(uuid);
        if (providers == null) return;
        for (ISaveProvider provider : new java.util.ArrayList<>(providers)) {
            if (provider instanceof net.minecraft.tileentity.TileEntity
                    && ((net.minecraft.tileentity.TileEntity) provider).isInvalid()) {
                providers.remove(provider);
                continue;
            }
            if (provider instanceof appeng.api.networking.IGridHost) {
                appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) provider)
                    .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
                if (node != null && node.getGrid() != null) grids.add(node.getGrid());
            }
        }
        if (providers.isEmpty()) {
            providerCache.remove(uuid);
            // Weak host keys release removed drive/chest handlers.
        }
    }

    public void clearCaches() { providerCache.clear(); handlerCache.clear(); }

    public static UUID getOwner(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        try { return UUID.fromString(stack.getTagCompound().getString("OwnerUUID")); }
        catch (IllegalArgumentException ex) { return null; }
    }

	@Override
	public void onCreated(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
		super.onCreated(stack, world, player);
		if (player != null && !world.isRemote && getOwner(stack) == null) {
			setOwner(stack, player.getUniqueID(), player.getCommandSenderName());
		}
	}

	public static void setOwner(ItemStack stack, UUID uuid, String name) {
		if (stack != null) {
			if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
			if (uuid != null) stack.getTagCompound().setString("OwnerUUID", uuid.toString());
			if (name != null) stack.getTagCompound().setString("OwnerName", name);
		}
	}

    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean advanced) {
        if (getOwner(stack) != null) list.add(net.minecraft.util.StatCollector.translateToLocalFormatted(
            "pe.ae2.owner", stack.getTagCompound().getString("OwnerName")));
        else list.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.unbound"));
        list.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.exact"));
        list.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.online_only"));
        list.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.shared_budget"));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
        if (!world.isRemote && player != null && player.isSneaking()) {
            UUID owner = getOwner(stack);
            if (owner != null && !owner.equals(player.getUniqueID())) {
                player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("pe.ae2.owner_only"));
                return stack;
            }
            setOwner(stack, player.getUniqueID(), player.getCommandSenderName());
            player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("pe.ae2.bound"));
        }
        return stack;
    }

	@Override
	public boolean isCell(ItemStack stack) {
		return stack != null && stack.getItem() == this;
	}

	@Override
	public IMEInventoryHandler getCellInventory(ItemStack stack, ISaveProvider saveProvider, StorageChannel channel) {
		if (channel == StorageChannel.ITEMS && isCell(stack)) {
			UUID ownerUUID = null;
			String ownerName = null;
			if (stack.hasTagCompound()) {
				try {
					if (stack.getTagCompound().hasKey("OwnerUUID")) ownerUUID = UUID.fromString(stack.getTagCompound().getString("OwnerUUID"));
					if (stack.getTagCompound().hasKey("OwnerName")) ownerName = stack.getTagCompound().getString("OwnerName");
				} catch (Exception ignored) {}
			}
			if (ownerUUID == null) return null;
            // A null-host inspection must not retain a global handler.
            if (saveProvider == null) {
                EMCInventoryHandler handler = new EMCInventoryHandler();
                handler.setOwner(ownerUUID, ownerName);
                return handler;
            }

			if (saveProvider != null) {
				Set<ISaveProvider> providers = providerCache.computeIfAbsent(ownerUUID, k -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
				providers.add(saveProvider);
			}

            Map<UUID, EMCInventoryHandler> hosted = handlerCache.computeIfAbsent(saveProvider, k -> new HashMap<>());
            EMCInventoryHandler handler = hosted.computeIfAbsent(ownerUUID, k -> new EMCInventoryHandler());
            handler.setStorageHost(saveProvider);
			handler.setOwner(ownerUUID, ownerName);
			return handler;
		}
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getTopTexture_Light() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getTopTexture_Medium() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getTopTexture_Dark() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler, IMEInventoryHandler inv, ItemStack stack, StorageChannel channel) {}
	@Override
	public int getStatusForCell(ItemStack stack, IMEInventory inv) { return getOwner(stack) == null ? 4 : 1; }
	@Override
	public double cellIdleDrain(ItemStack stack, IMEInventory inv) { return 0.5; }
	@Override
	public boolean isEditable(ItemStack stack) { return false; }
	@Override
	public IInventory getUpgradesInventory(ItemStack stack) { return null; }
	@Override
	public IInventory getConfigInventory(ItemStack stack) { return null; }
	@Override
	public FuzzyMode getFuzzyMode(ItemStack stack) { return FuzzyMode.IGNORE_ALL; }
	@Override
	public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		itemIcon = register.registerIcon("projecte:transmute_tablet");
		iconTop = register.registerIcon("projecte:transmute_tablet");
	}
}
