/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

import tools.jackson.databind.DeserializationFeature;

class JsonRowSchemaTest {
	@TempDir
	Path directory;

	private static final List<DataFormat> FORMATS = List.of(DataFormat.JSON, DataFormat.JSONL,
			DataFormat.YAML, DataFormat.TOML);

	static Stream<Arguments> combinedFormats() {
		return FORMATS.stream().flatMap(first -> FORMATS.stream().map(second -> Arguments.of(first, second)));
	}

	@ParameterizedTest
	@MethodSource("combinedFormats")
	void combinedFilesShareInferenceWithoutFixingTheFirstFilesTypes(final DataFormat first, final DataFormat second)
			throws Exception {
		final Path a = file(first, "a", List.of(Map.of("V", 1)));
		final Path b = file(second, "b", List.of(Map.of("V", new BigDecimal("1.5"), "extra", "kept")));
		final Table table = new Table("ITEMS");
		try (var iterator = (CombinedRowIteratorHandler.CombinedRowIterator) FileRowIteratorFactory
				.create(List.of(a.toFile(), b.toFile())).iterator(table.getRows())) {
			assertEquals(1L, iterator.next().<Long>get("V"));
			assertTrue(iterator.hasNext());
			assertTrue(iterator.hasNext());
			final var row = iterator.next();
			assertEquals(new BigDecimal("1.5"), row.get("V"));
			assertEquals("kept", row.get("extra"));
			assertEquals(DataType.DECIMAL, table.getColumns().get("V").getDataType());
			assertFalse(iterator.hasNext());
		}
	}

	@ParameterizedTest
	@MethodSource("combinedFormats")
	void combinedFilesPreserveExplicitTypes(final DataFormat first, final DataFormat second) throws Exception {
		final Path a = file(first, "a", List.of(Map.of("V", 1)));
		final Path b = file(second, "b", List.of(Map.of("V", new BigDecimal("1.5"))));
		final Table table = new Table("ITEMS");
		final var column = new Column("V").setDataType(DataType.DECIMAL).setLength(30).setScale(5);
		table.getColumns().add(column);
		try (var iterator = (CombinedRowIteratorHandler.CombinedRowIterator) FileRowIteratorFactory
				.create(List.of(a.toFile(), b.toFile())).iterator(table.getRows())) {
			iterator.next();
			assertEquals(new BigDecimal("1.5"), iterator.next().get("V"));
			assertEquals(30L, column.getLength());
			assertEquals(5, column.getScale());
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void infersDistinctSourceKeysWithoutApplyingAliases(final DataFormat format) throws Exception {
		final Map<String, Object> values = new LinkedHashMap<>();
		values.put("user_id", 1);
		values.put("userId", 2);
		final Table table = new Table("ITEMS");
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(file(format, "names", List.of(values)))
				.iterator(table.getRows())) {
			final var row = iterator.next();
			assertEquals(2, table.getColumns().size());
			assertEquals(1L, row.<Long>get("user_id"));
			assertEquals(2L, row.<Long>get("userId"));
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void rejectsSourceNamesThatTheSchemaCannotDistinguish(final DataFormat format) throws Exception {
		final Map<String, Object> values = new LinkedHashMap<>();
		values.put("V", 1);
		values.put("v", 2);
		final Table table = new Table("ITEMS");
		table.getColumns().setCaseSensitive(false);
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(file(format, "ambiguous", List.of(values)))
				.iterator(table.getRows())) {
			final var failure = assertThrows(IllegalArgumentException.class, iterator::next);
			assertTrue(failure.getMessage().contains("Ambiguous JSON column name"));
			assertFalse(iterator.hasNext());
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void preservesAliasLookupAgainstAnExplicitSchema(final DataFormat format) throws Exception {
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("user_id").setDataType(DataType.BIGINT));
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(
				file(format, "alias", List.of(Map.of("userId", 2)))).iterator(table.getRows())) {
			assertEquals(2L, iterator.next().<Long>get("user_id"));
			assertEquals(1, table.getColumns().size());
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL", "YAML", "TOML" })
	void retainsDecimalPrecisionAndGrowsInferredScale(final DataFormat format) throws Exception {
		final var precise = new BigDecimal("0.123456789012345678901234567890");
		final Table table = new Table("ITEMS");
		try (var iterator = (AbstractIterator) format.createRowIteratorHandler(
				file(format, "precise", List.of(Map.of("V", 1), Map.of("V", precise)))).iterator(table.getRows())) {
			iterator.next();
			final BigDecimal actual = iterator.next().get("V");
			assertEquals(0, precise.compareTo(actual));
			final var column = table.getColumns().get("V");
			assertTrue(column.getScale() >= precise.scale());
			assertTrue(column.getLength() >= column.getScale() + 1L);
		}
	}

	@ParameterizedTest
	@EnumSource(value = DataFormat.class, names = { "JSON", "JSONL" })
	void usesExactParsingWithoutMutatingTheProvidedConverter(final DataFormat format) throws Exception {
		final var precise = new BigDecimal("0.123456789012345678901234567890");
		final var converter = new JsonConverter();
		final var mapper = converter.getObjectMapper();
		final boolean before = mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		final var path = file(format, "explicit", List.of(Map.of("V", precise)));
		final var handler = format == DataFormat.JSON ? new JsonRowIteratorHandler(path.toFile(), converter)
				: new JsonLineRowIteratorHandler(path.toFile(), converter);
		final Table table = new Table("ITEMS");
		table.getColumns().add(new Column("V").setDataType(DataType.DECIMAL).setLength(38).setScale(30));
		try (var iterator = (AbstractIterator) handler.iterator(table.getRows())) {
			assertEquals(0, precise.compareTo(iterator.next().get("V")));
		}
		assertSame(mapper, converter.getObjectMapper());
		assertEquals(before, mapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
	}

	private Path file(final DataFormat format, final String name, final List<? extends Map<String, ?>> rows)
			throws Exception {
		final var path = directory.resolve(name + "." + format.getFileExtension());
		final var converter = format.createJsonConverter();
		final String text;
		if (format == DataFormat.JSONL) {
			final StringBuilder lines = new StringBuilder();
			for (final var row : rows) lines.append(converter.toJsonString(row)).append('\n');
			text = lines.toString();
		} else {
			text = converter.toJsonString(format == DataFormat.TOML ? Map.of("items", rows) : rows);
		}
		Files.writeString(path, text);
		return path;
	}
}
