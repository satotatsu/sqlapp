/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

class RowIteratorRegressionTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void inferredIntegerColumnsPromoteToDecimal(final DataFormat format) throws Exception {
		final Table table = new Table("ITEMS");
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(numericFile(format)).iterator(table.getRows())) {
			assertEquals(9007199254740993L, iterator.next().get("V"));
			assertTrue(iterator.hasNext());
			assertTrue(iterator.hasNext());
			assertEquals(new BigDecimal("1.5"), iterator.next().get("V"));
			assertEquals(new BigDecimal("2"), iterator.next().get("V"));
			assertFalse(iterator.hasNext());
			assertEquals(DataType.DECIMAL, table.getColumns().get("V").getDataType());
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void preservesExplicitDecimalDefinition(final DataFormat format) throws Exception {
		final Table table = new Table("ITEMS");
		final Column column = new Column("V").setDataType(DataType.DECIMAL).setLength(30).setScale(3);
		table.getColumns().add(column);
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(numericFile(format)).iterator(table.getRows())) {
			assertEquals(new BigDecimal("9007199254740993"), iterator.next().get("V"));
			assertEquals(new BigDecimal("1.5"), iterator.next().get("V"));
			assertEquals(DataType.DECIMAL, column.getDataType());
			assertEquals(30L, column.getLength());
			assertEquals(3, column.getScale());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void infersTypesForExistingUntypedColumnsButPreservesExplicitIntegers(final boolean typed) throws Exception {
		final Table table = new Table("ITEMS");
		final Column column = new Column("V");
		if (typed) column.setDataType(DataType.INT);
		table.getColumns().add(column);
		final Path file = directory.resolve("values.json");
		Files.writeString(file, "[{\"V\":1},{\"V\":1.5}]");
		try (var iterator = (AbstractIterator) new JsonRowIteratorHandler(file.toFile()).iterator(table.getRows())) {
			iterator.next();
			final Object value = iterator.next().get("V");
			assertEquals(typed ? 1 : new BigDecimal("1.5"), value);
			assertEquals(typed ? DataType.INT : DataType.DECIMAL, column.getDataType());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  \t", "null" })
	void jsonLinesRejectInvalidRowsInsteadOfTruncatingInput(final String invalid) throws Exception {
		final var reader = new TrackingReader("{\"V\":1}\n" + invalid + "\n{\"V\":2}\n");
		try (var iterator = new JsonLineRowIteratorHandler.JsonlineRowIterator(new Table("ITEMS").getRows(),
				reader, new JsonConverter(), 0L, (row, column, value) -> value)) {
			assertEquals(1L, iterator.next().get("V"));
			final RuntimeException failure = assertThrows(RuntimeException.class, iterator::hasNext);
			assertTrue(failure.getCause().getMessage().contains("JSON object at line 2"));
			assertEquals(1, reader.closes);
			assertFalse(iterator.hasNext());
		}
		assertEquals(1, reader.closes);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void emptyFileTypeInputIsAnEmptyIterator(final boolean withSchema) throws Exception {
		final Table table = new Table("ITEMS");
		if (withSchema) table.getColumns().add(new Column("V").setDataType(DataType.INT));
		final var reader = new TrackingReader("");
		try (var iterator = (AbstractIterator) new FileTypeRowIteratorHandler(reader, DataFormat.CSV).iterator(table.getRows())) {
			assertFalse(iterator.hasNext());
			assertFalse(iterator.hasNext());
			assertThrows(NoSuchElementException.class, iterator::next);
			assertEquals(1, reader.closes);
			assertEquals(withSchema ? 1 : 0, table.getColumns().size());
		}
		assertEquals(1, reader.closes);
	}

	@Test
	void emptyFileTypeFileIsAnEmptyIterator() throws Exception {
		final Path file = Files.createFile(directory.resolve("empty.csv"));
		try (var iterator = (AbstractIterator) new FileTypeRowIteratorHandler(file.toFile()).iterator(new Table().getRows())) {
			assertFalse(iterator.hasNext());
		}
	}

	private Path numericFile(final DataFormat format) throws Exception {
		final Path file = directory.resolve("values." + format.getFileExtension());
		final String text = switch (format) {
		case JSON -> "[{\"V\":9007199254740993},{\"V\":1.5},{\"V\":2}]";
		case JSONL -> "{\"V\":9007199254740993}\n{\"V\":1.5}\n{\"V\":2}\n";
		case YAML -> "- V: 9007199254740993\n- V: 1.5\n- V: 2\n";
		case TOML -> "[[items]]\nV = 9007199254740993\n[[items]]\nV = 1.5\n[[items]]\nV = 2\n";
		default -> throw new IllegalArgumentException(format.name());
		};
		Files.writeString(file, text);
		return file;
	}

	private static final class TrackingReader extends StringReader {
		private int closes;

		private TrackingReader(final String value) {
			super(value);
		}

		@Override
		public void close() {
			closes++;
			super.close();
		}
	}
}
