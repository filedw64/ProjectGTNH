package moze_intel.projecte.integration;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.integration.NEI.NEIInit;
import moze_intel.projecte.integration.TConstruct.TConstructInit;
import moze_intel.projecte.integration.mappers.ChiselMapper;
import moze_intel.projecte.integration.mappers.GTMapper;
import moze_intel.projecte.utils.PELogger;

// Single class to initiate different mod compatibilities. Idea came from Avaritia by SpitefulFox
public final class Integration
{
	public static boolean NEI = false, PHC = false, PHN = false, CCC = false,
		EFR = false, natura = false, gregtech = false, forestry = false,
		chisel = false, duraDisplay = false, avaritia = false,
		botania = false, TConstruct = false;

    public static void preInitAE2() {
        if (Loader.isModLoaded("appliedenergistics2")) loadAE2();
    }

    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    private static void loadAE2() {
        moze_intel.projecte.integration.ae2.AE2Integration.preInit();
    }

    public static void initAE2Recipes() {
        if (Loader.isModLoaded("appliedenergistics2")) loadAE2Recipes();
    }

    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    private static void loadAE2Recipes() {
        moze_intel.projecte.integration.ae2.AE2Integration.initRecipes();
    }


    // AppliedE persistent recovery v4
    public static void startAE2Recovery(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        if (Loader.isModLoaded("appliedenergistics2")) startAE2RecoveryOptional(event);
    }
    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    private static void startAE2RecoveryOptional(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        moze_intel.projecte.integration.ae2.AE2Integration.recoveryServerStarting(event);
    }
    public static void stopAE2Recovery() {
        if (Loader.isModLoaded("appliedenergistics2")) stopAE2RecoveryOptional();
    }
    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    private static void stopAE2RecoveryOptional() {
        moze_intel.projecte.integration.ae2.AE2Integration.recoveryServerStopping();
    }

    public static void clearAE2() {
        if (Loader.isModLoaded("appliedenergistics2")) clearAE2Caches();
    }

    @cpw.mods.fml.common.Optional.Method(modid = "appliedenergistics2")
    private static void clearAE2Caches() {
        moze_intel.projecte.integration.ae2.AE2Integration.clear();
    }

	public static void modChecks()
	{
		NEI = Loader.isModLoaded("NotEnoughItems");
        PHC = Loader.isModLoaded("harvestcraft");
        PHN = Loader.isModLoaded("harvestthenether");
        CCC = Loader.isModLoaded("CodeChickenCore");
        EFR = Loader.isModLoaded("etfuturum");
        natura = Loader.isModLoaded("Natura");
        gregtech = Loader.isModLoaded("gregtech");
        forestry = Loader.isModLoaded("Forestry");
		chisel = Loader.isModLoaded("chisel");
		duraDisplay = Loader.isModLoaded("duradisplay");
		avaritia = Loader.isModLoaded("Avaritia");
		botania = Loader.isModLoaded("Botania");
		TConstruct = Loader.isModLoaded("TConstruct");
	}

	public static void init()
	{
		modChecks();

		if (NEI) {
            PELogger.logInfo("Try to integrate with NotEnoughItems");
			try {
				NEIInit.init();
			} catch (NoClassDefFoundError e) {
                NEI = false;
				PELogger.logWarn("NEI integration not loaded due to server side being detected");
			} catch (Throwable e) {
                NEI = false;
				e.printStackTrace();
			}
		}

        if (CCC) {
            PELogger.logInfo("Try to integrate with CodeChicken Core");
            try {
                CCCInit.init();
            } catch (Throwable e) {
                CCC = false;
                e.printStackTrace();
            }
        }

        if (gregtech) {
            PELogger.logInfo("Try to integrate with gregtech");
            try {
                GTMapper.init();
            } catch (NoClassDefFoundError e) {
                gregtech = false;
                PELogger.logWarn("Integration with gregtech failed");
            } catch (Throwable e) {
                gregtech = false;
                e.printStackTrace();
            }
        }

		if (chisel) {
			PELogger.logInfo("Try to integrate with Chisel");
			try {
				ChiselMapper.init();
			} catch (NoClassDefFoundError e) {
				chisel = false;
				PELogger.logWarn("Integration with Chisel failed");
			} catch (Throwable e) {
				chisel = false;
				e.printStackTrace();
			}
		}

		if (duraDisplay) {
			PELogger.logInfo("Try to **hack** DuraDisplay!");
			try {
				DuraDisplayInit.init();
			} catch (Throwable e) {
				duraDisplay = false;
				e.printStackTrace();
				PELogger.logInfo("Cannot **hack** DuraDisplay! I hate it!");
			}
		}

		if (TConstruct) {
			PELogger.logInfo("Try to integrate with Tinkers' Construct");
			try {
				TConstructInit.init();
			} catch (Throwable e) {
				TConstruct = false;
				e.printStackTrace();
			}
		}
	}
}
