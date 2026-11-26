/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;

class FileParserRegressionTest {

	@Test
	void directReadStartsParsingAndContinuesAfterExplicitBegin() throws IOException {
		try (CsvParser parser = new CsvParser(new StringReader("a,b\nc,d\n"), s -> {})) {
			assertArrayEquals(new String[] { "a", "b" }, parser.read());
			parser.beginParsing();
			assertArrayEquals(new String[] { "c", "d" }, parser.read());
			assertNull(parser.read());
		}
		try (TsvParser parser = new TsvParser(new StringReader("a\tb\nc\td\n"), s -> {})) {
			assertArrayEquals(new String[] { "a", "b" }, parser.read());
			assertArrayEquals(new String[] { "c", "d" }, parser.read());
			assertNull(parser.read());
		}
	}

	@Test
	void rawReadsDeliverEveryCompleteRecordAndIndex() throws IOException {
		for (String lineBreak : List.of("\n", "\r\n", "")) {
			final List<String> records = new ArrayList<>();
			final List<Long> indices = new ArrayList<>();
			try (FixedByteLengthParser parser = parser("abc" + lineBreak + "def" + lineBreak, lineBreak)) {
				parser.readAll((bytes, index) -> {
					records.add(new String(bytes, StandardCharsets.UTF_8));
					indices.add(index);
				});
			}
			assertEquals(List.of("abc" + lineBreak, "def" + lineBreak), records);
			assertEquals(List.of(0L, 1L), indices);
		}
	}

	@Test
	void truncatedRecordsAreRejectedByBothReadMethods() throws IOException {
		for (boolean rows : List.of(false, true)) {
			final List<Object> records = new ArrayList<>();
			try (FixedByteLengthParser parser = parser("abc\nd", "\n")) {
				final EOFException failure = assertThrows(EOFException.class, () -> {
					if (rows) {
						parser.readAllRecord((row, index) -> records.add(row.get("value")));
					} else {
						parser.readAll((bytes, index) -> records.add(new String(bytes, StandardCharsets.UTF_8)));
					}
				});
				assertTrue(failure.getMessage().contains("expected 4 bytes but read 1"));
			}
			assertEquals(List.of(rows ? "abc" : "abc\n"), records);
		}
	}

	@Test
	void shortStreamReadsAreAccumulatedForBothReadMethods() throws IOException {
		for (boolean rows : List.of(false, true)) {
			final ByteArrayInputStream input = new ByteArrayInputStream("abc\ndef\n".getBytes(StandardCharsets.UTF_8)) {
				@Override
				public synchronized int read(final byte[] bytes, final int offset, final int length) {
					return super.read(bytes, offset, Math.min(length, 1));
				}
				@Override
				public synchronized int available() {
					return 0;
				}
			};
			final List<String> records = new ArrayList<>();
			try (FixedByteLengthParser parser = new FixedByteLengthParser(input, StandardCharsets.UTF_8, table(), s -> {})) {
				if (rows) {
					parser.readAllRecord((row, index) -> records.add((String) row.get("value")));
				} else {
					parser.readAll((bytes, index) -> records.add(new String(bytes, StandardCharsets.UTF_8)));
				}
			}
			assertEquals(rows ? List.of("abc", "def") : List.of("abc\n", "def\n"), records);
		}
	}

	@Test
	void emptyInputDeliversNoRecords() throws IOException {
		try (FixedByteLengthParser parser = parser("", "\n")) {
			parser.readAll((bytes, index) -> fail("Unexpected record"));
		}
		try (FixedByteLengthParser parser = parser("", "\n")) {
			parser.readAllRecord((row, index) -> fail("Unexpected row"));
		}
	}

	@Test
	void oversizedValuesAreRejectedWithoutWritingTheRow() throws IOException {
		for (boolean numeric : List.of(false, true)) {
			final Table table = table();
			table.getColumns().add(c -> {
				c.setName("number");
				c.setDataType(DataType.INT);
			});
			final var row = table.newRow();
			row.put("value", numeric ? "XYZ" : "あい");
			row.put("number", numeric ? Integer.MIN_VALUE : 1);
			final ByteArrayOutputStream output = new ByteArrayOutputStream();
			try (FixedByteLengthWriter writer = new FixedByteLengthWriter(output, StandardCharsets.UTF_8, table, s -> {})) {
				final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> writer.writeRow(row));
				assertTrue(failure.getMessage().contains(numeric ? "number" : "value"));
				assertTrue(failure.getMessage().contains(numeric ? "11 bytes" : "6 bytes"));
				row.put("value", "XYZ");
				row.put("number", 1);
				writer.writeRow(row);
			}
			assertEquals("XYZ         1\n", output.toString(StandardCharsets.UTF_8));
		}
	}

	private FixedByteLengthParser parser(final String text, final String lineBreak) {
		return new FixedByteLengthParser(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
				StandardCharsets.UTF_8, table(), s -> s.setLineBreak(lineBreak));
	}

	private Table table() {
		final Table table = new Table();
		table.getColumns().add(c -> {
			c.setName("value");
			c.setDataType(DataType.VARCHAR);
			c.setLength(3);
		});
		return table;
	}
}
