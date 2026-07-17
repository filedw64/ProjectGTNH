package moze_intel.projecte.integration;

import codechicken.core.launch.CodeChickenCorePlugin;

public class CCCInit {
    public static boolean finiteWater = false;
    public static void init() {
        finiteWater = CodeChickenCorePlugin.config.getTag("tweaks", false).getTag("finiteWater", false).getBooleanValue();
    }
}
