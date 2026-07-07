package projectgtnh.proxies;

import projectgtnh.playerData.AlchBagProps;
import projectgtnh.playerData.TransmutationProps;
import net.minecraft.entity.player.EntityPlayer;

public interface IProxy
{
    void registerKeyBinds();
    void registerRenderers();
    void registerClientOnlyEvents();
    void initializeManual();
    void clearClientKnowledge();
    TransmutationProps getClientTransmutationProps();
    AlchBagProps getClientBagProps();
    EntityPlayer getClientPlayer();
    boolean isJumpPressed();
}
