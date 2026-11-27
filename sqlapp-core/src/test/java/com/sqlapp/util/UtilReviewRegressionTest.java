package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class UtilReviewRegressionTest {

	@Test
	void readsProcessOutputEvenWhenStreamIsNotReady() throws Exception {
		var input = new ByteArrayInputStream("first\nsecond".getBytes(StandardCharsets.UTF_8)) {
			@Override
			public synchronized int available() {
				return 0;
			}
		};
		var reader = new RuntimeExec.StreamReaderThread(input, "UTF-8");
		reader.start();
		reader.join(5000);
		assertFalse(reader.isAlive());
		assertEquals(List.of("first", "second"), reader.getResult());
	}

	@Test
	void reportsProcessOutputReadFailures() throws Exception {
		IOException failure = new IOException("read failed");
		var reader = new RuntimeExec.StreamReaderThread(new InputStream() {
			@Override
			public int read() throws IOException {
				throw failure;
			}
		});
		reader.start();
		reader.join(5000);
		assertFalse(reader.isAlive());
		assertSame(failure, assertThrows(RuntimeException.class, reader::getResult).getCause());
	}

	@Test
	void clonesOwnTheirNestedMapStructure() {
		var original = new DoubleKeyMap<String, String, Object>();
		Object value = new Object();
		original.put("a", "b", value);
		original.put(null, null, value);
		var clone = original.clone();
		assertEquals(original, clone);
		assertSame(value, clone.get("a", "b"));
		clone.put("a", "b", "changed");
		clone.remove(null, null);
		clone.put("a", "c", "added");
		assertSame(value, original.get("a", "b"));
		assertSame(value, original.get(null, null));
		assertFalse(original.containsKey("a", "c"));
		clone.clear("a");
		assertSame(value, original.get("a", "b"));

		var triple = new TripleKeyMap<String, String, String, Object>();
		triple.put("a", "b", "c", value);
		triple.clone().remove("a", "b", "c");
		assertSame(value, triple.get("a", "b", "c"));
		var quad = new QuadKeyMap<String, String, String, String, Object>();
		quad.put("a", "b", "c", "d", value);
		quad.clone().put("a", "b", "c", "d", "changed");
		assertSame(value, quad.get("a", "b", "c", "d"));
	}

	@Test
	void mapViewsKeepLookupConsistent() {
		List<Supplier<Map<String, String>>> factories = List.of(CaseInsensitiveMap::new,
				CaseInsensitiveGetMap::new);
		for (var factory : factories) {
			var map = factory.get();
			var keys = map.keySet();
			map.put("Ab", "first");
			assertTrue(keys.contains("Ab"));
			assertTrue(keys.remove("Ab"));
			assertFalse(map.containsKey("AB"));
			map.put("Ab", "second");
			assertEquals("second", map.get("AB"));
			assertTrue(map.values().remove("second"));
			assertFalse(map.containsKey("AB"));
			map.put("Ab", "third");
			assertEquals("third", map.entrySet().iterator().next().setValue("fourth"));
			assertEquals("fourth", map.get("AB"));
			assertTrue(map.entrySet().remove(Map.entry("Ab", "fourth")));
			assertFalse(map.containsKey("AB"));
			map.put("Ab", "fifth");
			map.put("Cd", "sixth");
			assertTrue(map.values().removeIf("fifth"::equals));
			assertFalse(map.containsKey("AB"));
			assertEquals("sixth", map.get("CD"));
			map.keySet().retainAll(Set.of());
			assertFalse(map.containsKey("CD"));
			map.put(null, null);
			assertTrue(map.containsKey(null));
			map.remove(null);
			assertFalse(map.containsKey(null));
			map.put("Ab", "last");
			map.entrySet().clear();
			assertFalse(map.containsKey("AB"));
			assertTrue(map.isEmpty());
		}
	}

	@Test
	void duplicateSpellingsRetainFirstFallbackAfterMutations() {
		var map = new CaseInsensitiveGetMap<String>();
		map.put("aB", "first");
		map.put("Ab", "second");
		assertEquals("first", map.put("aB", "updated"));
		assertEquals("updated", map.get("AB"));
		assertEquals("second", map.put("Ab", "other"));
		assertEquals("updated", map.get("AB"));
		assertEquals("other", map.get("Ab"));
		map.replaceAll((key, value) -> value + "!");
		assertEquals("updated!", map.get("AB"));
		map.keySet().remove("aB");
		assertEquals("other!", map.get("AB"));
		map.put("aB", "new");
		map.remove("Ab");
		assertEquals("new", map.get("AB"));
		assertEquals("new", map.replace("aB", "replacement"));
		assertEquals("replacement", map.get("AB"));
	}

	@Test
	void rejectsOddHexWithoutDiscardingDigits() {
		assertThrows(IllegalArgumentException.class, () -> BinaryUtils.toBinaryFromHex("123"));
		assertThrows(IllegalArgumentException.class, () -> BinaryUtils.toBinaryFromHex("1"));
		assertThrows(IllegalArgumentException.class, () -> BinaryUtils.toBinaryFromHex("zz"));
		assertArrayEquals(new byte[0], BinaryUtils.toBinaryFromHex(""));
		assertArrayEquals(new byte[] { 0, 18, (byte) 171, (byte) 255 },
				BinaryUtils.toBinaryFromHex("0012aBFF"));
	}
}
