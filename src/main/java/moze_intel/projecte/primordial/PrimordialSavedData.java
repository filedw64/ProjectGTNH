package moze_intel.projecte.primordial;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/** Overworld storage keeps machine rules and offline credits independent of loaded dimensions. */
public final class PrimordialSavedData extends WorldSavedData {
    private static final String NAME = "projecte_primordial";
    public final Map<String, MachineConfig> machines = new LinkedHashMap<>();
    private final Map<UUID, Double> pending = new HashMap<>();
    public PrimordialSavedData() { super(NAME); }
    public PrimordialSavedData(String name) { super(name); }
    public static PrimordialSavedData get() {
        net.minecraft.world.World w = DimensionManager.getWorld(0);
        if (w == null) throw new IllegalStateException("No overworld");
        PrimordialSavedData d = (PrimordialSavedData) w.mapStorage.loadData(PrimordialSavedData.class, NAME);
        if (d == null) { d = new PrimordialSavedData(); w.mapStorage.setData(NAME, d); }
        return d;
    }
    public boolean canAcceptCredit(UUID owner, double amount) {
        if (owner == null || !Double.isFinite(amount) || amount <= 0) return false;
        double old = pending.containsKey(owner) ? pending.get(owner) : 0;
        return Double.isFinite(old + amount) && old + amount > old;
    }
    public boolean credit(UUID owner, double amount) {
        if (owner == null || !Double.isFinite(amount) || amount <= 0) return false;
        double old = pending.containsKey(owner) ? pending.get(owner) : 0;
        double next = old + amount;
        if (!Double.isFinite(next) || next <= old) return false;
        pending.put(owner, next); markDirty(); return true;
    }
    public void settle(net.minecraft.entity.player.EntityPlayerMP p) {
        Double value = pending.get(p.getUniqueID());
        if (value != null && EmcTransactions.creditOnline(p, value)) { pending.remove(p.getUniqueID()); markDirty(); }
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        machines.clear(); pending.clear();
        NBTTagList list = tag.getTagList("machines", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            try { MachineConfig c = MachineConfig.read(list.getCompoundTagAt(i)); machines.put(c.key(), c); }
            catch (IllegalArgumentException ignored) { /* Invalid entry must not prevent world loading. */ }
        }
        list = tag.getTagList("credits", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            try { double v = t.getDouble("emc"); if (Double.isFinite(v) && v > 0) pending.put(UUID.fromString(t.getString("owner")), v); }
            catch (IllegalArgumentException ignored) { }
        }
    }
    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (MachineConfig c : machines.values()) list.appendTag(c.write());
        tag.setTag("machines", list); list = new NBTTagList();
        for (Map.Entry<UUID, Double> e : pending.entrySet()) {
            NBTTagCompound t = new NBTTagCompound(); t.setString("owner", e.getKey().toString()); t.setDouble("emc", e.getValue()); list.appendTag(t);
        }
        tag.setTag("credits", list);
    }
}
