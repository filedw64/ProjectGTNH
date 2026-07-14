package moze_intel.projecte.integration;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.integration.MineTweaker.TweakInit;
import moze_intel.projecte.integration.NEI.NEIInit;
import moze_intel.projecte.utils.PELogger;

// Single class to initiate different mod compatibilities. Idea came from Avaritia by SpitefulFox
public class Integration
{
	public static boolean mtweak = false, NEI = false,
        PHC = false, PHN = false, CCC = false, EFR = false;

	public static void modChecks()
	{
		mtweak = Loader.isModLoaded("MineTweaker3");
		NEI = Loader.isModLoaded("NotEnoughItems");
        PHC = Loader.isModLoaded("harvestcraft");
        PHN = Loader.isModLoaded("harvestthenether");
        CCC = Loader.isModLoaded("CodeChickenCore");
        EFR = Loader.isModLoaded("etfuturum");
	}

	public static void init()
	{
		modChecks();

		if (mtweak) {
            PELogger.logInfo("Try to integrate with MineTweaker 3");
			try {
				TweakInit.init();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		if (NEI) {
            PELogger.logInfo("Try to integrate with NotEnoughItems");
			try {
				NEIInit.init();
			} catch (NoClassDefFoundError e) {
				PELogger.logWarn("NEI integration not loaded due to server side being detected");
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

        if (PHC) {
            PELogger.logInfo("Try to integrate with Pam's HarvestCraft");
            try {
                PHCInit.init();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (CCC) {
            PELogger.logInfo("Try to integrate with CodeChicken Core");
            try {
                CCCInit.init();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (EFR) {
            PELogger.logInfo("Try to integrate with Et Futurum Requiem");
            try {
                EFRInit.init();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
	}
}
