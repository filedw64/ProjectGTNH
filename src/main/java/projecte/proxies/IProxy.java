package projecte.proxies;

import net.minecraft.entity.player.EntityPlayer;
import projecte.playerData.AlchBagProps;
import projecte.playerData.TransmutationProps;

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
