package moze_intel.projecte.primordial;

import java.util.*;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.items.ItemPE;
import net.minecraft.block.Block;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.fluids.IFluidHandler;

public final class ConfiguratorItem extends ItemPE {
    public static final int GUI = 81, PLAYER_GUI = 82;
    public ConfiguratorItem() { setUnlocalizedName("configurator"); setTextureName("projecte:configurator"); setMaxStackSize(1); }
    @Override public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
        TileEntity tile = w.getTileEntity(x, y, z);
        if (tile instanceof BlackholeTile) { if (!w.isRemote) player.addChatComponentMessage(new ChatComponentTranslation("pe.primordial.blackhole_no_config")); return true; }
        if (!(tile instanceof IInventory) && !(tile instanceof IFluidHandler) && !MachinePorts.hasResource(tile, 0) && !MachinePorts.hasResource(tile, 1) && !MachinePorts.hasResource(tile, 2)) return false;
        if (!w.isRemote) {
            PrimordialSavedData data = PrimordialSavedData.get(); String key = w.provider.dimensionId + ":" + x + ":" + y + ":" + z;
            MachineConfig c = data.machines.get(key);
            if (c == null) { c = new MachineConfig(player.getUniqueID(), w.provider.dimensionId, x, y, z); c.block = Block.blockRegistry.getNameForObject(w.getBlock(x, y, z)); data.machines.put(key, c); data.markDirty(); }
            if (!c.owner.equals(player.getUniqueID())) { player.addChatComponentMessage(new ChatComponentTranslation("pe.primordial.not_owner")); return true; }
            player.openGui(PECore.instance, GUI, w, x, y, z);
        }
        return true;
    }
    @Override public ItemStack onItemRightClick(ItemStack stack, World w, EntityPlayer player) {
        if (!w.isRemote && !player.isSneaking()) player.openGui(PECore.instance, PLAYER_GUI, w, 0, 0, 0);
        return stack;
    }
    @Override public void addInformation(ItemStack s, EntityPlayer p, List lines, boolean advanced) { lines.add(net.minecraft.util.StatCollector.translateToLocal("pe.primordial.configurator_help")); }
}
