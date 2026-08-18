package moze_intel.projecte.utils;

import net.minecraft.item.ItemStack;

// 一个无数组+链表的简易 HashMap<ItemStack, int>
public class StackMap {
	private static final int DEFAULT_CAPACITY = 16;
	private static final int MAXIMUM_CAPACITY = 1048576; // 1 << 20
	private static final float DEFAULT_LOAD_FACTOR = 0.75f;
	private Node[] table;
	private int size;
	private int threshold;
	private final float loadFactor;

	// 静态内部节点（链表）
	private static class Node {
		final int hash;
		final ItemStack key;
		int value;
		Node next;

		Node(ItemStack key, int hash, int value, Node next) {
			this.key = key;
			this.hash = hash;
			this.value = value;
			this.next = next;
		}

		public final ItemStack getKey() { return key; }

		public final int getValue() { return value; }
	}

	private static int tableSizeFor(int cap) {
		int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
		return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
	}

	public StackMap(int capacity, float loadFactor) {
		if (capacity < 0)
			throw new IllegalArgumentException("Illegal initial capacity: " + capacity);
		if (loadFactor <= 0 || loadFactor > 1 || Float.isNaN(loadFactor))
			throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
		int tableSize = tableSizeFor(capacity);
		this.loadFactor = loadFactor;
		table = new Node[tableSize];
		this.threshold = (int) (tableSize * loadFactor);
		size = 0;
	}

	public StackMap(int initialCapacity) {
		this(initialCapacity, DEFAULT_LOAD_FACTOR);
	}

	public StackMap() {
		this(DEFAULT_CAPACITY, DEFAULT_CAPACITY);
	}

	// 对 ItemStack 实现的 hashCode
	public static int hashCode(ItemStack stack) {
		int nbtHash = (stack.stackTagCompound == null || stack.stackTagCompound.hasNoTags()) ? 0 : stack.stackTagCompound.hashCode();
		return (nbtHash * 31 + stack.getItem().hashCode()) * 31 + stack.getItemDamage();
	}

	public static boolean equals(ItemStack a, ItemStack b) {
		return ItemHelper.areItemStacksEqual(a, b);
	}

	// 仿照 JDK 实现的扰动函数
	private static int hash(ItemStack stack) {
		int h;
		return (stack == null || stack.getItem() == null) ? 0 : (h = hashCode(stack)) ^ (h >>> 16);
	}

	private int indexFor(int hash) {
		return hash & (table.length - 1);
	}

	private int indexFor(int hash, int length) {
		return hash & (length - 1);
	}

	// 扩容实现
	private void resize() {
		int oldCapacity = table.length;
		if (oldCapacity >= MAXIMUM_CAPACITY) {
			threshold = Integer.MAX_VALUE; // 不再扩容
			return;
		}
		int newCapacity = oldCapacity << 1;
		threshold = (int) (newCapacity * loadFactor); // 更新 threshold
		Node[] newTable = new Node[newCapacity];
		// 遍历旧数组的所有链表
		for (int i = 0, tableLength = table.length; i < tableLength; i++) {
			Node curr = table[i];
			while (curr != null) {
				Node next = curr.next; // 保存下一个节点，防止断链
				int idx = indexFor(curr.hash, newCapacity); // 重新计算在新数组中的索引
				curr.next = newTable[idx]; // 头插法迁移到新数组
				newTable[idx] = curr;
				curr = next;
			}
		}
		table = newTable; // 替换为扩容后的新数组
	}

	public void put(ItemStack key, int value) {
		// 如果当前容量已到界限，触发扩容
		if (size >= threshold)
			resize();

		int hash = hash(key);
		int index = indexFor(hash);
		Node head = table[index];

		// 遍历链表，如果 Key 已存在则覆盖
		Node curr = head;
		while (curr != null) {
			if (hash == curr.hash && equals(key, curr.key)) {
				curr.value = value; // 覆盖旧值
				return;
			}
			curr = curr.next;
		}

		// 头插法
		table[index] = new Node(key, hash, value, head);
		size++;
	}

	public int get(ItemStack key) {
		return getOrDefault(key, 0);
	}

	public int getOrDefault(ItemStack key, int defaultValue) {
		int hash = hash(key);
		int index = indexFor(hash);
		Node curr = table[index];
		while (curr != null) {
			if (hash == curr.hash && equals(key, curr.key))
				return curr.value;
			curr = curr.next;
		}
		return defaultValue;
	}

	public boolean containsKey(ItemStack key) {
		int hash = hash(key);
		int index = indexFor(hash);
		Node curr = table[index];

		while (curr != null) {
			if (hash == curr.hash && equals(key, curr.key))
				return true;
			curr = curr.next;
		}
		return false;
	}

	public boolean remove(ItemStack key) {
		int hash = hash(key);
		int index = indexFor(hash);
		Node curr = table[index];
		Node prev = null;

		while (curr != null) {
			if (hash == curr.hash && equals(key, curr.key)) {
				if (prev == null)
					table[index] = curr.next;
				else prev.next = curr.next;
				size--;
				return true;
			}
			prev = curr;
			curr = curr.next;
		}
		return false; // 未找到
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}
}
