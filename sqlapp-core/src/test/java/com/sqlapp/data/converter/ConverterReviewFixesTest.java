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
		assertEquals(123456789, converters.convertObject("2026-01-01 10:00:00.123456789", Timestamp.class).getNanos());
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
		assertEquals("01:02:03.400", LocalTime.of(1, 2, 3, 400000000)
				.format(com.sqlapp.util.Java8DateUtils.getDateTimeFormatter("HH:mm:ss.SSS")));
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
					assertEquals(Locale.JAPAN.getClass(),
							converters.getConverter(Locale.class).convertObject("ja_JP").getClass());
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

	@Test
	void compactDigitsAreLocalDateTimes() {
		final Converters converters = new Converters();
		assertEquals(LocalDateTime.of(2026, 11, 21, 0, 0), converters.convertObject("20261121", LocalDateTime.class));
		assertEquals(java.time.LocalDate.of(2026, 11, 21),
				converters.convertObject("20261121", java.time.LocalDate.class));
		assertEquals(LocalDateTime.of(2026, 11, 21, 10, 30, 15),
				converters.convertObject("20261121103015", LocalDateTime.class));
		assertEquals(LocalDateTime.of(2026, 11, 21, 10, 30, 15, 123000000),
				converters.convertObject("20261121103015123", LocalDateTime.class));
		assertEquals(LocalDateTime.of(2026, 11, 21, 0, 0),
				converters.convertObject("20261121", java.time.ZonedDateTime.class).toLocalDateTime());
		// not a valid date: epoch milliseconds as before
		assertEquals(java.time.Instant.ofEpochMilli(20261399L),
				converters.convertObject("20261399", java.time.ZonedDateTime.class).toInstant());
		assertEquals(java.time.Instant.ofEpochMilli(1700000000000L),
				converters.convertObject("1700000000000", java.time.OffsetDateTime.class).toInstant());
	}

	@Test
	void singleQuoteIsNotAnIndexError() {
		final Converters converters = new Converters();
		assertThrows(UnsupportedOperationException.class, () -> converters.convertObject("'", LocalDateTime.class));
		assertThrows(RuntimeException.class, () -> converters.convertObject("'", java.time.Duration.class));
		assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5),
				converters.convertObject("'2026-01-02 03:04:05'", LocalDateTime.class));
	}

	@Test
	void currentKeywordsOnly() {
		final Converters converters = new Converters();
		for (final String text : new String[] { "now", "NOW()", "CURRENT_TIMESTAMP", "current_timestamp(3)",
				"current date", "SYSDATE", "systimestamp", "getdate()", "localtimestamp", "current" }) {
			final LocalDateTime value = converters.convertObject(text, LocalDateTime.class);
			assertTrue(java.time.Duration.between(value, LocalDateTime.now()).abs().getSeconds() < 60, text);
		}
		assertThrows(UnsupportedOperationException.class,
				() -> converters.convertObject("system error", LocalDateTime.class));
		assertThrows(UnsupportedOperationException.class,
				() -> converters.convertObject("currently unknown", LocalDateTime.class));
	}

	@Test
	void anyOptionalUsesDefaultWhenEmpty() {
		final Converters converters = new Converters();
		assertEquals(0, (int) converters.getConverter(int.class).convertObject(java.util.OptionalInt.empty()));
		assertEquals(0, (int) converters.getConverter(int.class).convertObject(java.util.Optional.empty()));
		assertEquals(null, new IntegerConverter().convertObject(java.util.OptionalLong.empty()));
		assertEquals(12, new IntegerConverter().convertObject(java.util.Optional.of("12")));
		assertEquals(1, new IntegerConverter().convertObject(java.util.OptionalDouble.of(1.9)));
		assertEquals(3L, new LongConverter().convertObject(java.util.Optional.of(3)));
		assertEquals((short) 4, new ShortConverter().convertObject(java.util.OptionalInt.of(4)));
		assertEquals(0L, (long) converters.getConverter(long.class).convertObject(java.util.OptionalLong.empty()));
		assertThrows(ConverterException.class, () -> new ByteConverter().convertObject(java.util.OptionalInt.of(300)));
	}

	@Test
	void bigIntegerTruncatesLikeOtherIntegralTypes() {
		final BigIntegerConverter converter = new BigIntegerConverter();
		assertEquals(java.math.BigInteger.ONE, converter.convertObject("1.5"));
		assertEquals(java.math.BigInteger.ONE, converter.convertObject(java.util.Optional.of(1.9d)));
		assertEquals(new java.math.BigDecimal(1e30d).toBigInteger(), converter.convertObject(1e30d));
		assertThrows(ConverterException.class, () -> converter.convertObject(Double.NaN));
		assertThrows(ConverterException.class, () -> converter.convertObject("abc"));
	}

	@Test
	void integralBytesAreSignExtendedAndRangeChecked() {
		assertEquals(1, IntegerConverter.toInt(new byte[] { 1 }));
		assertEquals(-1, IntegerConverter.toInt(new byte[] { (byte) 0xFF }));
		assertEquals(258, IntegerConverter.toInt(new byte[] { 0, 0, 0, 1, 2 }));
		assertEquals(0x01020304, IntegerConverter.toInt(new byte[] { 1, 2, 3, 4 }));
		assertThrows(ConverterException.class, () -> IntegerConverter.toInt(new byte[] { 1, 0, 0, 0, 0 }));
		assertEquals(0x0102030405060708L, LongConverter.toLong(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }));
		assertEquals(Short.MAX_VALUE, ShortConverter.toShort(new byte[] { 0x7F, (byte) 0xFF }));
		assertThrows(ConverterException.class, () -> ShortConverter.toShort(new byte[] { 1, 0, 0 }));
		assertEquals(1.5d, DoubleConverter.toDouble(java.nio.ByteBuffer.allocate(4).putFloat(1.5f).array()));
		assertEquals(1.5f, FloatConverter.toFloat(java.nio.ByteBuffer.allocate(8).putDouble(1.5d).array()));
		assertThrows(ConverterException.class, () -> DoubleConverter.toDouble(new byte[] { 1, 2 }));
	}

	@Test
	void conversionFailuresAreConverterExceptions() {
		assertThrows(ConverterException.class, () -> new IntegerConverter().convertObject("abc"));
		final ConverterException enumFailure = assertThrows(ConverterException.class,
				() -> new EnumConverter<>(Color.class).convertObject("BLUE"));
		assertTrue(enumFailure.getMessage().contains("BLUE"), enumFailure.getMessage());
		assertThrows(ConverterException.class, () -> new URLConverter().convertObject("not a url"));
	}

	@Test
	void durationAndPeriodAcceptAnyDate() {
		final java.util.Date date = new java.util.Date(0);
		assertEquals(new Converters().convertObject(new java.sql.Date(0), java.time.Duration.class),
				new Converters().convertObject(date, java.time.Duration.class));
		assertEquals(new Converters().convertObject(new java.sql.Date(0), java.time.Period.class),
				new Converters().convertObject(date, java.time.Period.class));
	}
}
