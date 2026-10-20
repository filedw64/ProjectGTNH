package moze_intel.projecte.primordial;

import java.util.List;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.items.ItemPE;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.StatCollector;

public final class EmcAcceleratorItem extends ItemPE {
    public static final int GUI = 83;
    public EmcAcceleratorItem() { setUnlocalizedName("emc_accelerator"); setTextureName("projecte:emc_accelerator"); setMaxStackSize(1); }
    @Override public boolean onItemUseFirst(ItemStack s, EntityPlayer p, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        if (!w.isRemote) {
            AccelerationService.bind(s, p, x, y, z); p.openGui(PECore.instance, GUI, w, 0, 0, 0);
        }
        return true;
    }
    @Override public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (!w.isRemote) { p.openGui(PECore.instance, GUI, w, 0, 0, 0); }
        return s;
    }
    @Override public boolean itemInteractionForEntity(ItemStack s, EntityPlayer p, net.minecraft.entity.EntityLivingBase entity) {
        if (!p.worldObj.isRemote) { AccelerationService.bindEntity(s, p, entity); p.openGui(PECore.instance, GUI, p.worldObj, 0, 0, 0); } return true;
    }
    @Override public void addInformation(ItemStack s, EntityPlayer p, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("pe.primordial.mode") + ": " + AccelerationSettings.kind(s));
        lines.add(AccelerationSettings.rate(s) + "x / " + AccelerationSettings.targets(s).tagCount());
        lines.add(StatCollector.translateToLocal("pe.primordial.accelerator_help"));
    }
}
