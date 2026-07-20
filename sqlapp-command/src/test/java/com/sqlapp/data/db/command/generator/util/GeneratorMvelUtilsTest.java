package com.sqlapp.data.db.command.generator.util;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.iterable.CombinedIterable;
import com.sqlapp.iterable.IndexedConvertIterable;

class GeneratorMvelUtilsTest {

	@Test
	void test() {
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable1 = create(5);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable2 = create(4);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable3 = create(0);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable4 = create(3);
		CombinedIterable<Map<String, Object>> combinedIterable = new CombinedIterable<>(
				List.of(iterable1, iterable2, iterable3, iterable4));
		for (Map<String, Object> map : combinedIterable) {
			System.out.println(map);
		}
	}

	private IndexedConvertIterable<Map<String, Object>, Map<String, Object>> create(int size) {
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> countConvertIterable = new IndexedConvertIterable<>(
				() -> {
					return GeneratorMvelUtils.iterator(size);
				}, (i, map) -> {
					return map;
				});
		return countConvertIterable;
	}

}
