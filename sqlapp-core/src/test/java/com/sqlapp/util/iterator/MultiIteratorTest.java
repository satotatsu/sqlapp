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

package com.sqlapp.util.iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MultiIteratorTest {

	@Test
	void test() {
		List<List<Integer>> listList = List.of(List.of(), List.of(1, 2), List.of(3, 4), List.of());
		test(listList, "a1a2a3a4");
		listList = List.of();
		test(listList, "");
		listList = List.of(List.of(1));
		test(listList, "a1");
		listList = List.of(List.of());
		test(listList, "");
		listList = List.of();
		test(listList, "");
	}

	private void test(List<List<Integer>> listList, String result) {
		Function<List<Integer>, Iterator<String>> func = (a -> {
			return a.stream().map(val -> "a" + val).collect(Collectors.toList()).iterator();
		});
		MultiIterable<List<Integer>, String> itr = new MultiIterable<List<Integer>, String>(listList, func);
		StringBuilder builder = new StringBuilder();
		for (String val : itr) {
			builder.append(val);
		}
		assertEquals(result, builder.toString());
	}
}
