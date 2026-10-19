package moze_intel.projecte.emc;

import moze_intel.projecte.PECore;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.handlers.TileEntityHandler;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.utils.PELogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Server-tick watcher for custom_emc.cfg. Content fingerprints also detect same-size, same-mtime edits. */
public final class EMCHotReload
{
	private static final int CHECK_INTERVAL = 20;
	private static final int STABLE_TICKS = 20;
	private static File configFile;
	private static byte[] observed;
	private static byte[] handled;
	private static int stableTicks;
	private static int tickCounter;

	private EMCHotReload() {}

	public static void init()
	{
		configFile = new File(PECore.CONFIG_DIR, "custom_emc.cfg");
		tickCounter = 0;
		noteSelfWrite();
		PELogger.logDebug("Watching %s for external EMC changes (autoReloadEMC=%s).", configFile, ProjectEConfig.emcAutoReload);
	}

	public static void shutdown()
	{
		configFile = null;
		observed = null;
		handled = null;
		stableTicks = 0;
		tickCounter = 0;
	}

	/** Called only for actual command writes, never after reading an externally edited file. */
	public static void noteSelfWrite()
	{
		if (configFile == null) return;
		try {
			observed = fingerprint();
			handled = observed;
			stableTicks = 0;
		}
		catch (IOException e) {
			// Do not acknowledge a file state we could not read.
			observed = null;
		}
	}

	public static void tick()
	{
		if (configFile == null || !ProjectEConfig.emcAutoReload) return;
		if (++tickCounter < CHECK_INTERVAL) return;
		tickCounter = 0;
		byte[] current;
		try {
			current = fingerprint();
		}
		catch (IOException e) {
			observed = null;
			stableTicks = 0;
			return; // Wait for an editor's atomic replacement to finish.
		}
		if (!Arrays.equals(current, observed)) {
			observed = current;
			stableTicks = 0;
			return;
		}
		if (Arrays.equals(current, handled)) return;
		stableTicks += CHECK_INTERVAL;
		if (stableTicks < STABLE_TICKS) return;
		stableTicks = 0;
		handled = current; // A failed content state is attempted only once.
		boolean applied = EMCMapper.reloadCustomEmc();
		if (!applied) {
			notifyOperators("pe.command.emc.external_failed");
			PELogger.logError("[failed] Could not apply the external changes in %s; previous EMC values retained. Fix the file and save different content to retry.", configFile.getName());
			return;
		}
		// A concurrent external edit remains unacknowledged and is picked up by subsequent ticks.
		try {
			TileEntityHandler.checkAllCondensers();
			PacketHandler.sendFragmentedEmcPacketToAll();
			PELogger.logInfo("Applied external changes to %s.", configFile.getName());
		}
		catch (RuntimeException e) {
			PELogger.logError("EMC values were applied, but condenser/client refresh failed: %s", e);
		}
	}

	private static void notifyOperators(String key)
	{
		net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
		if (server == null || server.getConfigurationManager() == null) return;
		for (Object entry : server.getConfigurationManager().playerEntityList) {
			net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) entry;
			if (player.canCommandSenderUseCommand(4, "projecte"))
				player.addChatMessage(new net.minecraft.util.ChatComponentTranslation(key));
		}
	}

	private static byte[] fingerprint() throws IOException
	{
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(configFile.toPath())) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
			}
			return digest.digest();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
