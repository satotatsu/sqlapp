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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.rowiterator.DataFormat;

class FileIterablesTest {
	@TempDir
	Path temporaryDirectory;

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
	void appliesFilePredicateToEachFileInDirectory() {
		final File directory = new File(path);

		assertEquals(1, FileIterables.readAllAsMap(directory,
				file -> file.getName().endsWith(".xml")).size());
		assertEquals(1, FileIterables.readAllRecursiveAsMap(directory,
				file -> file.getName().endsWith(".xlsx")).size());
	}

	@Test
	void readsJsonArrayAsMaps() throws Exception {
		final Path json = temporaryDirectory.resolve("items.json");
		Files.writeString(json, "[{\"id\":1},{\"id\":2}]");

		int count = 0;
		for (final Map<String, Object> row : FileIterables.readAsMap(json)) {
			assertTrue(row.containsKey("id"));
			count++;
		}
		assertEquals(2, count);
	}

	@Test
	void preservesJsonlAndYamlScalarTypes() throws Exception {
		final Path jsonl = temporaryDirectory.resolve("items.jsonl");
		Files.writeString(jsonl, "{\"id\":1,\"active\":true}\n");
		final Path yaml = temporaryDirectory.resolve("items.yaml");
		Files.writeString(yaml, "---\n- id: 1\n  active: true\n");

		for (final Path file : List.of(jsonl, yaml)) {
			final Map<String, Object> row = FileIterables.readAsMap(file).iterator().next();
			assertInstanceOf(Number.class, row.get("id"));
			assertInstanceOf(Boolean.class, row.get("active"));
		}
	}

	@Test
	void keepsCsvValuesAsStrings() throws Exception {
		final Path csv = temporaryDirectory.resolve("items.csv");
		Files.writeString(csv, "id,active\n1,true\n");

		final Map<String, Object> row = FileIterables.readAsMap(csv).iterator().next();
		assertInstanceOf(String.class, row.get("id"));
		assertInstanceOf(String.class, row.get("active"));
	}

	@Test
	void reportsSupportedMapFileFormats() {
		assertTrue(FileIterables.supports(Path.of("items.json")));
		assertTrue(FileIterables.supports(new File("items.xlsx")));
		assertFalse(FileIterables.supports(Path.of("items.toml")));
		assertFalse(FileIterables.supports(Path.of("items.txt")));
		assertFalse(FileIterables.supports((Path) null));
		assertTrue(DataFormat.JSONL.supportsMapRows());
		assertTrue(DataFormat.XML.supportsMapRows());
		assertFalse(DataFormat.TOML.supportsMapRows());
		assertInstanceOf(JsonMapIterable.class, DataFormat.JSON.createMapIterable(Path.of("items.json")));
		assertInstanceOf(XmlRowIterable.class, DataFormat.XML.createMapIterable(Path.of("items.xml")));
		assertInstanceOf(ExcelIterable.class, DataFormat.EXCEL.createMapIterable(Path.of("items.xlsx")));
	}

	@Test
	void rejectsUnsupportedDirectFileButSkipsItDuringDirectoryScan() throws Exception {
		final Path toml = temporaryDirectory.resolve("items.toml");
		Files.writeString(toml, "[[items]]\nid = 1\n");

		assertThrows(IllegalArgumentException.class, () -> FileIterables.readAsMap(toml));
		assertTrue(FileIterables.readAllAsMap(temporaryDirectory, path -> true).isEmpty());
	}

	@Test
	void readsExcel2003WorkbookAsMaps() throws Exception {
		final Path xls = temporaryDirectory.resolve("items.xls");
		try (var workbook = DataFormat.EXCEL2003.createWorkbook();
				OutputStream output = Files.newOutputStream(xls)) {
			final var sheet = workbook.createSheet();
			sheet.createRow(0).createCell(0).setCellValue("id");
			sheet.createRow(1).createCell(0).setCellValue(1);
			workbook.write(output);
		}

		final var iterator = FileIterables.readAsMap(xls).iterator();
		try {
			final Map<String, Object> row = iterator.next();
			assertTrue(row.containsKey("id"));
		} finally {
			((AutoCloseable) iterator).close();
		}
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
