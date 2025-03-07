/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import static com.sqlapp.util.CommonUtils.*;

public class TripleKeyMapTest {

	@Test
	public void testGetST() {
		TripleKeyMap<String, String, String, Object> map=tripleKeyMap();
		map.put("k1a", "k2a", "k3a", "val1");
		map.put("k1a", "k2a", "k3b", "val2");
		map.put("k1a", "k2b", "k3a", "val3");
		map.put("k1a", "k2b", "k3b", "val4");
		map.put("k1b", "k2a", "k3a", "val5");
		map.put(null, "k2a", "k3a", "val6");
		map.put(null, null, null, "val7");
		assertEquals("val1", map.get("k1a", "k2a", "k3a"));
		assertEquals("val2", map.get("k1a", "k2a", "k3b"));
		assertEquals("val3", map.get("k1a", "k2b", "k3a"));
		assertEquals("val4", map.get("k1a", "k2b", "k3b"));
		assertEquals("val5", map.get("k1b", "k2a", "k3a"));
		assertEquals("val6", map.get(null,  "k2a", "k3a"));
		assertEquals("val7", map.get(null, null, null));
	}
}
