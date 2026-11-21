/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ConverterReviewFixesTest {

	@Test
	void timestampKeepsAllFractionDigits() {
		final Converters converters = new Converters();
		assertEquals(123456789, converters.convertObject("2026-1-1 10:00:00.123456789", Timestamp.class).getNanos());
		assertEquals(123456000, converters.convertObject("2026-1-1 10:00:00.123456", Timestamp.class).getNanos());
		assertEquals(500000000, converters.convertObject("2026-1-1 10:00:00.5", Timestamp.class).getNanos());
		assertEquals(123000000, converters.convertObject("2026-01-01 10:00:00.123", Timestamp.class).getNanos());
		assertEquals(123456789,
				converters.convertObject("2026-01-01 10:00:00.123456789", Timestamp.class).getNanos());
	}

	@Test
	void javaTimeKeepsAllFractionDigits() {
		final Converters converters = new Converters();
		assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123456000),
				converters.convertObject("2026-01-01T10:00:00.123456", LocalDateTime.class));
		assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0, 0, 123456789),
				converters.convertObject("2026-1-1 10:00:00.123456789", LocalDateTime.class));
		assertEquals(LocalTime.of(10, 0, 0, 120000000), converters.convertObject("10:00:00.12", LocalTime.class));
		assertEquals(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 123456000, ZoneOffset.ofHours(9)),
				converters.convertObject("2026-01-01 10:00:00.123456 +09:00", OffsetDateTime.class));
	}

	@Test
	void parseFormatterAcceptsOneToNineFractionDigits() {
		final DateTimeFormatter formatter = com.sqlapp.util.Java8DateUtils.getParseDateTimeFormatter("H:m:s.SSS");
		assertEquals(LocalTime.of(1, 2, 3, 400000000), LocalTime.parse("1:2:3.4", formatter));
		assertEquals(LocalTime.of(1, 2, 3, 123456789), LocalTime.parse("1:2:3.123456789", formatter));
		// quoted dots and output formatters are not changed
		assertSame(com.sqlapp.util.Java8DateUtils.getDateTimeFormatter("H:m:s'.SSS'"),
				com.sqlapp.util.Java8DateUtils.getParseDateTimeFormatter("H:m:s'.SSS'"));
		assertEquals("01:02:03.400",
				LocalTime.of(1, 2, 3, 400000000).format(com.sqlapp.util.Java8DateUtils.getDateTimeFormatter("HH:mm:ss.SSS")));
	}

	@Test
	void setParseFormatsKeepsOutputFormat() {
		final LocalDateTimeConverter converter = LocalDateTimeConverter.newInstance().setFormat("uuuu/MM/dd HH:mm");
		converter.setParseFormats("uuuu-M-d H:m:s");
		converter.addParseFormat("uuuu-M-d");
		assertEquals("2026/01/02 03:04", converter.format(LocalDateTime.of(2026, 1, 2, 3, 4, 5)));
	}

	@Test
	void localDateTimeToOffsetUsesOffsetOfThatDate() {
		final TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
			final OffsetDateTimeConverter converter = new OffsetDateTimeConverter();
			assertEquals(ZoneOffset.ofHours(-4),
					converter.toOffsetDateTime(LocalDateTime.of(2026, 7, 1, 12, 0)).getOffset());
			assertEquals(ZoneOffset.ofHours(-5),
					converter.toOffsetDateTime(LocalDateTime.of(2026, 1, 1, 12, 0)).getOffset());
		} finally {
			TimeZone.setDefault(original);
		}
	}

	static class CustomTimestamp extends Timestamp {
		private static final long serialVersionUID = 1L;

		CustomTimestamp(final long time) {
			super(time);
		}
	}

	@Test
	void findConverterUsesMostSpecificType() {
		for (int i = 0; i < 20; i++) {
			final Converters converters = new Converters();
			assertTrue((Object) converters.getConverter(CustomTimestamp.class) instanceof TimestampConverter);
		}
	}

	enum Color {
		RED, GREEN
	}

	@Test
	void convertersCanBeUsedFromManyThreads() throws Exception {
		final Converters converters = new Converters();
		final ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			final List<Callable<Boolean>> tasks = new ArrayList<>();
			for (int i = 0; i < 400; i++) {
				final int n = i;
				tasks.add(() -> {
					assertEquals(Color.GREEN, converters.convertObject("GREEN", Color.class));
					assertEquals(Locale.JAPAN.getClass(), converters.getConverter(Locale.class).convertObject("ja_JP").getClass());
					assertTrue((Object) converters.getConverter(CustomTimestamp.class) instanceof TimestampConverter);
					assertEquals(n, converters.convertObject(String.valueOf(n), Integer.class));
					return Boolean.TRUE;
				});
			}
			for (final Future<Boolean> future : executor.invokeAll(tasks)) {
				assertTrue(future.get());
			}
		} finally {
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void enumConverterSurvivesSerialization() throws Exception {
		final EnumConverter<Color> converter = new EnumConverter<>(Color.class);
		assertEquals(Color.RED, converter.convertObject("RED"));
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(converter);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			@SuppressWarnings("unchecked")
			final EnumConverter<Color> copy = (EnumConverter<Color>) in.readObject();
			assertEquals(Color.GREEN, copy.convertObject("GREEN"));
		}
	}

	@Test
	void numberFormatMustReadWholeText() {
		final IntegerConverter converter = new IntegerConverter();
		converter.setNumberFormat(NumberFormat.getInstance(Locale.US));
		assertEquals(1234, converter.convertObject("1,234"));
		assertThrows(ConverterException.class, () -> converter.convertObject("12abc"));
	}

	@Test
	void setNumberFormatDoesNotChangeCallerInstance() {
		final NumberFormat format = NumberFormat.getInstance(Locale.US);
		new IntegerConverter().setNumberFormat(format);
		assertFalse(format.isParseIntegerOnly());
	}

	@Test
	void equalsComparesParentState() {
		final IntegerConverter plain = new IntegerConverter();
		final IntegerConverter formatted = new IntegerConverter();
		formatted.setNumberFormat(NumberFormat.getInstance(Locale.US));
		assertNotEquals(plain, formatted);
		assertEquals(new IntegerConverter(), new IntegerConverter());
		assertNotEquals(new IntegerConverter(), new IntegerConverter().setDefaultValue(() -> 0));
		assertNotEquals(new UUIDConverter(), new URIConverter());
		assertEquals(new BooleanConverter(), new BooleanConverter());
		assertNotEquals(new BooleanConverter(), new BooleanConverter().setTrueString("true"));
	}
}
