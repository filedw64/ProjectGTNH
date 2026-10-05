package moze_intel.projecte.utils;

import cpw.mods.fml.common.FMLCommonHandler;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.playerData.TransmutationOffline;
import moze_intel.projecte.playerData.TransmutationProps;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class NetworkEmcHelper {
    private static UUID parse(String text) {
        try { return text == null ? null : UUID.fromString(text); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    private static EntityPlayerMP online(UUID uuid) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (uuid.equals(player.getUniqueID())) return player;
        }
        return null;
    }
    private static File playerFile(UUID uuid) {
        return new File(new File(DimensionManager.getCurrentSaveRootDirectory(), "playerdata"), uuid + ".dat");
    }
    public static double getNetworkEMC(String uuidStr) {
        return getNetworkEMCExact(uuidStr).toLegacyDouble();
    }
    public static ExactEMC getNetworkEMCExact(String uuidStr) {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        UUID uuid = parse(uuidStr);
        if (uuid == null) return ExactEMC.ZERO;
        EntityPlayerMP player = online(uuid);
        if (player != null) return Transmutation.getEmcExact(player);
        File file = playerFile(uuid);
        if (!file.isFile()) return ExactEMC.ZERO;
        try (FileInputStream stream = new FileInputStream(file)) {
            return ExactEMCCodec.readBalance(CompressedStreamTools.readCompressed(stream)
                .getCompoundTag(TransmutationProps.PROP_NAME));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read offline EMC: " + uuid, error);
        }
    }
    public static boolean deductNetworkEMC(String uuidStr, double amount) {
        return deductNetworkEMCExact(uuidStr, ExactEMC.fromLegacyDouble(amount));
    }
    public static boolean deductNetworkEMCExact(String uuidStr, ExactEMC amount) {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        ExactEMCCodec.validateBalance(amount);
        UUID uuid = parse(uuidStr);
        if (uuid == null || MinecraftServer.getServer() == null) return false;
        EntityPlayerMP player = online(uuid);
        if (player != null) return Transmutation.tryRemoveEmcExact(player, amount);
        File file = playerFile(uuid);
        if (!file.isFile()) return false;
        // Same server thread as login/save. Preserve original .dat until complete replacement.
        java.nio.file.Path temporary = null;
        try {
            NBTTagCompound root;
            try (FileInputStream stream = new FileInputStream(file)) {
                root = CompressedStreamTools.readCompressed(stream);
            }
            NBTTagCompound props = root.getCompoundTag(TransmutationProps.PROP_NAME);
            ExactEMC current = ExactEMCCodec.readBalance(props);
            if (current.compareTo(amount) < 0) return false;
            ExactEMCCodec.writeBalance(props, current.subtract(amount));
            root.setTag(TransmutationProps.PROP_NAME, props);
            temporary = Files.createTempFile(file.toPath().getParent(), "projecte-emc-", ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                CompressedStreamTools.writeCompressed(root, stream);
            }
            Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            TransmutationOffline.clear(uuid);
            return true;
        } catch (java.io.IOException error) {
            PELogger.logWarn("Cannot safely update offline EMC for " + uuid + ": " + error);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (java.io.IOException ignored) {}
            }
        }
    }
}
