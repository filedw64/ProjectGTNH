package moze_intel.projecte.primordial;

import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;

public final class MachineConfig {
    public UUID owner;
    public int dimension, x, y, z;
    public String block = "";
    public final Rule[] rules = new Rule[41];
    public MachineConfig(UUID owner, int dimension, int x, int y, int z) {
        this.owner = owner; this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        for (int i = 0; i < rules.length; i++) rules[i] = new Rule();
    }
    public boolean playerInventory() { return block.equals("@player"); }
    public String key() { return playerInventory() ? "player:" + owner : dimension + ":" + x + ":" + y + ":" + z; }
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("owner", owner.toString()); t.setInteger("dim", dimension);
        t.setInteger("x", x); t.setInteger("y", y); t.setInteger("z", z); t.setString("block", block);
        NBTTagList list = new NBTTagList(); for (Rule r : rules) list.appendTag(r.write()); t.setTag("rules", list); return t;
    }
    public static MachineConfig read(NBTTagCompound t) {
        MachineConfig c = new MachineConfig(UUID.fromString(t.getString("owner")), t.getInteger("dim"), t.getInteger("x"), t.getInteger("y"), t.getInteger("z"));
        c.block = t.getString("block"); NBTTagList list = t.getTagList("rules", 10);
        for (int i = 0; i < Math.min(c.rules.length, list.tagCount()); i++) c.rules[i] = Rule.read(list.getCompoundTagAt(i));
        return c;
    }
    public static final class Rule {
        public ItemStack sample;
        public FluidStack fluid;
        public int amount = 1, interval = 20;
        public boolean keep, enabled;
        public NBTTagList ports = new NBTTagList();
        public long next;
        public NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            if (sample != null) t.setTag("sample", sample.writeToNBT(new NBTTagCompound()));
            if (fluid != null) t.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
            t.setInteger("amount", amount); t.setInteger("interval", interval); t.setBoolean("keep", keep); t.setBoolean("enabled", enabled); t.setTag("ports", ports.copy()); return t;
        }
        public static Rule read(NBTTagCompound t) {
            Rule r = new Rule(); r.sample = ItemStack.loadItemStackFromNBT(t.getCompoundTag("sample"));
            if (r.sample != null) r.sample.stackSize = 1;
            r.fluid = FluidStack.loadFluidStackFromNBT(t.getCompoundTag("fluid"));
            if (r.fluid != null) r.fluid.amount = 1;
            r.amount = Math.max(1, t.getInteger("amount")); r.interval = Math.max(1, Math.min(72000, t.getInteger("interval")));
            r.keep = t.getBoolean("keep"); r.enabled = t.getBoolean("enabled");
            NBTTagList p = t.getTagList("ports", 10);
            for (int i = 0; i < Math.min(256, p.tagCount()); i++) r.ports.appendTag(p.getCompoundTagAt(i).copy()); return r;
        }
    }
}
