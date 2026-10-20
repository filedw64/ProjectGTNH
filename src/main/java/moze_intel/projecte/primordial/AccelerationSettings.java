package moze_intel.projecte.primordial;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

public final class AccelerationSettings {
    public enum Kind { BLOCK, ENTITY, AREA, DAYTIME, WEATHER, AREA_FILTER, IDLE_FILTER }
    public static NBTTagCompound tag(ItemStack s) { if (!s.hasTagCompound()) s.setTagCompound(new NBTTagCompound()); return s.getTagCompound(); }
    public static Kind kind(ItemStack s) { return Kind.values()[Math.max(0, Math.min(6, tag(s).getInteger("kind")))]; }
    public static int rate(ItemStack s) { int v = tag(s).getInteger("rate"); return v >= 2 && v <= 1024 ? v : 2; }
    public static double costPerSecond(int multiplier, double coefficient) {
        return coefficient * Math.pow(Math.max(2, Math.min(1024, multiplier)) - 1D, 1.5D);
    }
    public static NBTTagList targets(ItemStack s) { return tag(s).getTagList("targets", 10); }
    public static void stop(ItemStack s) { NBTTagList list = targets(s); for (int i = 0; i < list.tagCount(); i++) list.getCompoundTagAt(i).setBoolean("enabled", false); tag(s).setTag("targets", list); }
    public static String id(ItemStack s) { NBTTagCompound t = tag(s); if (!t.hasKey("id")) t.setString("id", java.util.UUID.randomUUID().toString()); return t.getString("id"); }
    private AccelerationSettings() {}
}
