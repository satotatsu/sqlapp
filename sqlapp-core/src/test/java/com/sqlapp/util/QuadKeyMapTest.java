/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import static com.sqlapp.util.CommonUtils.*;

public class QuadKeyMapTest {

	@Test
	public void testGetST() {
		QuadKeyMap<String, String, String, String, Object> map=quadKeyMap();
		map.put("k1a", "k2a", "k3a", "k4a", "val1");
		map.put("k1a", "k2a", "k3a", "k4b", "val2");
		map.put("k1a", "k2a", "k3b", "k4a", "val3");
		map.put("k1a", "k2b", "k3a", "k4a", "val4");
		map.put("k1a", "k2b", "k3b", "k4a", "val5");
		map.put("k1b", "k2a", "k3a", "k4a", "val6");
		map.put(null, "k2a", "k3a", "k4a", "val7");
		map.put(null, null, "k3a", "k4a", "val8");
		map.put(null, null, null, "k4a", "val9");
		map.put(null, null, null, null, "val10");
		assertEquals("val1", map.get("k1a", "k2a", "k3a", "k4a"));
		assertEquals("val2", map.get("k1a", "k2a", "k3a", "k4b"));
		assertEquals("val3", map.get("k1a", "k2a", "k3b", "k4a"));
		assertEquals("val4", map.get("k1a", "k2b", "k3a", "k4a"));
		assertEquals("val5", map.get("k1a", "k2b", "k3b", "k4a"));
		assertEquals("val6", map.get("k1b", "k2a", "k3a", "k4a"));
		assertEquals("val7", map.get(null, "k2a", "k3a", "k4a"));
		assertEquals("val8", map.get(null, null, "k3a", "k4a"));
		assertEquals("val9", map.get(null,  null, null, "k4a"));
		assertEquals("val10", map.get(null, null, null, null));
	}
}
