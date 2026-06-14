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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FileIterablesTest {

	private String packagePath = this.getClass().getPackageName().replace(".", "/");
	private String path = "src/test/resources/" + packagePath;

	@Test
	void readAllAsMap() {
		List<Iterable<Map<String, Object>>> list = FileIterables.readAllAsMap(new File(path), f -> true);
		int i = 0;
		for (Iterable<Map<String, Object>> itr : list) {
			System.out.println("itr" + i + " start.");
			int j = 0;
			for (Map<String, Object> map : itr) {
				System.out.println(map);
				j++;
			}
			assertTrue(j > 0);
			i++;
		}
		assertEquals(2, i);
	}

	@Test
	void readAllAsMapByPath() {
		List<Iterable<Map<String, Object>>> list = FileIterables.readAllAsMap(new File(path).toPath(), f -> true);
		int i = 0;
		for (Iterable<Map<String, Object>> itr : list) {
			System.out.println("itr" + i + " start.");
			int j = 0;
			for (Map<String, Object> map : itr) {
				System.out.println(map);
				j++;
			}
			assertTrue(j > 0);
			i++;
		}
		assertEquals(2, i);
	}

	@Test
	void readAllRecursiveAsMap() {
		List<Iterable<Map<String, Object>>> list = FileIterables.readAllRecursiveAsMap(new File(path), f -> true);
		int i = 0;
		for (Iterable<Map<String, Object>> itr : list) {
			System.out.println("itr" + i + " start.");
			int j = 0;
			for (Map<String, Object> map : itr) {
				System.out.println(map);
				j++;
			}
			assertTrue(j > 0);
			i++;
		}
		assertEquals(4, i);
	}

	@Test
	void readAllRecursiveAsMapByPath() {
		List<Iterable<Map<String, Object>>> list = FileIterables.readAllRecursiveAsMap(new File(path).toPath(),
				f -> true);
		int i = 0;
		for (Iterable<Map<String, Object>> itr : list) {
			System.out.println("itr" + i + " start.");
			int j = 0;
			for (Map<String, Object> map : itr) {
				System.out.println(map);
				j++;
			}
			assertTrue(j > 0);
			i++;
		}
		assertEquals(4, i);
	}

}
