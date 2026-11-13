package moze_intel.projecte.network.commands;

import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.handlers.TileEntityHandler;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;

final class EmcCommandUpdate
{
	private EmcCommandUpdate() {}

	static void finish(ICommandSender sender, CustomEMCParser.UpdateResult result,
		ChatComponentTranslation success, ChatComponentTranslation invalid)
	{
		if (result == CustomEMCParser.UpdateResult.WRITE_FAILED) {
			sender.addChatMessage(invalid);
			return;
		}
		if (result != CustomEMCParser.UpdateResult.APPLIED) {
			sender.addChatMessage(new ChatComponentTranslation(result == CustomEMCParser.UpdateResult.ROLLED_BACK
				? "pe.command.emc.rolled_back" : "pe.command.emc.restore_failed"));
			return;
		}
		sender.addChatMessage(success);
		try {
			TileEntityHandler.checkAllCondensers();
			PacketHandler.sendFragmentedEmcPacketToAll();
			sender.addChatMessage(new ChatComponentTranslation("pe.command.emc.applied"));
		}
		catch (RuntimeException e) {
			PELogger.logError("[failed] EMC applied but client/condenser refresh failed: %s", e);
			sender.addChatMessage(new ChatComponentTranslation("pe.command.emc.refresh_failed"));
		}
	}
}
