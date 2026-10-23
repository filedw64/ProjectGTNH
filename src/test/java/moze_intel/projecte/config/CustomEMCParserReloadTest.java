package moze_intel.projecte.config;

import moze_intel.projecte.emc.NormalizedSimpleStack;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CustomEMCParserReloadTest
{
	private void reject(String... lines) throws Exception {
		Field pathField = CustomEMCParser.class.getDeclaredField("CONFIG_PATH");
		Field loadedField = CustomEMCParser.class.getDeclaredField("loaded");
		pathField.setAccessible(true);
		loadedField.setAccessible(true);
		Object oldPath = pathField.get(null);
		boolean oldLoaded = loadedField.getBoolean(null);
		Map<NormalizedSimpleStack, Double> oldValues = CustomEMCParser.userValues;
		Path file = Files.createTempFile("projecte-emc-reload", ".cfg");
		try {
			Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
			pathField.set(null, file);
			loadedField.setBoolean(null, true);
			Map<NormalizedSimpleStack, Double> sentinel = new HashMap<>();
			sentinel.put(null, 123.0);
			CustomEMCParser.userValues = sentinel;
			try {
				CustomEMCParser.readUserData();
				fail("Invalid file must not be committed");
			}
			catch (IllegalArgumentException expected) {
				assertSame(sentinel, CustomEMCParser.userValues);
				assertEquals(Double.valueOf(123), sentinel.get(null));
			}
		}
		finally {
			pathField.set(null, oldPath);
			loadedField.setBoolean(null, oldLoaded);
			CustomEMCParser.userValues = oldValues;
			Files.deleteIfExists(file);
		}
	}

	@Test public void rejectsIncompleteEntryBeforeNextEntry() throws Exception {
		reject("#0.2", "S:minecraft:stone", "M:0", "S:minecraft:dirt", "M:0", "E:12");
	}
	@Test public void rejectsTruncatedEntry() throws Exception {
		reject("#0.2", "S:minecraft:stone", "M:0");
	}
	@Test public void rejectsNonFiniteValue() throws Exception {
		reject("#0.2", "S:minecraft:stone", "M:0", "E:NaN");
		reject("#0.2", "S:minecraft:stone", "M:0", "E:Infinity");
	}
	@Test public void rejectsBadNumber() throws Exception {
		reject("#0.2", "S:minecraft:stone", "M:invalid", "E:10");
	}
	@Test public void commandFailureRestoresExactBytes() throws Exception { transaction(false, false, false); }
	@Test public void commandExceptionRestoresExactBytes() throws Exception { transaction(false, true, false); }
	@Test public void commandSuccessKeepsNewBytes() throws Exception { transaction(true, false, false); }
	@Test public void commandRollbackPreservesExternalEdit() throws Exception { transaction(false, false, true); }

	private void transaction(boolean success, boolean throwsException, boolean external) throws Exception {
		Field pathField = CustomEMCParser.class.getDeclaredField("CONFIG_PATH");
		pathField.setAccessible(true);
		Object oldPath = pathField.get(null);
		Path file = Files.createTempFile("projecte-emc-transaction", ".cfg");
		byte[] before = "#0.2\r\n# exact original\r\n".getBytes(StandardCharsets.UTF_8);
		byte[] after = "#0.2\n# command\n".getBytes(StandardCharsets.UTF_8);
		byte[] concurrent = "#0.2\n# external\n".getBytes(StandardCharsets.UTF_8);
		try {
			Files.write(file, before);
			pathField.set(null, file);
			CustomEMCParser.UpdateResult result = CustomEMCParser.updateAndApply(() -> {
				try { Files.write(file, after); return true; }
				catch (java.io.IOException e) { throw new IllegalStateException(e); }
			}, () -> {
				if (external) {
					try { Files.write(file, concurrent); }
					catch (java.io.IOException e) { throw new IllegalStateException(e); }
				}
				if (throwsException) throw new IllegalStateException("Injected application failure");
				return success;
			});
			assertEquals(external ? CustomEMCParser.UpdateResult.RESTORE_FAILED : success
				? CustomEMCParser.UpdateResult.APPLIED : CustomEMCParser.UpdateResult.ROLLED_BACK, result);
			assertArrayEquals(external ? concurrent : success ? after : before, Files.readAllBytes(file));
		}
		finally { pathField.set(null, oldPath); Files.deleteIfExists(file); }
	}

	@Test public void rejectsMissingHeader() throws Exception {
		reject("S:minecraft:stone", "M:0", "E:10");
	}
}
