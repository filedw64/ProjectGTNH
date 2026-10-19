package moze_intel.projecte.network.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.utils.MathUtils;

public class SetEmcCMD extends ProjectEBaseCMD
{
	@Override
	public String getCommandName()
	{
		return "projecte_setEMC";
	}

	@Override
	public String getCommandUsage(ICommandSender sender)
	{
		return "pe.command.set.usage";
	}

	@Override
	public int getRequiredPermissionLevel()
	{
		return 4;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] params)
	{
		if (params.length < 1)
		{
			sendError(sender, new ChatComponentTranslation("pe.command.set.usage"));
			return;
		}

		String name;
		int meta;
		double emc;

		if (params.length == 1)
		{
			ItemStack heldItem = getCommandSenderAsPlayer(sender).getHeldItem();

			if (heldItem == null)
			{
				sendError(sender, new ChatComponentTranslation("pe.command.set.usage"));
				return;
			}

			name = Item.itemRegistry.getNameForObject(heldItem.getItem());
			meta = heldItem.getItemDamage();
			emc = MathUtils.parseDouble(params[0]);

        }
		else
		{
			name = params[0];
			meta = 0;
			boolean isOD = !name.contains(":");

			if (!isOD && params.length > 2)
			{
                meta = MathUtils.parseInteger(params[1]);

                if (meta < 0)
                {
                    sendError(sender, new ChatComponentTranslation("pe.command.set.invalidmeta", params[1]));
                    return;
                }

                emc = MathUtils.parseDouble(params[2]);
            }
			else
			{
				emc = MathUtils.parseDouble(params[1]);
            }
        }
        if (!Double.isFinite(emc) || emc < 0 || emc > 1e300)
        {
            sendError(sender, new ChatComponentTranslation("pe.command.set.invalidemc", params[0]));
            return;
        }
        final String target = name;
        final int targetMeta = meta;
        final double targetEmc = emc;
        CustomEMCParser.UpdateResult result = CustomEMCParser.updateAndApply(
            () -> CustomEMCParser.addToFile(target, targetMeta, targetEmc), EMCMapper::reloadCustomEmc);
        EmcCommandUpdate.finish(sender, result,
            new ChatComponentTranslation("pe.command.set.success", name, emc),
            new ChatComponentTranslation("pe.command.set.invaliditem", name));
	}
}
