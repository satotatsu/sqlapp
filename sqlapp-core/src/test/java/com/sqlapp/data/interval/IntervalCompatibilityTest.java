/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.interval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.IntervalDayToSecondConverter;
import com.sqlapp.data.converter.IntervalYearToMonthConverter;
import com.sqlapp.util.file.CsvParser;
import com.sqlapp.util.file.CsvWriter;

class IntervalCompatibilityTest {
	@Test
	void readsFrozenStreamsOfEveryLegacyIntervalClass() throws Exception {
		final Properties fixtures = new Properties();
		try (var input = getClass().getResourceAsStream("legacy-serialization.properties")) {
			assertNotNull(input);
			fixtures.load(input);
		}
		assertEquals(15, fixtures.size());
		for (String name : fixtures.stringPropertyNames()) {
			final byte[] bytes = Base64.getDecoder().decode(fixtures.getProperty(name));
			final Interval value;
			try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
				value = (Interval) input.readObject();
			}
			assertEquals(name, value.getClass().getSimpleName());
			assertFalse(value.isPositive(), name);
			final int expectedMonths = switch (name) {
			case "Interval", "IntervalYearToDay" -> 14;
			case "IntervalYear" -> 60;
			case "IntervalMonth" -> 25;
			case "IntervalYearToMonth" -> 71;
			default -> 0;
			};
			final int expectedSeconds = switch (name) {
			case "Interval", "IntervalDayToSecond" -> 273_906;
			case "IntervalDay", "IntervalYearToDay" -> 259_200;
			case "IntervalHour" -> 90_000;
			case "IntervalMinute" -> 3_660;
			case "IntervalSecond" -> 62;
			case "IntervalDayToHour" -> 273_600;
			case "IntervalDayToMinute" -> 273_900;
			case "IntervalHourToMinute" -> 90_300;
			case "IntervalHourToSecond" -> 90_306;
			case "IntervalMinuteToSecond" -> 3_666;
			default -> 0;
			};
			final int expectedNanos = switch (name) {
			case "Interval", "IntervalSecond", "IntervalDayToSecond", "IntervalHourToSecond",
					"IntervalMinuteToSecond" -> 123_456_789;
			default -> 0;
			};
			assertEquals(expectedMonths, value.getMonthsFull(), name);
			assertEquals(expectedSeconds, value.getSecondsFull(), name);
			assertEquals(expectedNanos, value.getNanos(), name);
			// Also pin the writer's stream contract, not only deserialization.
			final var output = new ByteArrayOutputStream();
			try (var stream = new ObjectOutputStream(output)) {
				stream.writeObject(value);
			}
			assertArrayEquals(bytes, output.toByteArray(), name);
		}
	}

	@Test
	void pinsBaseSerializationFieldsAndUid() {
		final ObjectStreamClass descriptor = ObjectStreamClass.lookup(Interval.class);
		assertEquals(-5789019381885490073L, descriptor.getSerialVersionUID());
		assertArrayEquals(new String[] { "months:int", "nanos:int", "positive:boolean", "seconds:int" },
				java.util.Arrays.stream(descriptor.getFields())
						.map(field -> field.getName() + ":" + field.getType().getSimpleName()).toArray(String[]::new));
	}

	@Test
	void retainsLegacyConverterTextThroughCsv() throws Exception {
		final var yearMonth = new IntervalYearToMonthConverter();
		final var daySecond = new IntervalDayToSecondConverter();
		final var text = new StringWriter();
		try (var writer = new CsvWriter(text, settings -> settings.getFormat().setLineSeparator("\n"))) {
			writer.writeRow(yearMonth.format(IntervalYearToMonth.parse("-5-11")),
					daySecond.format(new IntervalDayToSecond(3, 4, 5, 6)));
		}
		assertEquals("-5-11,3 4:5:6\n", text.toString());
		final int[] rows = { 0 };
		try (var parser = new CsvParser(new StringReader(text.toString()), settings -> {})) {
			parser.readAll((row, number) -> {
				rows[0]++;
				final var restored = yearMonth.convertObject(row[0]);
				assertFalse(restored.isPositive());
				assertEquals(71, restored.getMonthsFull());
				assertEquals(273_906, daySecond.convertObject(row[1]).getSecondsFull());
			});
		}
		assertEquals(1, rows[0]);
	}
}
