package cofh.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/** Compile-time signature; runtime implementation is supplied by CoFH. */
public interface IEnergyReceiver extends IEnergyConnection {
    int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate);
    int getEnergyStored(ForgeDirection from);
    int getMaxEnergyStored(ForgeDirection from);
}
