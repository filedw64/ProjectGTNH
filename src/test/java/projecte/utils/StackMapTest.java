package projecte.utils;

import moze_intel.projecte.utils.StackMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StackMapTest {
	public StackMap map;

	@Before
	public void setup() {
		map = new StackMap();
	}

	@Test
	public void testDeduplication() {
		// 去重测试
		for (int i = 0; i < 16; i++)
			for (int j = 0; j < 16; j++)
				map.put(new ItemStack(Blocks.wool, i + j, (i + j) % 16), 1);
		assertEquals(16, map.size());
	}
}
