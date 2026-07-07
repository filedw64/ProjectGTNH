package projectgtnh.network.packets;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import projectgtnh.api.item.IExtraFunction;
import projectgtnh.api.item.IItemCharge;
import projectgtnh.api.item.IModeChanger;
import projectgtnh.api.item.IProjectileShooter;
import projectgtnh.config.ProjectGTNHConfig;
import projectgtnh.gameObjs.ObjHandler;
import projectgtnh.gameObjs.items.armor.GemArmorBase;
import projectgtnh.gameObjs.items.armor.GemChest;
import projectgtnh.gameObjs.items.armor.GemFeet;
import projectgtnh.gameObjs.items.armor.GemHelmet;
import projectgtnh.handlers.PlayerChecks;
import projectgtnh.utils.PEKeybind;
import projectgtnh.utils.PlayerHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

public class KeyPressPKT implements IMessage
{
	private PEKeybind key;

	public KeyPressPKT() {}

	public KeyPressPKT(PEKeybind key)
	{
		this.key = key;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		key = PEKeybind.values()[buf.readInt()];
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeInt(key.ordinal());
	}

	public static class Handler implements IMessageHandler<KeyPressPKT, IMessage>
	{
		@Override
		public IMessage onMessage(final KeyPressPKT message, final MessageContext ctx)
		{
			EntityPlayerMP player = ctx.getServerHandler().playerEntity;
			ItemStack stack = player.getHeldItem();

			switch (message.key)
			{
				case ARMOR_TOGGLE:
					if (player.isSneaking())
					{
						ItemStack helm = player.inventory.armorItemInSlot(3);

						if (helm != null && helm.getItem() == ObjHandler.gemHelmet)
						{
							GemHelmet.toggleNightVision(helm, player);
						}
					}
					else
					{
						ItemStack boots = player.inventory.armorItemInSlot(0);

						if (boots != null && boots.getItem() == ObjHandler.gemFeet)
						{
							((GemFeet) ObjHandler.gemFeet).toggleStepAssist(boots, player);
						}
					}
					break;
				case CHARGE:
					if (stack != null && stack.getItem() instanceof IItemCharge)
					{
						((IItemCharge) stack.getItem()).changeCharge(player, stack);
					}
					else if (stack == null || ProjectGTNHConfig.unsafeKeyBinds)
					{
						if (GemArmorBase.hasAnyPiece(player))
						{
							PlayerChecks.setGemState(player, !PlayerChecks.getGemState(player));
							player.addChatMessage(new ChatComponentTranslation(PlayerChecks.getGemState(player) ? "pe.gem.activate" : "pe.gem.deactivate"));
						}
					}
					break;
				case EXTRA_FUNCTION:
					if (stack != null && stack.getItem() instanceof IExtraFunction)
					{
						((IExtraFunction) stack.getItem()).doExtraFunction(stack, player);
					} else if (stack == null || ProjectGTNHConfig.unsafeKeyBinds)
					{
						if (PlayerChecks.getGemState(player) && player.inventory.armorInventory[2] != null && player.inventory.armorInventory[2].getItem() == ObjHandler.gemChest)
						{
							if (PlayerChecks.getGemCooldown(player) <= 0)
							{
								((GemChest) ObjHandler.gemChest).doExplode(player);
								PlayerChecks.resetGemCooldown(player);
							}
						}
					}
					break;
				case FIRE_PROJECTILE:
					if (stack != null && stack.getItem() instanceof IProjectileShooter)
					{
						if (PlayerChecks.getProjectileCooldown(player) <= 0) {
							if (((IProjectileShooter) stack.getItem()).shootProjectile(player, stack))
							{
								PlayerHelper.swingItem((player));
							}
							PlayerChecks.resetProjectileCooldown(player);
						}
					} else if (stack == null || ProjectGTNHConfig.unsafeKeyBinds)
					{
						if (PlayerChecks.getGemState(player) && player.inventory.armorInventory[3] != null && player.inventory.armorInventory[3].getItem() == ObjHandler.gemHelmet)
						{
							((GemHelmet) ObjHandler.gemHelmet).doZap(player);
						}
					}
					break;
				case MODE:
					if (stack != null && stack.getItem() instanceof IModeChanger)
					{
						((IModeChanger) stack.getItem()).changeMode(player, stack);
					}
					break;
			}
			return null;
		}
	}
}
