package moze_intel.projecte.primordial;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Limits are shared by every tool on the server, rather than multiplied by tool count. */
public final class PrimordialConfig {
    public static int targets, extraTicks, scans, duration, items, fluids, entities, machineRules;
    public static final double rfPerEmc = 1024D;
    public static double accelerationCost, sliceMillis, euPerEmc, manaPerEmc;
    public static String[] accelerationBlacklist, entityPrices;

    public static void init(File directory) {
        Configuration c = new Configuration(new File(directory, "primordial.cfg"));
        c.load();
        targets = c.getInt("targetsPerTool", "acceleration", 64, 1, 256, "Maximum bindings per accelerator");
        extraTicks = c.getInt("extraTicksPerTick", "acceleration", 4096, 1, 65536, "Global extra tick budget");
        scans = c.getInt("scansPerTick", "acceleration", 2048, 1, 65536, "Global scan budget");
        duration = c.getInt("activationTicks", "acceleration", 600, 1, 72000, "Maximum continuous activation");
        accelerationCost = c.get("acceleration", "emcPerMultiplierSecond", 1000D,
            "EMC/second per target = this coefficient * (multiplier - 1)^1.5; partial acceleration is charged proportionally").getDouble(1000D);
        sliceMillis = c.get("acceleration", "millisecondsPerTick", 2D).getDouble(2D);
        accelerationBlacklist = c.getStringList("blacklist", "acceleration", new String[] {
            "ProjectE:*", "primordial:*", "torcherino:*", "minecraft:mob_spawner"
        }, "Registry names, optionally ending in *; ProjectE is excluded to prevent recursion");
        items = c.getInt("itemsPerTick", "blackhole", 4096, 1, 1048576, "Per blackhole");
        fluids = c.getInt("fluidMbPerTick", "blackhole", 100000, 1, Integer.MAX_VALUE, "Per blackhole");
        entities = c.getInt("entitiesPerTick", "blackhole", 16, 1, 256, "Per blackhole");
        machineRules = c.getInt("rulesPerTick", "configurator", 2048, 1, 65536, "Global round-robin rule budget");
        // RF has a fixed exchange rate, including worlds with the former configurable rate.
        c.getCategory("energy").remove("rfPerEmc");
        euPerEmc = c.get("energy", "euPerEmc", 1000D, "EMC to EU supply only; EU to EMC recovery is not supported").getDouble(1000D);
        manaPerEmc = c.get("resource", "manaPerEmc", 1D, "Botania mana per EMC; matches the native Botania mapper by default").getDouble(1D);
        entityPrices = c.getStringList("prices", "blackhole", new String[0], "Entity registry name=EMC; overrides default living entity prices");
        if (!Double.isFinite(accelerationCost) || accelerationCost < 0) accelerationCost = 1000;
        if (!Double.isFinite(sliceMillis) || sliceMillis <= 0) sliceMillis = 2;
        if (!Double.isFinite(euPerEmc) || euPerEmc < 0) euPerEmc = 0;
        if (!Double.isFinite(manaPerEmc) || manaPerEmc < 0) manaPerEmc = 0;
        c.save();
    }
    private PrimordialConfig() {}
}
