package moze_intel.projecte.emc.mappers.customConversions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.io.IOUtils;
import moze_intel.projecte.PECore;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.emc.collector.IMappingCollector;
import moze_intel.projecte.emc.mappers.IEMCMapper;
import moze_intel.projecte.emc.mappers.customConversions.json.ConversionGroup;
import moze_intel.projecte.emc.mappers.customConversions.json.CustomConversion;
import moze_intel.projecte.emc.mappers.customConversions.json.CustomConversionDeserializer;
import moze_intel.projecte.emc.mappers.customConversions.json.CustomConversionFile;
import moze_intel.projecte.emc.mappers.customConversions.json.FixedValues;
import moze_intel.projecte.emc.mappers.customConversions.json.FixedValuesDeserializer;
import moze_intel.projecte.utils.PELogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class CustomConversionMapper implements IEMCMapper<NormalizedSimpleStack, Double>
{
	public static final ImmutableList<String> defaultfilenames = ImmutableList.of("metals", "example", "ODdefaults");

	// 将 Gson 实例提取为静态常量
	private static final Gson GSON;
	static {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(CustomConversion.class, new CustomConversionDeserializer());
		builder.registerTypeAdapter(FixedValues.class, new FixedValuesDeserializer());
		GSON = builder.create();
	}

	@Override
	public String getName()
	{
		return "CustomConversionMapper";
	}

	@Override
	public String getDescription()
	{
		return "";
	}

	@Override
	public boolean isAvailable()
	{
		return true;
	}

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Double> mapper, Configuration config)
	{
		File customConversionFolder = getCustomConversionFolder();
		if (customConversionFolder.isDirectory() || customConversionFolder.mkdir()) {
			if (config.getBoolean("writeDefaultFiles", "", true, "Create the default files if they are not present, yet. Will not overwrite them, only create them when they are not present."))
			{
				tryToWriteDefaultFiles();
			}
			File[] files = customConversionFolder.listFiles();
			if (files != null) {
				for (File f: files) {
					if (f.isFile() && f.canRead() && f.getName().toLowerCase().endsWith(".json")) {
						if (config.getBoolean(f.getName().substring(0, f.getName().length() - 5), "", true, String.format("Read file: %s?", f.getName()))) {
							// 使用 try-with-resources 自动关闭 FileReader，修复可能的文件句柄泄漏漏洞
							try (FileReader reader = new FileReader(f)) {
								addMappingsFromFile(reader, mapper);
								PELogger.logInfo("Collected Mappings from " + f.getName());
							} catch (Exception e) {
								PELogger.logFatal("Exception when reading file: " + f);
								e.printStackTrace();
							}
						}
					}
				}
			}
		} else {
			PELogger.logFatal("COULD NOT CREATE customConversions FOLDER IN config/ProjectE");
		}
	}

	public static File getCustomConversionFolder()
	{
		return new File(PECore.CONFIG_DIR, "customConversions");
	}

	public static void addMappingsFromFile(Reader json, IMappingCollector<NormalizedSimpleStack, Double> mapper) {
		addMappingsFromFile(parseJson(json), mapper);
	}

	public static void addMappingsFromFile(CustomConversionFile file, IMappingCollector<NormalizedSimpleStack, Double> mapper) {
		Map<String, NormalizedSimpleStack> fakes = new HashMap<>();

		for (Map.Entry<String, ConversionGroup> entry : file.groups.entrySet())
		{
			PELogger.logDebug(String.format("Adding conversions from group '%s' with comment '%s'", entry.getKey(), entry.getValue().comment));
			try
			{
				for (CustomConversion conversion : entry.getValue().conversions)
				{
					NormalizedSimpleStack output = getNSSfromJsonString(conversion.output, fakes);
					if (output == null) continue;
					mapper.addConversion(conversion.count, output, convertToNSSMap(conversion.ingredients, fakes));
				}
			} catch (Exception e) {
				PELogger.logFatal(String.format("ERROR reading custom conversion from group %s!", entry.getKey()));
				e.printStackTrace();
			}
		}

		try
		{
			if (file.values != null)
			{
				if (file.values.setValueBefore != null) {
					for (Map.Entry<String, Double> entry : file.values.setValueBefore.entrySet())
					{
						NormalizedSimpleStack something = getNSSfromJsonString(entry.getKey(), fakes);
						mapper.setValueBefore(something, entry.getValue());
						if (something instanceof NormalizedSimpleStack.NSSOreDictionary nssOD)
						{
							for (ItemStack itemStack : OreDictionary.getOres(nssOD.od))
							{
								mapper.setValueBefore(NormalizedSimpleStack.forItem(itemStack), entry.getValue());
							}
						}
					}
				}
				if (file.values.setValueAfter != null)
				{
					for (Map.Entry<String, Double> entry : file.values.setValueAfter.entrySet())
					{
						NormalizedSimpleStack something = getNSSfromJsonString(entry.getKey(), fakes);
						mapper.setValueAfter(something, entry.getValue());
						if (something instanceof NormalizedSimpleStack.NSSOreDictionary nssOD)
						{
							for (ItemStack itemStack : OreDictionary.getOres(nssOD.od))
							{
								mapper.setValueAfter(NormalizedSimpleStack.forItem(itemStack), entry.getValue());
							}
						}
					}
				}
				if (file.values.conversion != null)
				{
					for (CustomConversion conversion : file.values.conversion)
					{
						NormalizedSimpleStack out = getNSSfromJsonString(conversion.output, fakes);
						if (conversion.evalOD && out instanceof NormalizedSimpleStack.NSSOreDictionary nssOD)
						{
							for (ItemStack itemStack : OreDictionary.getOres(nssOD.od))
							{
								mapper.setValueFromConversion(conversion.count, NormalizedSimpleStack.forItem(itemStack), convertToNSSMap(conversion.ingredients, fakes));
							}
						}
						mapper.setValueFromConversion(conversion.count, out, convertToNSSMap(conversion.ingredients, fakes));
					}
				}
			}
		} catch (Exception e) {
			PELogger.logFatal("ERROR reading custom conversion values!");
			e.printStackTrace();
		}
	}

	private static NormalizedSimpleStack getNSSfromJsonString(String s, Map<String, NormalizedSimpleStack> fakes) {
		if (s.startsWith("OD|")) {
			return NormalizedSimpleStack.forOreDictionary(s.substring(3));
		}
		else if (s.startsWith("FAKE|")) {
			String fakeIdentifier = s.substring(5);
			// 减少哈希查询
			return fakes.computeIfAbsent(fakeIdentifier, NormalizedSimpleStack::forFake);
		}
		else if (s.startsWith("FLUID|")) {
			String fluidName = s.substring("FLUID|".length());
			Fluid fluid = FluidRegistry.getFluid(fluidName);
			if (fluid == null) return null;
			return NormalizedSimpleStack.forFluid(fluid);
		}
		else {
			return NormalizedSimpleStack.fromJson(s);
		}
	}

	private static<V> Map<NormalizedSimpleStack, V> convertToNSSMap(Map<String, V> m, Map<String, NormalizedSimpleStack> fakes) throws Exception{
		// 预先分配 Map 容量
		Map<NormalizedSimpleStack, V> out = new HashMap<>(m.size());
		for (Map.Entry<String, V> e: m.entrySet()) {
			NormalizedSimpleStack nssItem = getNSSfromJsonString(e.getKey(), fakes);
			if (nssItem != null) {
				out.put(nssItem, e.getValue());
			}
		}
		return out;
	}

	public static CustomConversionFile parseJson(Reader json) {
		// 复用全局静态 GSON 实例
		return GSON.fromJson(json, CustomConversionFile.class);
	}

	public static void tryToWriteDefaultFiles() {
		for (String filename: defaultfilenames) {
			writeDefaultFile(filename);
		}
	}

	private static void writeDefaultFile(String filename) {
		File customConversionFolder = getCustomConversionFolder();
		File f = new File(customConversionFolder, filename + ".json");
		if (f.exists()) {
			return;
		}
		try {
			if (f.createNewFile() && f.canWrite())
			{
				// 使用 try-with-resources 自动关闭 InputStream 和 OutputStream
				// 防止 IOUtils.copy 抛出异常时导致文件流泄漏
				try (InputStream stream = CustomConversionMapper.class.getClassLoader().getResourceAsStream("defaultCustomConversions/" + filename + ".json");
					 OutputStream outputStream = new FileOutputStream(f))
				{
					if (stream != null) {
						IOUtils.copy(stream, outputStream);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
