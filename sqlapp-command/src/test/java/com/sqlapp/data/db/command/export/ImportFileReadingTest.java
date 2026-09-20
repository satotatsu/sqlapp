/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ImportFileReadingTest {

	@TempDir
	Path directory;

	@Test
	void importsBinaryMvelAndMultipleFilesThroughJdbc() throws Exception {
		final byte[] bytes = { 0, 1, 2, 127, (byte) 255 };
		Files.write(directory.resolve("aaa.png"), bytes);
		final Path first = Files.writeString(directory.resolve("first.json"),
				"[{\"ID\":1,\"CONTENT\":\"${readFileAsBytes('aaa.png')}\"}]");
		final Path second = Files.writeString(directory.resolve("second.json"),
				"[{\"ID\":2,\"CONTENT\":\"${readFileAsBytes('aaa.png')}\"}]");
		final ImportDataCommand command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		command.setPlaceholders(true);
		command.setFileDirectory(directory.toFile());
		command.setDmlBatchSize(2);
		final AtomicInteger conversions = new AtomicInteger();
		command.setRowValueConverter((r, c, v) -> {
			if ("CONTENT".equals(c.getName())) {
				conversions.incrementAndGet();
			}
			return v;
		});
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.BLOB));
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER, CONTENT BLOB)");
			connection.setAutoCommit(false);
			assertEquals(2, command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table,
					List.of(first.toFile(), second.toFile())));
			try (var rows = statement.executeQuery("SELECT CONTENT FROM ITEMS ORDER BY ID")) {
				assertTrue(rows.next());
				assertArrayEquals(bytes, rows.getBytes(1));
				assertTrue(rows.next());
				assertArrayEquals(bytes, rows.getBytes(1));
				assertFalse(rows.next());
			}
			assertEquals(2, conversions.get());
		}
	}

	@Test
	void tableReaderDecodesTomlAndJsonLinesInOrder() throws Exception {
		final Path toml = Files.writeString(directory.resolve("items.toml"), "[[items]]\nID = 1\n");
		final Path jsonl = Files.writeString(directory.resolve("items.jsonl"), "{\"ID\":2}\n{\"ID\":3}\n");
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		new TableFileReader().setTableFilesPairs(List.of(
				new TableFileReader.TableFilesPair(table, toml.toFile(), jsonl.toFile())));
		int expected = 1;
		for (var row : table.getRows()) {
			assertEquals(expected++, ((Number) row.get("ID")).intValue());
		}
		assertEquals(4, expected);
	}

	@Test
	void disabledPlaceholdersRemainLiteral() throws Exception {
		final Path csv = Files.writeString(directory.resolve("items.csv"), "VALUE\n${missingVariable}\n");
		final Table table = new Table("ITEMS");
		new TableFileReader().setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, csv.toFile())));
		int count = 0;
		for (var row : table.getRows()) {
			assertEquals("${missingVariable}", row.get("VALUE"));
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	void missingBinaryFileFailsInsteadOfImportingAnEmptyValue() throws Exception {
		final Path json = Files.writeString(directory.resolve("items.json"),
				"[{\"CONTENT\":\"${readFileAsBytes('missing.png')}\"}]");
		final Table table = new Table("ITEMS");
		final TableFileReader reader = new TableFileReader();
		reader.setPlaceholders(true);
		reader.setFileDirectory(directory.toFile());
		reader.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, json.toFile())));
		assertThrows(RuntimeException.class, () -> {
			for (var row : table.getRows()) {
				fail("Missing binary must not produce a row");
			}
		});
	}

	@Test
	void rejectsUnknownFormatWithFileName() {
		final Table table = new Table("ITEMS");
		final var error = assertThrows(IllegalArgumentException.class, () -> new TableFileReader()
				.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table,
						directory.resolve("items.unknown").toFile()))));
		assertTrue(error.getMessage().contains("items.unknown"));
	}
}
