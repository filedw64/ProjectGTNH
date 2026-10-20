package moze_intel.projecte.primordial;

import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

public final class AccelerationService {
    private static int ticks, scans;
    private static long deadline;
    public static void beginTick() { ticks = scans = 0; deadline = System.nanoTime() + (long) (PrimordialConfig.sliceMillis * 1000000); }
    private static boolean room() { return ticks < PrimordialConfig.extraTicks && scans < PrimordialConfig.scans && System.nanoTime() < deadline; }
    public static boolean blacklisted(String name) {
        if (name == null) return true;
        for (String b : PrimordialConfig.accelerationBlacklist) if (b.endsWith("*") ? name.startsWith(b.substring(0, b.length() - 1)) : name.equals(b)) return true;
        return false;
    }
    public static void setRate(ItemStack s, int rate) {
        if (rate < 2 || rate > 1024) return;
        AccelerationSettings.stop(s);
        NBTTagCompound t = AccelerationSettings.tag(s);
        t.setInteger("rate", rate); t.removeTag("confirmation");
        NBTTagList list = AccelerationSettings.targets(s);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound target = list.getCompoundTagAt(i);
            target.setInteger("rate", rate); target.setInteger("repetitions", 0);
        }
    }
    public static void cycle(ItemStack s) {
        int rate = AccelerationSettings.rate(s);
        setRate(s, rate >= 1024 ? 2 : Math.min(1024, rate * 2));
    }
    public static void bind(ItemStack s, EntityPlayer p, int x, int y, int z) {
        NBTTagCompound tag = AccelerationSettings.tag(s); AccelerationSettings.Kind kind = AccelerationSettings.kind(s);
        if (kind == AccelerationSettings.Kind.ENTITY) return;
        if (kind == AccelerationSettings.Kind.AREA_FILTER || kind == AccelerationSettings.Kind.IDLE_FILTER) {
            NBTTagList list = tag.getTagList(kind.name(), 10); String key = p.dimension + ":" + x + ":" + y + ":" + z;
            NBTTagList next = new NBTTagList(); boolean found = false;
            for (int i = 0; i < list.tagCount(); i++) { NBTTagCompound e = list.getCompoundTagAt(i); if (key.equals(e.getString("key"))) found = true; else next.appendTag(e.copy()); }
            if (!found && next.tagCount() < 3375) { NBTTagCompound e = new NBTTagCompound(); e.setString("key", key); next.appendTag(e); }
            tag.setTag(kind.name(), next); return;
        }
        NBTTagCompound e = new NBTTagCompound(); e.setInteger("dim", p.dimension); e.setInteger("x", x); e.setInteger("y", y); e.setInteger("z", z); e.setString("kind", kind.name()); e.setString("block", Block.blockRegistry.getNameForObject(p.worldObj.getBlock(x, y, z))); e.setInteger("rate", AccelerationSettings.rate(s));
        add(s, e);
    }
    public static void bindEntity(ItemStack s, EntityPlayer p, Entity e) {
        if (AccelerationSettings.kind(s) != AccelerationSettings.Kind.ENTITY || e instanceof EntityPlayer || e.isDead) return;
        NBTTagCompound t = new NBTTagCompound(); t.setInteger("dim", p.dimension); t.setString("uuid", e.getUniqueID().toString()); t.setInteger("entity", e.getEntityId()); t.setString("kind", "ENTITY"); t.setInteger("rate", AccelerationSettings.rate(s)); add(s, t);
    }
    private static void add(ItemStack s, NBTTagCompound entry) {
        NBTTagList list = AccelerationSettings.targets(s), next = new NBTTagList(); boolean found = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound old = list.getCompoundTagAt(i);
            boolean same = old.getInteger("dim") == entry.getInteger("dim") && old.getString("kind").equals(entry.getString("kind")) && (entry.hasKey("uuid") ? old.getString("uuid").equals(entry.getString("uuid")) : old.getInteger("x") == entry.getInteger("x") && old.getInteger("y") == entry.getInteger("y") && old.getInteger("z") == entry.getInteger("z"));
            if (same) found = true; else next.appendTag(old.copy());
        }
        if (!found && next.tagCount() < PrimordialConfig.targets) next.appendTag(entry);
        AccelerationSettings.tag(s).setTag("targets", next); AccelerationSettings.tag(s).removeTag("confirmation");
    }
    public static void toggle(ItemStack s, EntityPlayer p) {
        NBTTagList list = AccelerationSettings.targets(s);
        boolean active = false, high = false;
        for (int i = 0; i < list.tagCount(); i++) { active |= list.getCompoundTagAt(i).getBoolean("enabled"); high |= list.getCompoundTagAt(i).getInteger("rate") >= 256; }
        if (active) { AccelerationSettings.stop(s); return; }
        if (list.tagCount() == 0) { p.openGui(moze_intel.projecte.PECore.instance, EmcAcceleratorItem.GUI, p.worldObj, 0, 0, 0); return; }
        NBTTagCompound t = AccelerationSettings.tag(s);
        if (high) {
            NBTTagCompound confirmation = new NBTTagCompound(); confirmation.setString("token", UUID.randomUUID().toString()); confirmation.setLong("time", p.worldObj.getTotalWorldTime()); confirmation.setString("tool", AccelerationSettings.id(s)); confirmation.setTag("quote", list.copy()); confirmation.setDouble("cost", PrimordialConfig.accelerationCost); t.setTag("confirmation", confirmation);
            p.openGui(moze_intel.projecte.PECore.instance, EmcAcceleratorItem.GUI, p.worldObj, 0, 0, 0); return;
        }
        activate(s, p);
    }
    public static void confirm(ItemStack s, EntityPlayer p, String token) {
        NBTTagCompound t = AccelerationSettings.tag(s), c = t.getCompoundTag("confirmation"); long elapsed = p.worldObj.getTotalWorldTime() - c.getLong("time");
        if (!token.equals(c.getString("token")) || elapsed < 20 || elapsed > 1200 || !AccelerationSettings.id(s).equals(c.getString("tool")) || !AccelerationSettings.targets(s).equals(c.getTagList("quote", 10)) || c.getDouble("cost") != PrimordialConfig.accelerationCost || p.getHeldItem() != s) return;
        t.removeTag("confirmation"); activate(s, p);
    }
    private static void activate(ItemStack s, EntityPlayer p) {
        NBTTagList list = AccelerationSettings.targets(s); long until = p.worldObj.getTotalWorldTime() + PrimordialConfig.duration;
        for (int i = 0; i < list.tagCount(); i++) { list.getCompoundTagAt(i).setBoolean("enabled", true); list.getCompoundTagAt(i).setLong("until", until); }
        AccelerationSettings.tag(s).setDouble("remaining", Integer.MAX_VALUE);
    }
    public static void tick(EntityPlayerMP p, ItemStack s) {
        NBTTagList list = AccelerationSettings.targets(s);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i); if (!e.getBoolean("enabled")) continue;
            if (p.worldObj.getTotalWorldTime() >= e.getLong("until")) { e.setBoolean("enabled", false); continue; }
            WorldServer w = DimensionManager.getWorld(e.getInteger("dim")); if (w == null || !room()) continue;
            int rate = Math.max(2, Math.min(1024, e.getInteger("rate")));
            String kind = e.getString("kind");
            int work = rate - 1;
            if (kind.equals("AREA")) work = Math.min(PrimordialConfig.extraTicks, 3375 * (rate - 1));
            double unit = AccelerationSettings.costPerSecond(rate, PrimordialConfig.accelerationCost) / 20D / (rate - 1);
            int completed = 0;
            try {
                while (completed < work && room()) {
                    if (!canRun(s, w, e)) break;
                    double remaining = AccelerationSettings.tag(s).getDouble("remaining");
                    if (remaining < unit || !EmcTransactions.spend(p, unit)) { e.setBoolean("enabled", false); break; }
                    AccelerationSettings.tag(s).setDouble("remaining", remaining - unit);
                    long start = System.nanoTime(); boolean ran = step(s, w, e); ticks++; completed++;
                    if (!ran) { EmcTransactions.refund(p, unit); AccelerationSettings.tag(s).setDouble("remaining", remaining); }
                    if (System.nanoTime() - start > (long) (PrimordialConfig.sliceMillis * 1000000)) { e.setBoolean("enabled", false); break; }
                }
            } catch (RuntimeException | LinkageError failure) {
                e.setBoolean("enabled", false); moze_intel.projecte.utils.PELogger.logWarn("Disabled failing accelerator target: %s", failure.toString());
            }
        }
    }
    private static boolean canRun(ItemStack s, WorldServer w, NBTTagCompound e) {
        scans++; String kind = e.getString("kind");
        if (kind.equals("DAYTIME")) return w.getGameRules().getGameRuleBooleanValue("doDaylightCycle");
        if (kind.equals("WEATHER")) return !w.provider.hasNoSky;
        if (kind.equals("ENTITY")) { Entity entity = w.getEntityByID(e.getInteger("entity")); return entity != null && !entity.isDead && !(entity instanceof EntityPlayer) && entity.getUniqueID().toString().equals(e.getString("uuid")) && w.blockExists((int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)); }
        int x = e.getInteger("x"), y = e.getInteger("y"), z = e.getInteger("z");
        if (!w.blockExists(x, y, z)) return false;
        if (!kind.equals("AREA") && blacklisted(Block.blockRegistry.getNameForObject(w.getBlock(x, y, z)))) return false;
        return kind.equals("AREA") || e.getString("block").equals(Block.blockRegistry.getNameForObject(w.getBlock(x, y, z)));
    }
    private static boolean step(ItemStack s, WorldServer w, NBTTagCompound e) {
        String kind = e.getString("kind");
        if (kind.equals("DAYTIME")) { if (w.getWorldTime() == Long.MAX_VALUE) return false; w.setWorldTime(w.getWorldTime() + 1); return true; }
        if (kind.equals("WEATHER")) { weather(w); return true; }
        if (kind.equals("ENTITY")) { Entity entity = w.getEntityByID(e.getInteger("entity")); entity.ticksExisted++; entity.onUpdate(); return true; }
        int x = e.getInteger("x"), y = e.getInteger("y"), z = e.getInteger("z");
        if (kind.equals("AREA")) {
            int cursor = Math.floorMod(e.getInteger("cursor"), 3375);
            int repetitions = e.getInteger("repetitions") + 1;
            int rate = Math.max(2, Math.min(1024, e.getInteger("rate")));
            if (repetitions >= rate - 1) { repetitions = 0; e.setInteger("cursor", (cursor + 1) % 3375); }
            e.setInteger("repetitions", repetitions);
            x += cursor % 15 - 7; y += cursor / 15 % 15 - 7; z += cursor / 225 - 7;
            String key = w.provider.dimensionId + ":" + x + ":" + y + ":" + z;
            NBTTagList white = AccelerationSettings.tag(s).getTagList("AREA_FILTER", 10), black = AccelerationSettings.tag(s).getTagList("IDLE_FILTER", 10);
            if (white.tagCount() > 0 && !contains(white, key)) { e.setInteger("repetitions", 0); e.setInteger("cursor", (cursor + 1) % 3375); return false; }
        }
        if (!w.blockExists(x, y, z)) return false;
        Block b = w.getBlock(x, y, z); if (blacklisted(Block.blockRegistry.getNameForObject(b))) return false;
        TileEntity tile = w.getTileEntity(x, y, z);
        if (tile != null && !tile.isInvalid() && tile.canUpdate()) {
            if (kind.equals("AREA") && contains(AccelerationSettings.tag(s).getTagList("IDLE_FILTER", 10), w.provider.dimensionId + ":" + x + ":" + y + ":" + z) && knownIdle(tile) && e.getInteger("repetitions") >= 3) return false;
            if (!aeTick(tile)) tile.updateEntity(); return true;
        }
        if (b.getTickRandomly()) { b.updateTick(w, x, y, z, w.rand); return true; }
        return false;
    }
    private static boolean aeTick(TileEntity tile) {
        if (!cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2")) return false;
        try {
            Class<?> nodeType = Class.forName("appeng.api.networking.IGridNode");
            java.lang.reflect.Method tick = tile.getClass().getMethod("tickingRequest", nodeType, int.class);
            java.lang.reflect.Method getNode = tile.getClass().getMethod("getGridNode", net.minecraftforge.common.util.ForgeDirection.class);
            for (net.minecraftforge.common.util.ForgeDirection face : net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS) {
                Object node = getNode.invoke(tile, face);
                if (node != null) { tick.invoke(tile, node, 1); return true; }
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) { return false; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("AE2 tickingRequest", failure); }
        return false;
    }
    private static final java.util.Map<String, Long> idleSince = new java.util.HashMap<>();
    private static boolean knownIdle(TileEntity tile) {
        String key = tile.getWorldObj().provider.dimensionId + ":" + tile.xCoord + ":" + tile.yCoord + ":" + tile.zCoord;
        try {
            Object active = tile.getClass().getMethod("isActive").invoke(tile);
            if (!(active instanceof Boolean) || ((Boolean) active)) { idleSince.remove(key); return false; }
            long now = tile.getWorldObj().getTotalWorldTime();
            Long since = idleSince.get(key);
            if (since == null) { if (idleSince.size() >= 4096) idleSince.clear(); idleSince.put(key, now); return false; }
            return now - since >= 100;
        } catch (ReflectiveOperationException ignored) { idleSince.remove(key); return false; }
    }
    public static void clear() { idleSince.clear(); }
    private static boolean contains(NBTTagList list, String key) { for (int i = 0; i < list.tagCount(); i++) if (list.getCompoundTagAt(i).getString("key").equals(key)) return true; return false; }
    private static void weather(WorldServer w) {
        WorldInfo info = w.getWorldInfo();
        int rain = info.getRainTime(), thunder = info.getThunderTime();
        if (rain <= 0) info.setRainTime(info.isRaining() ? w.rand.nextInt(12000) + 12000 : w.rand.nextInt(168000) + 12000);
        else { info.setRainTime(rain - 1); if (rain == 1) info.setRaining(!info.isRaining()); }
        if (thunder <= 0) info.setThunderTime(info.isThundering() ? w.rand.nextInt(12000) + 3600 : w.rand.nextInt(168000) + 12000);
        else { info.setThunderTime(thunder - 1); if (thunder == 1) info.setThundering(!info.isThundering()); }
    }
    private AccelerationService() {}
}
