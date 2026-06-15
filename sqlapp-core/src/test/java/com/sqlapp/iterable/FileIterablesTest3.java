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

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

class FileIterablesTest3 {

	private String packagePath = this.getClass().getPackageName().replace(".", "/");
	private String path = "src/test/resources/" + packagePath;

	@Test
	void readAllAsMap() {
		CachedMvelEvaluator evaluator = CachedMvelEvaluator.getInstance();
		Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
				FileIterables.readAllAsMap(new File(path), "true", evaluator));
		int i = 0;
		for (Map<String, Object> map : iterable) {
			System.out.println(map);
			i++;
		}
		assertEquals(46, i);
	}

	@Test
	void readAllAsMapTSV() {
		CachedMvelEvaluator evaluator = CachedMvelEvaluator.getInstance();
		Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
				FileIterables.readAllAsMap(new File(path), "file.name.endsWith('.tsv')", evaluator));
		int i = 0;
		for (Map<String, Object> map : iterable) {
			System.out.println(map);
			i++;
		}
		assertEquals(23, i);
	}

	@Test
	void readAllRecursiveAsMap() {
		CachedMvelEvaluator evaluator = CachedMvelEvaluator.getInstance();
		Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
				FileIterables.readAllRecursiveAsMap(new File(path), "true", evaluator));
		int i = 0;
		for (Map<String, Object> map : iterable) {
			System.out.println(map);
			i++;
		}
		assertEquals(92, i);
	}

	@Test
	void readAllRecursiveAsMapTSV_EXCEL() {
		CachedMvelEvaluator evaluator = CachedMvelEvaluator.getInstance();
		Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
				FileIterables.readAllRecursiveAsMap(new File(path),
						"file.name.endsWith('.xlsx') || file.name.endsWith('.tsv')", evaluator));
		int i = 0;
		for (Map<String, Object> map : iterable) {
			System.out.println(map);
			i++;
		}
		assertEquals(46, i);
	}

	@Test
	void readAllRecursiveAsMapYAML_EXCEL() {
		CachedMvelEvaluator evaluator = CachedMvelEvaluator.getInstance();
		Iterable<Map<String, Object>> iterable = new CombinedFileIterable<Map<String, Object>>(
				FileIterables.readAllRecursiveAsMap(new File(path),
						"file.name.endsWith('.xlsx') || file.name.endsWith('.yaml')", evaluator));
		int i = 0;
		for (Map<String, Object> map : iterable) {
			System.out.println(map);
			i++;
		}
		assertEquals(46, i);
	}

}
