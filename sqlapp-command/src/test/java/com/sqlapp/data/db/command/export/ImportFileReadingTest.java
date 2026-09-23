/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.export;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.InvalidValueException;

class ImportFileReadingTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "true", "false" })
	void honorsCustomTomlConverterInBothEntryPoints(final boolean importCommand) throws Exception {
		final Path file = Files.writeString(directory.resolve("items.toml"), "[[items]]\nID = 1\n");
		final var calls = new AtomicInteger();
		final var converter = new com.sqlapp.util.TomlConverter() {
			@Override
			public <T> T fromJsonString(final java.io.File input, final Class<T> type) {
				final T document = super.fromJsonString(input, type);
				calls.incrementAndGet();
				assertInstanceOf(java.util.Map.class, document);
				return type.cast(java.util.Map.of("items", List.of(java.util.Map.of("ID", 42))));
			}
		};
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		if (importCommand) {
			final var command = new ImportDataCommand();
			command.setSqlType(SqlType.INSERT);
			command.setTomlConverter(converter);
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
					var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INTEGER)");
				connection.setAutoCommit(false);
				assertEquals(1, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
						table, List.of(file.toFile())));
				try (var result = statement.executeQuery("SELECT ID FROM ITEMS")) {
					assertTrue(result.next());
					assertEquals(42, result.getInt(1));
					assertFalse(result.next());
				}
			}
		} else {
			final var reader = new TableFileReader();
			reader.setTomlConverter(converter);
			reader.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, file.toFile())));
			int count = 0;
			for (var row : table.getRows()) {
				assertEquals(42, ((Number) row.get("ID")).intValue());
				count++;
			}
			assertEquals(1, count);
		}
		assertEquals(1, calls.get());
	}

	@ParameterizedTest
	@CsvSource({ "0", "2" })
	void importAndTableReaderHonorExcelHeaderRows(final int headers) throws Exception {
		final Path file = directory.resolve("items.xlsx");
		try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
				var output = Files.newOutputStream(file)) {
			final var sheet = workbook.createSheet("items");
			for (int i = 0; i < headers; i++) {
				sheet.createRow(i).createCell(0).setCellValue("comment");
			}
			sheet.createRow(headers).createCell(0).setCellValue(42);
			workbook.write(output);
		}
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var reader = new TableFileReader();
		reader.setExcelSkipHeaderRowsSize(headers);
		reader.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, file.toFile())));
		int count = 0;
		for (var row : table.getRows()) {
			assertEquals(42, ((Number) row.get("ID")).intValue());
			count++;
		}
		assertEquals(1, count);
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		command.setExcelSkipHeaderRowsSize(headers);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER)");
			connection.setAutoCommit(false);
			assertEquals(1, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(file.toFile())));
			try (var result = statement.executeQuery("SELECT ID FROM ITEMS")) {
				assertTrue(result.next());
				assertEquals(42, result.getInt(1));
				assertFalse(result.next());
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "INSERT", "INSERT_ROWS" })
	void importsDirectoryWithConfiguredExpressionDelimiters(final SqlType sqlType) throws Exception {
		final Path input = Files.createDirectory(directory.resolve("input"));
		Files.writeString(input.resolve("first.csv"), "ID\n@{value}\n");
		Files.writeString(input.resolve("second.json"), "[{\"ID\":\"@{value + 1}\"}]");
		Files.writeString(input.resolve("README.txt"), "not import data");
		Files.createDirectory(input.resolve("nested"));
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var command = new ImportDataCommand();
		command.setSqlType(sqlType);
		command.setPlaceholders(true);
		command.setPlaceholderPrefix("@{");
		command.getContext().put("value", 10);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER)");
			connection.setAutoCommit(false);
			assertEquals(2, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(input.toFile())));
			try (var result = statement.executeQuery("SELECT ID FROM ITEMS ORDER BY ID")) {
				assertTrue(result.next());
				assertEquals(10, result.getInt(1));
				assertTrue(result.next());
				assertEquals(11, result.getInt(1));
				assertFalse(result.next());
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "INSERT", "INSERT_ROWS" })
	void failsWhenImportDirectoryCannotBeListed(final SqlType sqlType) throws Exception {
		final var unreadable = new java.io.File(directory.resolve("unreadable").toString()) {
			@Override
			public boolean isDirectory() {
				return true;
			}

			@Override
			public java.io.File[] listFiles() {
				return null;
			}
		};
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var command = new ImportDataCommand();
		command.setSqlType(sqlType);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "")) {
			final var error = assertThrows(java.io.IOException.class, () -> command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table, List.of(unreadable)));
			assertTrue(error.getMessage().contains("unreadable"));
		}
	}

	@Test
	void rejectsUnsupportedRowSqlBeforeReadingInput() throws Exception {
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.MERGE_ROWS);
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getConstraints().addPrimaryKeyConstraint("PK_ITEMS", "ID");
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "")) {
			// HSQL intentionally does not register its MERGE_ROWS factory.
			final var error = assertThrows(IllegalArgumentException.class, () -> command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table,
					List.of(directory.resolve("not-read.csv").toFile())));
			assertTrue(error.getMessage().contains("MERGE_ROWS"));
		}
	}

	@Test
	void failedRowBatchDoesNotCommitEarlierBatches() throws Exception {
		final Path file = Files.writeString(directory.resolve("items.csv"), "ID\n1\n2\n1\n");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT_ROWS);
		command.setDmlBatchSize(2);
		final var commits = new AtomicInteger();
		command.setCommitHandler(connection -> {
			connection.commit();
			commits.incrementAndGet();
		});
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER PRIMARY KEY)");
			connection.setAutoCommit(false);
			assertThrows(java.sql.SQLException.class, () -> command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table, List.of(file.toFile())));
			assertEquals(0, commits.get());
			connection.rollback();
			try (var result = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "INSERT", "INSERT_ROWS" })
	void closesProgrammaticRowsWhenDatabaseExecutionFails(final SqlType sqlType) throws Exception {
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final Row first = table.newRow();
		first.put("ID", 1);
		final Row duplicate = table.newRow();
		duplicate.put("ID", 1);
		final var closes = new AtomicInteger();
		table.setRowIteratorHandler(rows -> new ClosingRowIterator(List.of(first, duplicate), closes));
		final var command = new ImportDataCommand();
		command.setSqlType(sqlType);
		command.setDmlBatchSize(2);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER PRIMARY KEY)");
			connection.setAutoCommit(false);
			assertThrows(java.sql.SQLException.class, () -> command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table, List.of()));
			assertEquals(1, closes.get());
		}
	}

	@ParameterizedTest
	@CsvSource({ "1", "2", "4" })
	void importsRowSqlWithOneEvaluationPerValue(final int batchSize) throws Exception {
		final Path file = Files.writeString(directory.resolve("rows.csv"),
				"ID,CONTENT\n1,${counter.incrementAndGet()}\n2,${counter.incrementAndGet()}\n3,${counter.incrementAndGet()}\n");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.INT));
		table.getConstraints().addPrimaryKeyConstraint("PK_ITEMS", "ID");
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT_ROWS);
		command.setDmlBatchSize(batchSize);
		command.setPlaceholders(true);
		final var counter = new AtomicInteger();
		final var commits = new AtomicInteger();
		command.setCommitHandler(connection -> {
			connection.commit();
			commits.incrementAndGet();
		});
		command.getContext().put("counter", counter);
		command.getContext().put("lookup", List.of());
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER PRIMARY KEY, CONTENT INTEGER)");
			connection.setAutoCommit(false);
			assertEquals(3, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(file.toFile())));
			assertEquals(3, counter.get());
			assertEquals(1, commits.get());
			try (var result = statement.executeQuery("SELECT ID, CONTENT FROM ITEMS ORDER BY ID")) {
				for (int i = 1; i <= 3; i++) {
					assertTrue(result.next());
					assertEquals(i, result.getInt(1));
					assertEquals(i, result.getInt(2));
				}
				assertFalse(result.next());
			}
		}
	}

	@Test
	void tableReaderEvaluatesFileExpressionsWithConfiguredContextAndDelimiters() throws Exception {
		final byte[] bytes = { 0, 127, (byte) 255 };
		Files.write(directory.resolve("aaa.png"), bytes);
		final Path file = Files.writeString(directory.resolve("items.json"),
				"[{\"CONTENT\":\"@{new File(path)}\",\"LITERAL\":\"@{literal}\"}]");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.BLOB));
		table.getColumns().add(new Column("LITERAL").setDataType(DataType.VARCHAR));
		final var reader = new TableFileReader();
		reader.setPlaceholders(true);
		reader.setPlaceholderPrefix("@{");
		reader.setFileDirectory(directory.toFile());
		reader.getContext().put("path", "aaa.png");
		reader.getContext().put("literal", "@{mustNotRun()}");
		reader.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, file.toFile())));
		int count = 0;
		for (var row : table.getRows()) {
			assertArrayEquals(bytes, (byte[]) row.get("CONTENT"));
			assertEquals("@{mustNotRun()}", row.get("LITERAL"));
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	void customImportConversionRunsBeforeExpressionsAndKeepsOriginalValueOnFailure() throws Exception {
		final Path file = Files.writeString(directory.resolve("items.csv"), "CONTENT\nmissing.png\n");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.BLOB));
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		command.setPlaceholders(true);
		command.setFileDirectory(directory.toFile());
		final var calls = new AtomicInteger();
		command.setRowValueConverter((row, column, value) -> {
			calls.incrementAndGet();
			return "${new File('" + value + "')}";
		});
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (CONTENT BLOB)");
			connection.setAutoCommit(false);
			final var error = assertThrows(InvalidValueException.class, () -> command.executeImport(connection,
					DialectResolver.getInstance().getDialect(connection), table, List.of(file.toFile())));
			assertEquals("CONTENT", error.getKey());
			assertEquals("missing.png", error.getValue());
			assertInstanceOf(java.io.IOException.class, error.getCause());
			assertTrue(error.getMessage().contains("items.csv"));
			assertEquals(1, calls.get());
			try (var result = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				assertTrue(result.next());
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "csv,1,INSERT", "csv,2,INSERT", "json,1,INSERT", "json,2,INSERT",
			"csv,1,INSERT_ROWS", "csv,2,INSERT_ROWS", "csv,3,INSERT_ROWS",
			"json,1,INSERT_ROWS", "json,2,INSERT_ROWS", "json,3,INSERT_ROWS" })
	void importsFileConstructorBinary(final String format, final int batchSize, final SqlType sqlType) throws Exception {
		final byte[] bytes = { 0, 1, 127, (byte) 255 };
		Files.write(directory.resolve("aaa.png"), bytes);
		final String data = "csv".equals(format)
				? "ID,CONTENT\n1,${new File('aaa.png')}\n2,${new File('aaa.png')}\n"
				: "[{\"ID\":1,\"CONTENT\":\"${new File('aaa.png')}\"},"
						+ "{\"ID\":2,\"CONTENT\":\"${new File('aaa.png')}\"}]";
		final Path file = Files.writeString(directory.resolve("items." + format), data);
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.BLOB));
		final var command = new ImportDataCommand();
		command.setSqlType(sqlType);
		command.setDmlBatchSize(batchSize);
		command.setPlaceholders(true);
		command.setFileDirectory(directory.toFile());
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER, CONTENT BLOB)");
			connection.setAutoCommit(false);
			assertEquals(2, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(file.toFile())));
			try (var rows = statement.executeQuery("SELECT CONTENT FROM ITEMS ORDER BY ID")) {
				for (int i = 0; i < 2; i++) {
					assertTrue(rows.next());
					assertArrayEquals(bytes, rows.getBytes(1));
				}
				assertFalse(rows.next());
			}
			assertEquals(DataType.BLOB, table.getColumns().get("CONTENT").getDataType());
		}
	}

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
	void rejectsDirectoryWithoutSupportedDataFiles() throws Exception {
		final Path input = Files.createDirectory(directory.resolve("input"));
		Files.writeString(input.resolve("README.txt"), "no data");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER)");
			connection.setAutoCommit(false);
			final var error = assertThrows(IllegalArgumentException.class,
					() -> command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
							table, List.of(input.toFile())));
			assertEquals("At least one data file is required.", error.getMessage());
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
	void doesNotEvaluateAnExpressionReturnedByAnotherExpression() throws Exception {
		final Path json = Files.writeString(directory.resolve("items.json"),
				"[{\"CONTENT\":\"${literal}\"}]");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.VARCHAR).setLength(100));
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		command.setPlaceholders(true);
		final String literal = "${counter.incrementAndGet()}";
		final var counter = new AtomicInteger();
		command.getContext().put("literal", literal);
		command.getContext().put("counter", counter);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (CONTENT VARCHAR(100))");
			connection.setAutoCommit(false);
			assertEquals(1, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(json.toFile())));
			try (var rows = statement.executeQuery("SELECT CONTENT FROM ITEMS")) {
				assertTrue(rows.next());
				assertEquals(literal, rows.getString(1));
			}
			assertEquals(0, counter.get());
		}
	}

	@Test
	void importsCsvWithoutLosingRowsOrSchemaTypes() throws Exception {
		final Path csv = Files.writeString(directory.resolve("items.csv"), "ID,CONTENT\n1,hello\n");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("CONTENT").setDataType(DataType.VARCHAR).setLength(20));
		final var command = new ImportDataCommand();
		command.setSqlType(SqlType.INSERT);
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS (ID INTEGER, CONTENT VARCHAR(20))");
			connection.setAutoCommit(false);
			assertEquals(1, command.executeImport(connection, DialectResolver.getInstance().getDialect(connection),
					table, List.of(csv.toFile())));
			assertEquals(DataType.INT, table.getColumns().get("ID").getDataType());
			try (var rows = statement.executeQuery("SELECT CONTENT FROM ITEMS WHERE ID = 1")) {
				assertTrue(rows.next());
				assertEquals("hello", rows.getString(1));
			}
		}
	}

	@Test
	void missingBinaryFileFailsInsteadOfImportingAnEmptyValue() throws Exception {
		final Path json = Files.writeString(directory.resolve("items.json"),
				"[{\"CONTENT\":\"${new File('missing.png')}\"}]");
		final Table table = new Table("ITEMS");
		final TableFileReader reader = new TableFileReader();
		reader.setPlaceholders(true);
		reader.setFileDirectory(directory.toFile());
		reader.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table, json.toFile())));
		final var error = assertThrows(InvalidValueException.class, () -> {
			for (var row : table.getRows()) {
				fail("Missing binary must not produce a row");
			}
		});
		assertEquals("CONTENT", error.getKey());
		assertEquals("${new File('missing.png')}", error.getValue());
		assertInstanceOf(java.io.IOException.class, error.getCause());
		assertTrue(error.getMessage().contains("items.json"));
	}

	@Test
	void rejectsUnknownFormatWithFileName() {
		final Table table = new Table("ITEMS");
		final var error = assertThrows(IllegalArgumentException.class, () -> new TableFileReader()
				.setTableFilesPairs(List.of(new TableFileReader.TableFilesPair(table,
						directory.resolve("items.unknown").toFile()))));
		assertTrue(error.getMessage().contains("items.unknown"));
	}

	private static final class ClosingRowIterator implements Iterator<Row>, AutoCloseable {

		private final Iterator<Row> delegate;
		private final AtomicInteger closes;

		private ClosingRowIterator(final List<Row> rows, final AtomicInteger closes) {
			this.delegate = rows.iterator();
			this.closes = closes;
		}

		@Override
		public boolean hasNext() {
			return delegate.hasNext();
		}

		@Override
		public Row next() {
			return delegate.next();
		}

		@Override
		public void close() {
			closes.incrementAndGet();
		}
	}
}
