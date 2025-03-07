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
import static com.sqlapp.util.CommonUtils.*;

import java.util.Map;

import org.junit.jupiter.api.Test;


public class CaseInsensitiveLinkedMapTest {
	/**
	 * Test method for {@link com.sqlapp.util.CaseInsensitiveMap#entrySet()}.
	 */
	@Test
	public void testEntrySet() {
		Map<String, String> map=caseInsensitiveLinkedMap();
		map.put("aB", "a");
		map.put("Ab", "c");
		map.put("Bc", "b");
		map.put("cD", "c");
		map.put("EF", "e");
		assertEquals("c", map.get("aB"));
		assertEquals("c", map.get("AB"));
		int i=0;
		for(Map.Entry<String, String> entry:map.entrySet()){
			if (i==0){
				assertTrue("aB".endsWith(entry.getKey()));
				assertEquals("c", entry.getValue());
			}
			if (i==1){
				assertTrue("Bc".endsWith(entry.getKey()));
				assertEquals("b", entry.getValue());
			}
			if (i==2){
				assertTrue("cD".endsWith(entry.getKey()));
				assertEquals("c", entry.getValue());
			}
			if (i==3){
				assertTrue("EF".endsWith(entry.getKey()));
				assertEquals("e", entry.getValue());
			}
			i++;
		}
	}
}
