package projecte.emc.pregenerated;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import projecte.emc.NormalizedSimpleStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public class PregeneratedEMC
{
	static final Gson gson =  new GsonBuilder().registerTypeAdapter(NormalizedSimpleStack.class, new NSSJsonTypeAdapter().nullSafe()).enableComplexMapKeySerialization().setPrettyPrinting().create();

	public static boolean tryRead(File f, Map<NormalizedSimpleStack, Double> map)
	{
		try {
			Map<NormalizedSimpleStack, Double> m = read(f);
			map.clear();
			map.putAll(m);
			return true;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Map<NormalizedSimpleStack, Double> read(File file) throws IOException
	{
		Type type = new TypeToken<Map<NormalizedSimpleStack, Double>>() {}.getType();
		FileReader reader = new FileReader(file);
		Map<NormalizedSimpleStack, Double> map = gson.fromJson(reader, type);
		reader.close();
		map.remove(null);
		return map;
	}

	public static void write(File file, Map<NormalizedSimpleStack, Double> map) throws IOException
	{
		Type type = new TypeToken<Map<NormalizedSimpleStack, Double>>() {}.getType();
		FileWriter writer = new FileWriter(file);
		gson.toJson(map, type, writer);
		writer.close();
	}
}
