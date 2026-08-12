package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.container.AlchBagContainer;
import moze_intel.projecte.gameObjs.items.AlchemicalBag;
import moze_intel.projecte.gameObjs.items.tools.PEToolBase;
import moze_intel.projecte.handlers.PlayerChecks;
import moze_intel.projecte.playerData.AlchBagProps;
import moze_intel.projecte.playerData.AlchemicalBags;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.playerData.TransmutationOffline;
import moze_intel.projecte.playerData.TransmutationProps;
import moze_intel.projecte.utils.ChatHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public class PlayerEvents {
	// Handles playerData props from being wiped on death
	@SubscribeEvent
	public void cloneEvent(PlayerEvent.Clone evt) {
		if (!evt.wasDeath) return; // Vanilla handles it for us.

		NBTTagCompound bag = new NBTTagCompound();
		NBTTagCompound transmute = new NBTTagCompound();

		AlchBagProps.getDataFor(evt.original).saveNBTData(bag); // Cache old
		TransmutationProps.getDataFor(evt.original).saveNBTData(transmute);

		AlchBagProps.getDataFor(evt.entityPlayer).loadNBTData(bag); // Reapply on new
		TransmutationProps.getDataFor(evt.entityPlayer).loadNBTData(transmute);

		PELogger.logDebug("Reapplied bag and knowledge on player respawning");
	}

	@SubscribeEvent
	public void onEntityJoinWorld(EntityJoinWorldEvent event) {
		Entity ent = event.entity;
		if (!ent.worldObj.isRemote && ent instanceof EntityPlayerMP player) {
			Transmutation.sync(player);
			AlchemicalBags.syncFull(player);
		}
	}

	@SubscribeEvent
	public void onConstruct(EntityEvent.EntityConstructing event) {
		Entity ent = event.entity;
		if (ent instanceof EntityPlayer player && !(ent instanceof FakePlayer)) {
			TransmutationOffline.clear(ent.getUniqueID());
			PELogger.logDebug("Clearing offline data cache in preparation to load online data");

			TransmutationProps.register(player);
			AlchBagProps.register(player);
		}
	}

	@SubscribeEvent
	public void onHighAlchemistJoin(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent evt) {
		EntityPlayer player = evt.player;
		if (PECore.uuids.contains((player.getUniqueID().toString()))) {
			IChatComponent prior = ChatHelper.modifyColor(new ChatComponentTranslation("pe.server.high_alchemist"), EnumChatFormatting.BLUE);
			IChatComponent playername = ChatHelper.modifyColor(new ChatComponentText(" " + player.getCommandSenderName() + " "), EnumChatFormatting.GOLD);
			IChatComponent latter = ChatHelper.modifyColor(new ChatComponentTranslation("pe.server.has_joined"), EnumChatFormatting.BLUE);
			MinecraftServer.getServer().getConfigurationManager().sendChatMsg(prior.appendSibling(playername).appendSibling(latter)); // Sends to all everywhere, not just same world like before.
		}
	}

	@SubscribeEvent
	public void playerChangeDimension(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
		PlayerChecks.onPlayerChangeDimension((EntityPlayerMP) event.player);
	}

	@SubscribeEvent
	public void pickupItem(EntityItemPickupEvent event) {
		EntityPlayer player = event.entityPlayer;
		World world = player.worldObj;

		if (world.isRemote)
			return;

		ItemStack picked = event.item.getEntityItem();

		if (player.openContainer instanceof AlchBagContainer bag) {
			IInventory inv = bag.inventory;

			if (ItemHelper.invContainsItem(inv, new ItemStack(ObjHandler.blackHole, 1, 1))
				|| ItemHelper.invContainsItem(inv, new ItemStack(ObjHandler.voidRing, 1, 1))
				&& ItemHelper.hasSpaceForSingle(inv, picked))
			{
				ItemStack remain = ItemHelper.pushStackInInv(inv, picked);

				if (remain == null) {
					event.item.delayBeforeCanPickup = 10;
					event.item.setDead();
					world.playSoundAtEntity(player, "random.pop", 0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
				}
				else event.item.setEntityItemStack(remain);

				event.setCanceled(true);
			}
		}
		else {
			ItemStack bag = AlchemicalBag.getFirstBagWithSuctionItem(player, player.inventory.mainInventory);

			if (bag == null)
				return;

			ItemStack[] inv = AlchemicalBags.get(player, (byte) bag.getItemDamage());

			if (ItemHelper.hasSpaceForSingle(inv, picked)) {
				ItemStack remain = ItemHelper.pushStackInInv(inv, picked);

				if (remain == null) {
					event.item.delayBeforeCanPickup = 10;
					event.item.setDead();
					world.playSoundAtEntity(player, "random.pop", 0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
				}
				else event.item.setEntityItemStack(remain);

				AlchemicalBags.set(player, (byte) bag.getItemDamage(), inv);
				AlchemicalBags.syncPartial(player, bag.getItemDamage());

				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent
	public void onItemToss(ItemTossEvent event) {
		EntityPlayer player = event.player;
		ItemStack stack = event.entityItem.getEntityItem();

		if (stack == null || !(stack.getItem() instanceof PEToolBase))
			return;// 判断丢出的物品是否为我们需要保护的工具/武器

		// 如果没有打开额外的GUI，openContainer 就是玩家自身的 inventoryContainer
		if (player.openContainer == player.inventoryContainer) {

			// 取消抛出事件
			event.setCanceled(true);
			event.entityItem.setDead();

			// 将物品重新塞回玩家背包
			player.inventory.addItemStackToInventory(stack);

			// 在服务端强制同步玩家背包
			if (!player.worldObj.isRemote && player instanceof EntityPlayerMP) {
				player.inventoryContainer.detectAndSendChanges();
			}
		}
	}
}
