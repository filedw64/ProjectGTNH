package moze_intel.projecte.integration.ae2;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** A real pattern identity is required for CraftingCPUCluster NBT task recovery. */
public final class ItemEMCTransmutationPattern extends Item implements ICraftingPatternItem {
    public ItemEMCTransmutationPattern() {
        setUnlocalizedName("pe_emc_transmutation_pattern");
        setTextureName("projecte:transmute");
        setMaxStackSize(1);
    }
    @Override public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        try { return EMCTransmutationPattern.read(stack); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
