/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CombinedIterableTest {

	@Test
	void test() {
		List<Integer> list1 = List.of(1, 2);
		List<Integer> list2 = List.of(3, 4);
		List<Integer> list3 = List.of();
		List<Integer> list4 = List.of(5);
		int[] cnt = new int[1];
		cnt[0] = 0;
		CombinedIterable<Integer> iterable = new CombinedIterable<Integer>(List.of(list1, list2, list3, list4), itr -> {
			System.out.println("swith cnt=" + cnt[0]++);
		});
		int j = 0;
		for (Integer i : iterable) {
			System.out.println(i);
			assertEquals(j + 1, i);
			j++;
		}
		assertEquals(5, j);
	}

}
