/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MapUtilsTest {

	@SuppressWarnings("unchecked")
	@Test
	void testMerge1() {
		Map<String, Object> map1 = getObject1();
		Map<String, Object> map2 = getObject2();
		MapUtils.merge(map1, map2, (key, o1, o2) -> o2 != null ? o2 : o1, () -> new LinkedHashMap<String, Object>());
		//
		assertEquals(1, map1.get("a1"));
		assertEquals(3, map1.get("a2"));
		assertEquals(4, map1.get("a3"));
		assertTrue(map1.containsKey("child1"));
		Map<String, Object> map = (Map<String, Object>) map1.get("child1");
		assertTrue(map instanceof HashMap);
		assertEquals(200, map.get("b1"));
		assertEquals(300, map.get("b2"));
		assertEquals(400, map.get("c1"));
		map = (Map<String, Object>) map1.get("child2");
		assertTrue(map instanceof LinkedHashMap);
		assertEquals(1200, map.get("c2b1"));
		assertEquals(1300, map.get("c2b2"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testMerge2() {
		Map<String, Object> map1 = getObject1();
		Map<String, Object> map2 = getObject2();
		MapUtils.merge(map1, map2, (key, o1, o2) -> o2 != null ? o1 : o2, () -> new HashMap<String, Object>());
		//
		assertEquals(1, map1.get("a1"));
		assertEquals(2, map1.get("a2"));
		assertEquals(4, map1.get("a3"));
		assertTrue(map1.containsKey("child1"));
		Map<String, Object> map = (Map<String, Object>) map1.get("child1");
		assertTrue(map instanceof HashMap);
		assertEquals(100, map.get("b1"));
		assertEquals(300, map.get("b2"));
		assertEquals(400, map.get("c1"));
		map = (Map<String, Object>) map1.get("child2");
		assertTrue(map instanceof HashMap);
		assertEquals(1200, map.get("c2b1"));
		assertEquals(1300, map.get("c2b2"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testMerge3() {
		Map<String, Object> map1 = getObject1();
		Map<String, Object> map2 = getObject2();
		MapUtils.merge(map1, map2, (key, o1, o2) -> o2 != null ? o2 : o1);
		//
		assertEquals(1, map1.get("a1"));
		assertEquals(3, map1.get("a2"));
		assertEquals(4, map1.get("a3"));
		assertTrue(map1.containsKey("child1"));
		Map<String, Object> map = (Map<String, Object>) map1.get("child1");
		assertTrue(map instanceof HashMap);
		assertEquals(200, map.get("b1"));
		assertEquals(300, map.get("b2"));
		assertEquals(400, map.get("c1"));
		map = (Map<String, Object>) map1.get("child2");
		assertTrue(map instanceof LinkedHashMap);
		assertEquals(1200, map.get("c2b1"));
		assertEquals(1300, map.get("c2b2"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void testMerge4() {
		Map<String, Object> map1 = getObject1();
		Map<String, Object> map2 = getObject2();
		MapUtils.merge(map1, map2);
		//
		assertEquals(1, map1.get("a1"));
		assertEquals(3, map1.get("a2"));
		assertEquals(4, map1.get("a3"));
		assertTrue(map1.containsKey("child1"));
		Map<String, Object> map = (Map<String, Object>) map1.get("child1");
		assertTrue(map instanceof HashMap);
		assertEquals(200, map.get("b1"));
		assertEquals(300, map.get("b2"));
		assertEquals(400, map.get("c1"));
		map = (Map<String, Object>) map1.get("child2");
		assertTrue(map instanceof LinkedHashMap);
		assertEquals(1200, map.get("c2b1"));
		assertEquals(1300, map.get("c2b2"));
	}

	private Map<String, Object> getObject1() {
		Map<String, Object> map1 = new HashMap<>();
		map1.put("a1", 1);
		map1.put("a2", 2);
		Map<String, Object> mapChild1 = new HashMap<>();
		map1.put("child1", mapChild1);
		mapChild1.put("b1", 100);
		mapChild1.put("c1", 400);
		return map1;
	}

	private Map<String, Object> getObject2() {
		Map<String, Object> map2 = new HashMap<>();
		map2.put("a2", 3);
		map2.put("a3", 4);
		Map<String, Object> mapChild2 = new HashMap<>();
		map2.put("child1", mapChild2);
		mapChild2.put("b1", 200);
		mapChild2.put("b2", 300);
		Map<String, Object> mapChild3 = new HashMap<>();
		map2.put("child2", mapChild3);
		mapChild3.put("c2b1", 1200);
		mapChild3.put("c2b2", 1300);
		return map2;
	}

}
