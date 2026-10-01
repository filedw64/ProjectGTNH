package moze_intel.projecte.integration.ae2;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.*;
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

	private final Map<UUID, EMCInventoryHandler> handlerCache = new HashMap<>();
	private final Map<UUID, Set<ISaveProvider>> providerCache = new HashMap<>();

	public ItemMEEMCCell() {
		setCreativeTab(ObjHandler.tab);
		setUnlocalizedName("pe_me_emc_cell");
		setMaxStackSize(1);
	}

	public void notifyGridForPlayer(UUID uuid) {
		if (uuid == null) return;
		Set<ISaveProvider> providers = providerCache.get(uuid);
		if (providers != null) {
			for (ISaveProvider sp : providers) {
				if (sp instanceof appeng.api.networking.IGridHost) {
					try {
						appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) sp).getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
						if (node != null && node.getGrid() != null) {
							node.getGrid().postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
							appeng.api.networking.storage.IStorageGrid storageGrid = (appeng.api.networking.storage.IStorageGrid) node.getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
							EMCInventoryHandler handler = handlerCache.get(uuid);
							if (storageGrid != null && handler != null) {
								appeng.api.storage.data.IItemList<appeng.api.storage.data.IAEItemStack> current = appeng.api.AEApi.instance().storage().createItemList();
								handler.getAvailableItems(current);
								if (current != null && !current.isEmpty()) {
									appeng.api.networking.security.BaseActionSource src = (sp instanceof appeng.api.networking.security.IActionHost)
										? new appeng.api.networking.security.MachineSource((appeng.api.networking.security.IActionHost) sp)
										: new appeng.api.networking.security.PlayerSource(handler.getPlayer(), null);
									storageGrid.postAlterationOfStoredItems(StorageChannel.ITEMS, current, src);
								}
							}
						}
					} catch (Throwable ignored) {}
				}
			}
		}
	}

	@Override
	public void onCreated(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
		super.onCreated(stack, world, player);
		if (player != null) {
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

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		if (stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey("OwnerName")) {
			list.add(EnumChatFormatting.AQUA + "Owner: " + EnumChatFormatting.WHITE + stack.getTagCompound().getString("OwnerName"));
		} else {
			list.add(EnumChatFormatting.GRAY + "Unbound (Shift+Right-click to claim)");
		}
		list.add(EnumChatFormatting.GREEN + "Capacity: " + EnumChatFormatting.GOLD + "Infinite 64-bit EMC");
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, net.minecraft.world.World world, EntityPlayer player) {
		if (player != null && player.isSneaking()) {
			setOwner(stack, player.getUniqueID(), player.getCommandSenderName());
			if (!world.isRemote) {
				world.playSoundAtEntity(player, "random.orb", 0.8F, 1.2F);
				player.addChatMessage(new ChatComponentText(
					EnumChatFormatting.GREEN + "[ProjectE] " + EnumChatFormatting.GRAY + "ME EMC Storage Cell claimed by " + EnumChatFormatting.YELLOW + player.getCommandSenderName()
				));
			}
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

			if (saveProvider != null) {
				Set<ISaveProvider> providers = providerCache.computeIfAbsent(ownerUUID, k -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
				providers.add(saveProvider);
			}

			EMCInventoryHandler handler = handlerCache.computeIfAbsent(ownerUUID, k -> new EMCInventoryHandler());
			handler.setOwner(ownerUUID, ownerName);
			return handler;
		}
		return null;
	}

	@Override
	public IIcon getTopTexture_Light() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	public IIcon getTopTexture_Medium() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	public IIcon getTopTexture_Dark() { return iconTop != null ? iconTop : itemIcon; }
	@Override
	public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler, IMEInventoryHandler inv, ItemStack stack, StorageChannel channel) {}
	@Override
	public int getStatusForCell(ItemStack stack, IMEInventory inv) { return 1; }
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
		itemIcon = register.registerIcon("projecte:me_emc_cell");
		iconTop = register.registerIcon("projecte:me_emc_cell_top");
	}
}
