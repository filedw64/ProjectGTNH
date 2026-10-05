package moze_intel.projecte.integration.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** AppliedE queue transfer. Not a craftable token or a cryptographically authenticated currency. */
public final class ItemEMCRecoveryBundle extends Item {
    // AppliedE core hardening v3
    // AppliedE persistent recovery v4
    public ItemEMCRecoveryBundle() {
        setUnlocalizedName("pe_emc_recovery_bundle");
        setTextureName("projecte:transmute_tablet");
        setMaxStackSize(1);
        // No creative tab, EMC mapping or recipe.
    }
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
            int side, float hitX, float hitY, float hitZ) {
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof TileMEEMCLink)) return false;
        if (world.isRemote) return true;
        if (!player.canPlayerEdit(x, y, z, side, stack)) return false;
        boolean restored = ((TileMEEMCLink) raw).restoreEMCRecoveryBundle(stack, player);
        if (!restored) player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("pe.ae2.recovery_rejected"));
        return true;
    }
    @Override @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, java.util.List<String> lines, boolean advanced) {
        lines.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.recovery_hint"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.recovery_warning"));
        if (stack.hasTagCompound() && stack.getTagCompound().getInteger("RecoveryVersion") == 4)
            lines.add(stack.getTagCompound().getString("Receipt"));
        else lines.add(net.minecraft.util.StatCollector.translateToLocal("pe.ae2.recovery_legacy"));
    }
}
