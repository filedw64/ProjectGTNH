package moze_intel.projecte.integration.ae2;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Processing pattern. CPU consumes the EMC input; the medium must NEVER debit it again. */
public final class EMCTransmutationPattern implements ICraftingPatternDetails {
    // AppliedE core refinement v2
    private final ItemStack definition;
    private final NBTTagCompound identity;
    private final int identityHash;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private int priority;

    private EMCTransmutationPattern(ItemStack definition, IAEItemStack[] inputs, IAEItemStack[] outputs) {
        this.definition = definition.copy(); this.inputs = inputs; this.outputs = outputs;
        priority = definition.getTagCompound().getInteger("Priority");
        identity = (NBTTagCompound) this.definition.getTagCompound().copy();
        identity.removeTag("Priority");
        identityHash = identity.hashCode();
    }
    private static IAEItemStack ae(ItemStack stack, long count) {
        IAEItemStack value = AEApi.instance().storage().createItemStack(stack);
        if (value == null || count <= 0 || count > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pattern stack");
        value.setStackSize(count);
        return value;
    }
    public static EMCTransmutationPattern item(ItemStack output, BigInteger cost, int priority) {
        if (output == null || output.getItem() == null || output.getItem() == AE2Integration.itemEMCResource
                || output.getItem() == AE2Integration.itemEMCTransmutationPattern || cost == null || cost.signum() <= 0
                || cost.bitLength() > 8 * ItemEMCResource.MAX_TIERS)
            throw new IllegalArgumentException("Unsupported transmutation cost / output");
        ItemStack target = output.copy(); target.stackSize = 1;
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound item = new NBTTagCompound(); target.writeToNBT(item);
        tag.setTag("Target", item); tag.setByteArray("Cost", cost.toByteArray()); tag.setInteger("Priority", priority);
        List<IAEItemStack> in = new ArrayList<>();
        for (int tier = 1; cost.signum() > 0; tier++, cost = cost.shiftRight(8)) {
            int digit = cost.and(BigInteger.valueOf(255)).intValue();
            if (digit > 0) in.add(ae(ItemEMCResource.stack(tier), digit));
        }
        return make(tag, in.toArray(new IAEItemStack[in.size()]), new IAEItemStack[] { ae(target, 1) });
    }
    public static EMCTransmutationPattern split(int tier, int priority) {
        if (tier < 2 || tier > ItemEMCResource.MAX_TIERS) throw new IllegalArgumentException("Invalid split tier");
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("SplitTier", tier); tag.setInteger("Priority", priority);
        return make(tag, new IAEItemStack[] { ae(ItemEMCResource.stack(tier), 1) },
            new IAEItemStack[] { ae(ItemEMCResource.stack(tier - 1), ItemEMCResource.RADIX) });
    }
    private static EMCTransmutationPattern make(NBTTagCompound tag, IAEItemStack[] in, IAEItemStack[] out) {
        tag.setInteger("Version", 1);
        ItemStack definition = new ItemStack(AE2Integration.itemEMCTransmutationPattern);
        definition.setTagCompound(tag);
        return new EMCTransmutationPattern(definition, in, out);
    }

    public static EMCTransmutationPattern read(ItemStack stack) {
        if (stack == null || stack.getItem() != AE2Integration.itemEMCTransmutationPattern
                || stack.getItemDamage() != 0 || !stack.hasTagCompound())
            throw new IllegalArgumentException("Missing pattern");
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("Version", 3) || tag.getInteger("Version") != 1)
            throw new IllegalArgumentException("Unsupported pattern version");
        if (!tag.hasKey("Priority", 3)) throw new IllegalArgumentException("Missing priority");
        EMCTransmutationPattern restored;
        if (tag.hasKey("SplitTier")) {
            if (!tag.hasKey("SplitTier", 3) || tag.hasKey("Target") || tag.hasKey("Cost"))
                throw new IllegalArgumentException("Mixed pattern definition");
            restored = split(tag.getInteger("SplitTier"), tag.getInteger("Priority"));
        } else {
            if (!tag.hasKey("Target", 10) || !tag.hasKey("Cost", 7))
                throw new IllegalArgumentException("Missing target or cost");
            byte[] bytes = tag.getByteArray("Cost");
            if (bytes.length < 1 || bytes.length > 33) throw new IllegalArgumentException("Invalid cost size");
            restored = item(ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Target")),
                new BigInteger(bytes), tag.getInteger("Priority"));
        }
        // Accept canonical v1 records only; original v1 saves remain compatible.
        if (!ItemStack.areItemStackTagsEqual(stack, restored.definition))
            throw new IllegalArgumentException("Noncanonical pattern NBT");
        return restored;
    }
    public boolean matchesTable(InventoryCrafting table) {
        if (table == null || table.getSizeInventory() != inputs.length) return false;
        for (int i = 0; i < inputs.length; i++) {
            ItemStack actual = table.getStackInSlot(i), expected = inputs[i].getItemStack();
            if (actual == null || actual.stackSize != inputs[i].getStackSize()
                    || !EMCInventoryHandler.matchesPrecision(actual, expected, 0)) return false;
        }
        return true;
    }
    private static IAEItemStack[] copy(IAEItemStack[] source) {
        IAEItemStack[] result = new IAEItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i].copy();
        return result;
    }
    @Override public ItemStack getPattern() { return definition.copy(); }
    @Override public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
        return slot >= 0 && slot < inputs.length && EMCInventoryHandler.matchesPrecision(inputs[slot].getItemStack(), stack, 0);
    }
    @Override public boolean isCraftable() { return false; }
    @Override public IAEItemStack[] getInputs() { return copy(inputs); }
    @Override public IAEItemStack[] getCondensedInputs() { return copy(inputs); }
    @Override public IAEItemStack[] getOutputs() { return copy(outputs); }
    @Override public IAEItemStack[] getCondensedOutputs() { return copy(outputs); }
    @Override public boolean canSubstitute() { return false; }
    @Override public boolean canBeSubstitute() { return false; }
    @Override public ItemStack getOutput(InventoryCrafting table, World world) { return outputs[0].getItemStack(); }
    @Override public int getPriority() { return priority; }
    @Override public void setPriority(int priority) {
        this.priority = priority; definition.getTagCompound().setInteger("Priority", priority);
    }
    @Override public boolean equals(Object obj) {
        return obj instanceof EMCTransmutationPattern
            && identity.equals(((EMCTransmutationPattern) obj).identity);
    }
    @Override public int hashCode() { return identityHash; }
}
