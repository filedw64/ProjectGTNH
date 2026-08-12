package moze_intel.projecte.config;

import com.google.common.collect.Maps;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.PECore;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomEMCParser
{
	private static final String VERSION = "#0.2";
	private static File CONFIG;
	private static Path CONFIG_PATH;
	private static boolean loaded;

	public static Map<NormalizedSimpleStack, Double> userValues = Maps.newHashMap();

	public static void init()
	{
		CONFIG = new File(PECore.CONFIG_DIR, "custom_emc.cfg");
		CONFIG_PATH = CONFIG.toPath();
		loaded = false;

		try
		{
			if (!CONFIG.exists())
			{
				if (CONFIG.createNewFile())
				{
					writeDefaultFile();
					loaded = true;
				}
			}
			else
			{
				// 用 NIO Files 一次性读取
				List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
				if (lines.isEmpty() || !lines.get(0).equals(VERSION))
				{
					PELogger.logFatal("Found old custom EMC file: resetting.");
					writeDefaultFile();
				}
				loaded = true;
			}
		}
		catch (IOException e)
		{
			PELogger.logFatal("Exception in file I/O: couldn't create custom configuration files.");
			e.printStackTrace();
		}
	}

	public static void readUserData()
	{
		if (!loaded)
		{
			PELogger.logFatal("ERROR: configurations files are not loaded!");
			return;
		}

		userValues.clear();
		try
		{
			List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
			int i = 0;

			while (i < lines.size())
			{
				String line = lines.get(i).trim();
				i++;

				if (line.isEmpty() || line.length() < 3 || line.charAt(0) == '#' || line.charAt(1) != ':') continue;

				if (line.charAt(0) == 'S')
				{
					String name = line.substring(2);
					int meta = -1;
					double emc = -1;

					// 向下探查 Meta 和 EMC
					while (i < lines.size())
					{
						String nextLine = lines.get(i).trim();
						if (nextLine.startsWith("M:")) {
							meta = Integer.parseInt(nextLine.substring(2));
						} else if (nextLine.startsWith("E:")) {
							emc = Double.parseDouble(nextLine.substring(2));
							i++;
							break; // 找到了 EMC，这个 Entry 结束
						}
						i++;
					}

					if (emc == -1) continue; // 格式错误？

					if (name.contains(":"))
					{
						ItemStack stack = ItemHelper.getStackFromString(name, meta);
						if (stack == null)
						{
							PELogger.logFatal("Error in custom EMC file: couldn't find item: " + name);
							continue;
						}

						if (emc <= 0) PELogger.logInfo("Removed " + name + " from EMC mapping");
						else PELogger.logInfo("Registered custom EMC for: " + name + "(" + emc + ")");

						userValues.put(NormalizedSimpleStack.forItem(stack), Math.max(emc, 0.0));
					}
					else
					{
						// 合并 Oredictionary 的获取操作
						List<ItemStack> odItems = ItemHelper.getODItems(name);
						if (odItems.isEmpty())
						{
							PELogger.logFatal("Error in custom EMC file: no OD entry for " + name);
							continue;
						}

						if (emc <= 0) PELogger.logInfo("Removed " + name + " from EMC mapping");
						else PELogger.logInfo("Registered custom EMC for: " + name + "(" + emc + ")");

						for (ItemStack stack : odItems)
						{
							userValues.put(NormalizedSimpleStack.forItem(stack), Math.max(emc, 0.0));
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static boolean addToFile(String toAdd, int meta, double emc)
	{
		if (!loaded) return false;

		try
		{
			// 优化 3：只读一次文件进内存，直接在内存 List 中进行状态机查找和修改，彻底消除 O(2N) 的双重 IO 灾难
			List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
			boolean isOD = !toAdd.contains(":");
			boolean found = false;

			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i).trim();
				if (line.startsWith("S:") && line.substring(2).equals(toAdd))
				{
					int eIndex = -1;
					boolean metaMatches = isOD; // 如果是 OD，直接认为 Meta 匹配

					// 往下找 M: 和 E:
					for (int j = i + 1; j < lines.size(); j++) {
						String subLine = lines.get(j).trim();
						if (subLine.startsWith("M:") && !isOD) {
							if (Integer.parseInt(subLine.substring(2)) == meta) {
								metaMatches = true;
							}
						} else if (subLine.startsWith("E:")) {
							eIndex = j;
							break; // 找到当前 Entry 的 E 行
						} else if (subLine.startsWith("S:")) {
							break; // 越界到了下一个 Entry
						}
					}

					if (metaMatches && eIndex != -1) {
						lines.set(eIndex, "E:" + emc);
						found = true;
						break;
					}
				}
			}

			if (!found)
			{
				lines.add("");
				lines.add("S:" + toAdd);
				if (!isOD) lines.add("M:" + meta);
				lines.add("E:" + emc);
			}

			// 一次性写回
			Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
			return true;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public static boolean removeFromFile(String toRemove, int meta)
	{
		if (!loaded) return false;

		try
		{
			List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
			boolean isOD = !toRemove.contains(":");
			boolean removed = false;

			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i).trim();
				if (line.startsWith("S:") && line.substring(2).equals(toRemove))
				{
					int sIndex = i;
					int mIndex = -1;
					int eIndex = -1;
					boolean metaMatches = isOD;

					for (int j = i + 1; j < lines.size(); j++) {
						String subLine = lines.get(j).trim();
						if (subLine.startsWith("M:") && !isOD) {
							mIndex = j;
							if (Integer.parseInt(subLine.substring(2)) == meta) {
								metaMatches = true;
							}
						} else if (subLine.startsWith("E:")) {
							eIndex = j;
							break;
						} else if (subLine.startsWith("S:")) {
							break;
						}
					}

					if (metaMatches && eIndex != -1) {
						// 从后往前删
						lines.remove(eIndex);
						if (mIndex != -1) lines.remove(mIndex);
						lines.remove(sIndex);
						removed = true;
						break;
					}
				}
			}

			if (removed)
			{
				Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
			}
			return removed;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	private static void writeDefaultFile()
	{
		List<String> lines = new ArrayList<>();
		lines.add(VERSION);
		lines.add("Custom EMC file");
		lines.add("This file is used for custom EMC registration, it is recommended that you do not modify it manually.");
		lines.add("In game commands are avaliable to set custom values. Type /projecte in game for usage info.");

		try {
			Files.write(CONFIG_PATH, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
