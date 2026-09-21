/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.iterable.CombinedIterable;
import com.sqlapp.iterable.IndexedConvertIterable;

class GeneratorMvelUtilsTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void combinesEmptyAndNonEmptyIterables() {
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable1 = create(5);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable2 = create(4);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable3 = create(0);
		final IndexedConvertIterable<Map<String, Object>, Map<String, Object>> iterable4 = create(3);
		final CombinedIterable<Map<String, Object>> combinedIterable = new CombinedIterable<>(
				List.of(iterable1, iterable2, iterable3, iterable4));

		assertEquals(12, count(combinedIterable));
	}

	@Test
	void readsJsonRowsFromFilePathAndString() throws Exception {
		final Path file = temporaryDirectory.resolve("items.json");
		Files.writeString(file, "[{\"id\":1},{\"id\":2}]");

		assertEquals(2, count(GeneratorMvelUtils.fileIterator(file.toFile())));
		assertEquals(2, count(GeneratorMvelUtils.fileIterator(file)));
		assertEquals(2, count(GeneratorMvelUtils.fileIterator(file.toString())));
	}

	@Test
	void preservesEmptyResultForUnsupportedPathObject() {
		assertEquals(0, count(GeneratorMvelUtils.fileIterator(new Object())));
	}

	private static int count(final Iterable<Map<String, Object>> iterable) {
		int count = 0;
		for (@SuppressWarnings("unused") final Map<String, Object> row : iterable) {
			count++;
		}
		return count;
	}

	private static IndexedConvertIterable<Map<String, Object>, Map<String, Object>> create(final int size) {
		return new IndexedConvertIterable<>(() -> GeneratorMvelUtils.iterator(size), (i, map) -> map);
	}
}
