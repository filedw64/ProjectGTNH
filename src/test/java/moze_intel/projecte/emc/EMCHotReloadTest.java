package moze_intel.projecte.emc;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;

public class EMCHotReloadTest
{
	@Test public void detectsSameSizeSameTimestampReplacement() throws Exception {
		Field field = EMCHotReload.class.getDeclaredField("configFile");
		field.setAccessible(true);
		Object previous = field.get(null);
		Method fingerprint = EMCHotReload.class.getDeclaredMethod("fingerprint");
		fingerprint.setAccessible(true);
		Path path = Files.createTempFile("projecte-emc-watch", ".cfg");
		try {
			field.set(null, path.toFile());
			Files.write(path, "E:100".getBytes(StandardCharsets.UTF_8));
			FileTime time = Files.getLastModifiedTime(path);
			byte[] before = (byte[]) fingerprint.invoke(null);
			Files.write(path, "E:200".getBytes(StandardCharsets.UTF_8));
			Files.setLastModifiedTime(path, time);
			assertFalse(Arrays.equals(before, (byte[]) fingerprint.invoke(null)));
		}
		finally {
			field.set(null, previous);
			Files.deleteIfExists(path);
		}
	}
}
