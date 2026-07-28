package moze_intel.projecte.utils;

import moze_intel.projecte.PECore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PELogger
{
	private static final Logger logger = LogManager.getLogger(PECore.MODID);

    public static Logger getLogger() {
        return logger;
    }

    public static void logTrace(String msg)
    {
        logger.trace(msg);
    }

    public static void logDebug(String msg)
    {
        logger.debug(msg);
    }

	public static void logInfo(String msg)
	{
		logger.info(msg);
	}

    public static void logWarn(String msg)
    {
        logger.warn(msg);
    }

    public static void logError(String msg)
    {
        logger.error(msg);
    }

	public static void logFatal(String msg)
	{
		logger.fatal(msg);
	}

    public static void logTrace(String msg, Object... args)
    {
        logger.trace(String.format(msg, args));
    }

    public static void logDebug(String msg, Object... args)
    {
        logger.debug(String.format(msg, args));
    }

	public static void logInfo(String msg, Object... args)
	{
		logger.info(String.format(msg, args));
	}

    public static void logWarn(String msg, Object... args)
    {
        logger.warn(String.format(msg, args));
    }

    public static void logError(String msg, Object... args)
    {
        logger.error(String.format(msg, args));
    }

    public static void logFatal(String msg, Object... args)
    {
        logger.fatal(String.format(msg, args));
    }
}
