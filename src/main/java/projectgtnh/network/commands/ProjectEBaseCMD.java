package projectgtnh.network.commands;

import projectgtnh.utils.ChatHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public abstract class ProjectGTNHBaseCMD extends CommandBase
{
	@Override
	public abstract String getCommandName();
	
	@Override
	public abstract int getRequiredPermissionLevel();

	@Override
	public abstract String getCommandUsage(ICommandSender sender);

	@Override
	public abstract void processCommand(ICommandSender sender, String[] params);
	
	protected void sendSuccess(ICommandSender sender, IChatComponent message)
	{
		sendMessage(sender, ChatHelper.modifyColor(message, EnumChatFormatting.GREEN));
	}
	
	protected void sendError(ICommandSender sender, IChatComponent message)
	{
		sendMessage(sender, ChatHelper.modifyColor(message, EnumChatFormatting.RED));
	}
	
	protected void sendMessage(ICommandSender sender, IChatComponent message)
	{
		sender.addChatMessage(message);
	}
}
