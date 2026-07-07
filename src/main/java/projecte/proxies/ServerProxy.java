package projecte.proxies;

import net.minecraft.entity.player.EntityPlayer;
import projecte.playerData.AlchBagProps;
import projecte.playerData.TransmutationProps;

public class ServerProxy implements IProxy
{
	public void registerKeyBinds() {}
	public void registerRenderers() {}
	public void registerClientOnlyEvents() {}
	public void initializeManual() {}
	public void clearClientKnowledge() {}
	public TransmutationProps getClientTransmutationProps() { return null; }
	public AlchBagProps getClientBagProps() { return null; }
	public EntityPlayer getClientPlayer() { return null; }
	public boolean isJumpPressed() { return false; }
}
