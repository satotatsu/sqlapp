/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;

class UniqueListTest {
	@Test
	void bulkAdditionsPreserveOrderAndUniquenessIncludingNull() {
		UniqueList<String> list = new UniqueList<>();
		assertTrue(list.addAll(Arrays.asList("a", "a", null, null, "b")));
		assertEquals(Arrays.asList("a", null, "b"), list);
		assertFalse(list.addAll(list));
		assertTrue(list.addAll(1, Arrays.asList("c", "c", "a", "d")));
		assertEquals(Arrays.asList("a", "c", "d", null, "b"), list);
		assertThrows(IndexOutOfBoundsException.class, () -> list.add(-1, "a"));
	}

	@Test
	void iteratorAndDefaultRemovalKeepMembershipConsistent() {
		UniqueList<String> list = values("a", "b", "c");
		Iterator<String> iterator = list.iterator();
		assertEquals("a", iterator.next());
		iterator.remove();
		assertFalse(list.contains("a"));
		assertTrue(list.add("a"));
		assertTrue(list.removeIf("b"::equals));
		assertFalse(list.contains("b"));
		assertTrue(list.add("b"));
	}

	@Test
	void indexedIteratorTracksPositionAndRejectsDuplicateReplacement() {
		UniqueList<String> list = values("a", "b", "c");
		ListIterator<String> iterator = list.listIterator(1);
		assertEquals("b", iterator.next());
		assertThrows(IllegalArgumentException.class, () -> iterator.set("a"));
		iterator.set("d");
		assertFalse(list.contains("b"));
		iterator.add("a");
		assertEquals(2, iterator.nextIndex());
		iterator.add("e");
		assertEquals("e", iterator.previous());
		iterator.remove();
		assertFalse(list.contains("e"));
		assertEquals(List.of("a", "d", "c"), list);
		assertThrows(IllegalArgumentException.class, () -> list.set(1, "a"));
		assertEquals("a", list.set(0, "a"));
	}

	@Test
	void nestedSublistsApplyUniquenessAcrossTheWholeList() {
		UniqueList<String> list = values("a", "b", "c", "d");
		List<String> sub = list.subList(1, 3);
		assertFalse(sub.add("a"));
		assertTrue(sub.addAll(List.of("e", "e", "a")));
		assertThrows(IllegalArgumentException.class, () -> sub.set(0, "d"));
		ListIterator<String> iterator = sub.listIterator();
		assertEquals("b", iterator.next());
		iterator.set("f");
		assertFalse(list.contains("b"));
		sub.subList(1, 3).clear();
		assertEquals(List.of("a", "f", "d"), list);
		assertFalse(list.contains("c"));
		assertFalse(list.contains("e"));
		sub.clear();
		assertEquals(List.of("a", "d"), list);
		assertFalse(list.contains("f"));
	}

	@Test
	void equalityHashingSortingCloningAndSerializationFollowListSemantics() throws Exception {
		UniqueList<String> list = values("c", "b", "a");
		list.subList(0, 2).sort(null);
		assertEquals(List.of("b", "c", "a"), list);
		list.sort(null);
		List<String> ordinary = new ArrayList<>(List.of("a", "b", "c"));
		assertEquals(ordinary, list);
		assertEquals(list, ordinary);
		assertEquals(ordinary.hashCode(), list.hashCode());
		assertNotEquals(list, new Object());
		assertNotEquals(list, null);
		UniqueList<String> clone = list.clone();
		clone.remove("a");
		assertTrue(list.contains("a"));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(list); }
		try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			UniqueList<?> restored = (UniqueList<?>) input.readObject();
			assertEquals(list, restored);
			assertTrue(restored.contains("a"));
		}
	}

	@Test
	void partitionFunctionRemovalDoesNotInvalidateItsOwnIterator() {
		var function = new com.sqlapp.data.schemas.PartitionFunction("F")
				.setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		var values = function.getValues();
		values.addAll("4", "1", "3", "2");
		assertTrue(values.removeIf(value -> Integer.parseInt(value) % 2 == 0));
		assertEquals(List.of("1", "3"), values);
		assertFalse(values.contains("2"));
		values.subList(0, 1).clear();
		assertEquals(List.of("3"), values);
		assertFalse(values.contains("1"));
	}

	private UniqueList<String> values(String... elements) {
		UniqueList<String> list = new UniqueList<>();
		list.addAll(Arrays.asList(elements));
		return list;
	}
}
