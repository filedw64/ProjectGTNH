package projectgtnh.network.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import projectgtnh.config.CustomEMCParser;
import projectgtnh.emc.EMCMapper;
import projectgtnh.network.PacketHandler;
import projectgtnh.handlers.TileEntityHandler;

public class ReloadEmcCMD extends ProjectGTNHBaseCMD
{
	@Override
	public String getCommandName() 
	{
		return "projecte_reloadEMC";
	}
	
	@Override
	public String getCommandUsage(ICommandSender sender)
	{
		return "/projecte reloadEMC";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] params) 
	{
		sender.addChatMessage(new ChatComponentTranslation("pe.command.reload.started"));

		EMCMapper.clearMaps();
		CustomEMCParser.readUserData();
		EMCMapper.map();
		TileEntityHandler.checkAllCondensers();

		sender.addChatMessage(new ChatComponentTranslation("pe.command.reload.success"));

		PacketHandler.sendFragmentedEmcPacketToAll();
	}

	@Override
	public int getRequiredPermissionLevel() 
	{
		return 4;
	}
}
