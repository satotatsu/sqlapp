/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbstractStringMapTest {
	@Test
	void removalNormalizesKeysRegardlessOfDeclaredType() {
		for (AbstractStringMap<String> concrete : java.util.List.of(
				new UpperMap<String>(), new LowerMap<String>(), new LowerUnderScoreISMap<String>())) {
			Map<String, String> map = concrete;
			map.put("AbC", "value");
			assertEquals("value", map.remove("aBc"));
			assertFalse(map.containsKey("ABC"));
			map.put("AbC", "value");
			assertTrue(map.remove("aBc", "value"));
			assertTrue(map.isEmpty());
			map.put(null, "null-value");
			assertEquals("null-value", map.remove(null));
		}
	}
}
