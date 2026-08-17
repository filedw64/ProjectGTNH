package moze_intel.projecte.utils;

import cpw.mods.fml.common.FMLCommonHandler;
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
import java.util.UUID;

public class NetworkEmcHelper {

	public static double getNetworkEMC(String uuidStr) {
		if (uuidStr == null || uuidStr.isEmpty()) return 0;
		UUID uuid;
		try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { return 0; }

		MinecraftServer server = MinecraftServer.getServer();
		if (server != null) {
			for (Object obj : server.getConfigurationManager().playerEntityList) {
				EntityPlayerMP player = (EntityPlayerMP) obj;
				if (player.getUniqueID().equals(uuid)) {
					return Transmutation.getEmc(player);
				}
			}
		}

		// 离线玩家处理
		File playerData = new File(DimensionManager.getCurrentSaveRootDirectory(), "playerdata");
		File playerFile = new File(playerData, uuid.toString() + ".dat");
		if (playerFile.exists() && playerFile.isFile()) {
			try {
				NBTTagCompound root = CompressedStreamTools.readCompressed(new FileInputStream(playerFile));
				return root.getCompoundTag(TransmutationProps.PROP_NAME).getDouble("transmutationEmc");
			} catch (Exception e) {
				PELogger.logWarn("无法读取玩家离线EMC数据: " + uuid);
			}
		}
		return 0;
	}

	public static boolean deductNetworkEMC(String uuidStr, double amount) {
		if (uuidStr == null || uuidStr.isEmpty()) return false;
		UUID uuid;
		try { uuid = UUID.fromString(uuidStr); } catch (Exception e) { return false; }

		MinecraftServer server = MinecraftServer.getServer();
		if (server == null) return false;

		// 尝试寻找在线玩家
		for (Object obj : server.getConfigurationManager().playerEntityList) {
			EntityPlayerMP player = (EntityPlayerMP) obj;
			if (player.getUniqueID().equals(uuid)) {
				double currentEmc = Transmutation.getEmc(player);
				if (currentEmc >= amount) {
					Transmutation.setEmc(player, currentEmc - amount);
					Transmutation.sync(player); // 同步给客户端
					return true;
				}
				return false;
			}
		}

		// 离线玩家处理（直接修改 dat 文件）
		File playerData = new File(DimensionManager.getCurrentSaveRootDirectory(), "playerdata");
		File playerFile = new File(playerData, uuid.toString() + ".dat");
		if (playerFile.exists() && playerFile.isFile()) {
			try {
				NBTTagCompound root = CompressedStreamTools.readCompressed(new FileInputStream(playerFile));
				NBTTagCompound props = root.getCompoundTag(TransmutationProps.PROP_NAME);
				double currentEmc = props.getDouble("transmutationEmc");
				if (currentEmc >= amount) {
					props.setDouble("transmutationEmc", currentEmc - amount);
					CompressedStreamTools.writeCompressed(root, new FileOutputStream(playerFile));
					TransmutationOffline.clear(uuid); // 清除缓存，使其下次读取时更新
					return true;
				}
			} catch (Exception e) {
				PELogger.logWarn("无法修改玩家离线EMC数据: " + uuid);
			}
		}
		return false;
	}
}
