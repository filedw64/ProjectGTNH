package moze_intel.projecte.integration;

import cpw.mods.fml.common.Loader;
import moze_intel.projecte.integration.GregTech.GTMapper;
import moze_intel.projecte.integration.MineTweaker.TweakInit;
import moze_intel.projecte.integration.NEI.NEIInit;
import moze_intel.projecte.utils.PELogger;

// Single class to initiate different mod compatibilities. Idea came from Avaritia by SpitefulFox
public class Integration
{
	public static boolean mtweak = false, NEI = false,
        PHC = false, PHN = false, CCC = false, EFR = false,
        natura = false, gregtech = false, IC2 = false;

	public static void modChecks()
	{
		mtweak = Loader.isModLoaded("MineTweaker3");
		NEI = Loader.isModLoaded("NotEnoughItems");
        PHC = Loader.isModLoaded("harvestcraft");
        PHN = Loader.isModLoaded("harvestthenether");
        CCC = Loader.isModLoaded("CodeChickenCore");
        EFR = Loader.isModLoaded("etfuturum");
        natura = Loader.isModLoaded("Natura");
        gregtech = Loader.isModLoaded("gregtech");
        IC2 = Loader.isModLoaded("IC2");
	}

	public static void init()
	{
		modChecks();

		if (mtweak) {
            PELogger.logInfo("Try to integrate with MineTweaker 3");
			try {
				TweakInit.init();
			} catch (Throwable e) {
                mtweak = false;
				e.printStackTrace();
			}
		}

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
                PELogger.logWarn("Integration with gregtech failed due to gregtech version below 5.09.51.482");
            } catch (Throwable e) {
                gregtech = false;
                e.printStackTrace();
            }
        }

        if (IC2) {
            PELogger.logInfo("Try to integrate with IndustrialCraft 2");
            try {
                IC2Init.init();
            } catch (Throwable e) {
                IC2 = false;
                e.printStackTrace();
            }
        }
	}
}
