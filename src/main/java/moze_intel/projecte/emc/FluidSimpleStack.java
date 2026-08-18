package moze_intel.projecte.emc;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class FluidSimpleStack extends SimpleStack {
	public FluidSimpleStack(int id) {
		super(id, 0);
	}

	public FluidSimpleStack(FluidStack fs) {
		this(-1);
		if (fs != null && fs.getFluid() != null)
			id = fs.getFluidID();
	}

	@Override
	public ItemStack toItemStack() {
		return null;
	}

	public FluidStack toFluidStack() {
		Fluid fluid = FluidRegistry.getFluid(id);
		if (fluid == null) return null;
		return new FluidStack(fluid, 1);
	}

	@Override
	public SimpleStack copy() {
		return new FluidSimpleStack(id);
	}

	@Override
	public int hashCode() {
		return -id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof FluidSimpleStack other)
			return this.id == other.id;
		return false;
	}

	@Override
	public String toString() {
		Fluid fluid = FluidRegistry.getFluid(id);

		if (fluid == null)
			return "Fluid id:" + id;

		return FluidRegistry.getFluidName(fluid);
	}
}
