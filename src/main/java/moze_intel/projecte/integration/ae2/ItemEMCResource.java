package moze_intel.projecte.integration.ae2;

import java.math.BigInteger;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Old-AE bridge for EMCKey: a transferable, EMC-backed item resource, not a learned item. */
public final class ItemEMCResource extends Item {
    public static final int RADIX = 256;
    public static final int MAX_TIERS = 32;
    public ItemEMCResource() {
        setUnlocalizedName("pe_emc_resource");
        setTextureName("projecte:transmute");
        // No creative tab or recipe: resources are withdrawn against real EMC.
        setMaxStackSize(64);
    }
    public static BigInteger unit(int tier) {
        if (tier < 1 || tier > MAX_TIERS) throw new IllegalArgumentException("Invalid EMC tier");
        return BigInteger.ONE.shiftLeft(8 * (tier - 1));
    }
    public static ItemStack stack(int tier) {
        unit(tier);
        ItemStack stack = new ItemStack(AE2Integration.itemEMCResource);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("EMCTier", tier);
        stack.setTagCompound(tag);
        return stack;
    }
    public static int tier(ItemStack stack) {
        if (stack == null || stack.getItem() != AE2Integration.itemEMCResource || stack.getItemDamage() != 0
                || !stack.hasTagCompound()) return 0;
        int tier = stack.getTagCompound().getInteger("EMCTier");
        // Reject extra NBT rather than turning arbitrary item variants into EMC.
        return tier >= 1 && tier <= MAX_TIERS
            && ItemStack.areItemStackTagsEqual(stack, stack(tier)) ? tier : 0;
    }
    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        int tier = tier(stack);
        if (tier != 0) lines.add(StatCollector.translateToLocalFormatted("pe.ae2.resource_unit", tier, unit(tier).toString()));
        lines.add(StatCollector.translateToLocal("pe.ae2.resource_bridge"));
    }
}
