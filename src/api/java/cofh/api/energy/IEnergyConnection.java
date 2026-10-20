package cofh.api.energy;

import net.minecraftforge.common.util.ForgeDirection;

/** Compile-time signature of the optional CoFH energy API. */
public interface IEnergyConnection {
    boolean canConnectEnergy(ForgeDirection from);
}
