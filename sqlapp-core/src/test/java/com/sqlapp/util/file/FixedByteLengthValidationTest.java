/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.PaddingType;

class FixedByteLengthValidationTest {

	@TempDir
	Path directory;

	@Test
	void invalidSettingsPreserveExistingFileAndDoNotCreateNewFiles() throws Exception {
		for (int length : new int[] { -2, 0 }) {
			final Path file = directory.resolve("existing" + length);
			Files.writeString(file, "original");
			final Consumer<FixedByteLengthFileSetting> setting = s -> s.addField("value", f -> f.setLength(length));
			final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
					() -> new FixedByteLengthWriter(file.toFile(), StandardCharsets.UTF_8, setting));
			assertTrue(failure.getMessage().contains("value"));
			assertEquals("original", Files.readString(file));
			final Path missing = directory.resolve("missing" + length);
			assertThrows(IllegalArgumentException.class,
					() -> new FixedByteLengthWriter(missing.toFile(), StandardCharsets.UTF_8, setting));
			assertFalse(Files.exists(missing));
		}
	}

	@Test
	void emptyPaddingIsRejectedBeforeOpeningTheOutput() throws Exception {
		final Path file = directory.resolve("empty-padding");
		Files.writeString(file, "original");
		final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new FixedByteLengthWriter(file.toFile(), StandardCharsets.UTF_8, table(),
						s -> {}, f -> f.setPadding("")));
		assertTrue(failure.getMessage().contains("value"));
		assertEquals("original", Files.readString(file));
	}

	@Test
	void implicitDecimalWidthIsRejectedForReadingAndWriting() throws Exception {
		final Table table = new Table();
		table.getColumns().add(c -> c.setName("amount").setDataType(DataType.DECIMAL));
		final Path file = directory.resolve("decimal");
		Files.writeString(file, "original");
		assertThrows(IllegalArgumentException.class,
				() -> new FixedByteLengthWriter(file.toFile(), StandardCharsets.UTF_8, table, s -> {}));
		assertThrows(IllegalArgumentException.class,
				() -> new FixedByteLengthWriter(file.toFile(), StandardCharsets.UTF_8, table, s -> {}, f -> {}));
		assertEquals("original", Files.readString(file));
		try (FixedByteLengthParser parser = new FixedByteLengthParser(new ByteArrayInputStream(new byte[0]),
				StandardCharsets.UTF_8, table, s -> {})) {
			assertThrows(IllegalArgumentException.class, () -> parser.readAllRecord((row, index) -> fail()));
		}
	}

	@Test
	void noPaddingWritesExactValuesAndRejectsShortValuesWithoutWritingThem() throws Exception {
		final Table table = table();
		final var row = table.newRow();
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (FixedByteLengthWriter writer = new FixedByteLengthWriter(output, StandardCharsets.UTF_8, table,
				s -> {}, f -> f.setPaddingType(PaddingType.NO_PADDING))) {
			row.put("value", "abc");
			writer.writeRow(row);
			row.put("value", "x");
			final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> writer.writeRow(row));
			assertTrue(failure.getMessage().contains("value"));
			row.put("value", "あ");
			writer.writeRow(row);
		}
		assertEquals("abc\nあ\n", output.toString(StandardCharsets.UTF_8));
	}

	@Test
	void multiBytePaddingMustFillTheRemainingWidthExactly() throws Exception {
		for (PaddingType type : new PaddingType[] { PaddingType.LEFT, PaddingType.RIGHT }) {
			final Table table = table();
			final var row = table.newRow();
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			try (FixedByteLengthWriter writer = new FixedByteLengthWriter(output, StandardCharsets.UTF_8, table,
					s -> {}, f -> { f.setLength(4); f.setPaddingType(type); f.setPadding("あ"); })) {
				row.put("value", "ab");
				final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> writer.writeRow(row));
				assertTrue(failure.getMessage().contains("value"));
				row.put("value", "b");
				writer.writeRow(row);
				row.put("value", "abcd");
				writer.writeRow(row);
			}
			assertEquals((type == PaddingType.LEFT ? "あb\n" : "bあ\n") + "abcd\n",
					output.toString(StandardCharsets.UTF_8));
		}
	}

	@Test
	void nullValuesAreRejectedWithFieldNameAndWriterCanContinue() throws Exception {
		final Table table = table();
		final var row = table.newRow();
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (FixedByteLengthWriter writer = new FixedByteLengthWriter(output, StandardCharsets.UTF_8, table, s -> {})) {
			final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> writer.writeRow(row));
			assertTrue(failure.getMessage().contains("value"));
			assertTrue(failure.getMessage().contains("NULL"));
			row.put("value", "abc");
			writer.writeRow(row);
		}
		assertEquals("abc\n", output.toString(StandardCharsets.UTF_8));
	}

	private Table table() {
		final Table table = new Table();
		table.getColumns().add(c -> c.setName("value").setDataType(DataType.VARCHAR).setLength(3));
		return table;
	}
}
