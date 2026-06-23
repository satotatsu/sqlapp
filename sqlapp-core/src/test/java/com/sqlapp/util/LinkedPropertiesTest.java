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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LinkedPropertiesTest {

	@Test
	public void test() {
		LinkedProperties props = create();
		int[] i = new int[1];
		props.forEach((k, v) -> {
			switch (i[0]) {
			case 0:
				assertEquals("z", k);
				assertEquals("Z", v);
				break;
			case 1:
				assertEquals("y", k);
				assertEquals("Y", v);
				break;
			case 2:
				assertEquals("x", k);
				assertEquals("X", v);
				break;
			case 3:
				assertEquals("w", k);
				assertEquals("W", v);
				break;
			case 4:
				assertEquals("a", k);
				assertEquals("A", v);
				break;
			case 5:
				assertEquals("b", k);
				assertEquals("B", v);
				break;
			default:
			}
			i[0]++;
		});
	}

	@Test
	public void sort() {
		LinkedProperties props = create();
		props.sort(new StringComparator());
		int[] i = new int[1];
		props.forEach((k, v) -> {
			switch (i[0]) {
			case 0:
				assertEquals("a", k);
				assertEquals("A", v);
				break;
			case 1:
				assertEquals("b", k);
				assertEquals("B", v);
				break;
			case 2:
				assertEquals("w", k);
				assertEquals("W", v);
				break;
			case 3:
				assertEquals("x", k);
				assertEquals("X", v);
				break;
			case 4:
				assertEquals("y", k);
				assertEquals("Y", v);
				break;
			case 5:
				assertEquals("z", k);
				assertEquals("Z", v);
				break;
			default:
			}
			i[0]++;
		});
	}

	private LinkedProperties create() {
		LinkedProperties props = new LinkedProperties();
		props.put("z", "Z");
		props.put("y", "Y");
		props.put("x", "X");
		props.put("w", "W");
		props.put("a", "A");
		props.put("b", "B");
		return props;
	}

}
