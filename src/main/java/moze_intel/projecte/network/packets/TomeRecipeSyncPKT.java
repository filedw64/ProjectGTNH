package moze_intel.projecte.network.packets;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import moze_intel.projecte.gameObjs.customRecipes.RecipeTome;

import java.util.ArrayList;
import java.util.List;

/** Shares the server's startup selection with client crafting and tooltips. */
public class TomeRecipeSyncPKT implements IMessage, IMessageHandler<TomeRecipeSyncPKT, IMessage>
{
	private final List<RecipeTome.Ingredient> ingredients = new ArrayList<>();

	public TomeRecipeSyncPKT() {}

	public static TomeRecipeSyncPKT create()
	{
		TomeRecipeSyncPKT packet = new TomeRecipeSyncPKT();
		packet.ingredients.addAll(RecipeTome.getSelection());
		return packet;
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		int count = buf.readUnsignedByte();
		if (count != RecipeTome.SIZE) throw new IllegalArgumentException("Invalid Tome recipe size");
		for (int i = 0; i < count; i++) {
			String key = ByteBufUtils.readUTF8String(buf);
			String mod = ByteBufUtils.readUTF8String(buf);
			ingredients.add(new RecipeTome.Ingredient(key, mod, ByteBufUtils.readItemStack(buf)));
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeByte(ingredients.size());
		for (RecipeTome.Ingredient ingredient : ingredients) {
			ByteBufUtils.writeUTF8String(buf, ingredient.key);
			ByteBufUtils.writeUTF8String(buf, ingredient.mod);
			ByteBufUtils.writeItemStack(buf, ingredient.seed);
		}
	}

	@Override
	public IMessage onMessage(TomeRecipeSyncPKT message, MessageContext context)
	{
		RecipeTome.receiveSelection(message.ingredients);
		return null;
	}
}
