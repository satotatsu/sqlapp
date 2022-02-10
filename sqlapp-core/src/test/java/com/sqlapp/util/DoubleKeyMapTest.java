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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static com.sqlapp.util.CommonUtils.*;

public class DoubleKeyMapTest {

	@Test
	public void testGetST() {
		DoubleKeyMap<String, String, Object> map=doubleKeyMap();
		map.put("k1a", "k2a", "val1");
		map.put("k1a", "k2b", "val2");
		map.put("k1b", "k2a", "val3");
		map.put(null, "k2a", "val4");
		map.put(null, null, "val5");
		assertEquals("val1", map.get("k1a", "k2a"));
		assertEquals("val2", map.get("k1a", "k2b"));
		assertEquals("val3", map.get("k1b", "k2a"));
		assertEquals("val4", map.get(null, "k2a"));
		assertEquals("val5", map.get(null, null));
	}

	@Test
	public void testToMap() {
		List<Bean> list=new ArrayList<>();
		list.add(new Bean("k1a", 1, "val1"));
		list.add(new Bean("k1a", 2, "val2"));
		list.add(new Bean("k1b", 2, "val3"));
		list.add(new Bean("k1b", 3, "val4"));
		list.add(new Bean("k1b", 4, "val5"));
		DoubleKeyMap<String, Integer, Bean> map=DoubleKeyMap.toMap(list, c->c.key1, c->c.key2);
		assertEquals("val1", map.get("k1a", 1).value);
		assertEquals("val2", map.get("k1a", 2).value);
		assertEquals("val3", map.get("k1b", 2).value);
		assertEquals("val4", map.get("k1b", 3).value);
		assertEquals("val5", map.get("k1b", 4).value);
	}

	@Test
	public void testToMapList() {
		List<Bean> list=new ArrayList<>();
		list.add(new Bean("k1a", 1, "val1"));
		list.add(new Bean("k1a", 1, "val1_1"));
		list.add(new Bean("k1a", 2, "val2"));
		list.add(new Bean("k1b", 2, "val3"));
		list.add(new Bean("k1b", 2, "val3_1"));
		list.add(new Bean("k1b", 3, "val4"));
		list.add(new Bean("k1b", 4, "val5"));
		DoubleKeyMap<String, Integer, List<Bean>> map=DoubleKeyMap.toListMap(list, c->c.key1, c->c.key2);
		assertEquals(2, map.get("k1a", 1).size());
		assertEquals(1, map.get("k1a", 2).size());
		assertEquals(2, map.get("k1b", 2).size());
	}

	static class Bean{
		public Bean(String key1, Integer key2, Object value){
			this.key1=key1;
			this.key2=key2;
			this.value=value;
		}
		public String key1;
		public Integer key2;
		public Object value;
	}
	
}
