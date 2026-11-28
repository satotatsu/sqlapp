/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.TestCaseBase;
import com.sqlapp.data.converter.Converter;

/**
 * TIME/TIMESTAMP WITH TIME ZONE keep their offset and fractional seconds
 * through text conversion and JDBC.
 */
class ZonedTemporalTypesTest extends TestCaseBase {

	private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
	private static final ZoneOffset TOKYO = ZoneOffset.ofHours(9);

	private TimeZone original;

	@Override
	@BeforeEach
	public void setUpTestCaseBase() {
		original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
	}

	@Override
	@AfterEach
	public void tearDownTestCaseBase() {
		TimeZone.setDefault(original);
	}

	@SuppressWarnings("unchecked")
	private static <T> Converter<T> converter(final DbDataType<?> type) {
		return (Converter<T>) type.getConverter();
	}

	@Test
	void timestampTextKeepsOffsetAndFraction() {
		final Converter<OffsetDateTime> converter = converter(new TimestampWithTimeZoneType());
		final OffsetDateTime expected = OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 123_400_000, IST);
		assertEquals(expected, converter.convertObject("2026-11-20 12:34:56.1234 +05:30"));
		assertEquals(expected, converter.convertObject("2026-11-20T12:34:56.1234+05:30"));
		assertEquals(expected, converter.convertObject("2026-11-20 12:34:56.1234 +0530"));
		assertEquals("2026-11-20 12:34:56.1234 +05:30", converter.format(expected));
		// UTC is written as Z, the shared converter convention, and reads back.
		final OffsetDateTime utc = OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 0, ZoneOffset.UTC);
		assertEquals("2026-11-20 12:34:56 Z", converter.format(utc));
		assertEquals(utc, converter.convertObject(converter.format(utc)));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 123_456_789, ZoneOffset.ofHours(-8)),
				converter.convertObject("2026-11-20 12:34:56.123456789 -08"));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 0, ZoneOffset.UTC),
				converter.convertObject("2026-11-20 12:34:56Z"));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 0, TOKYO),
				converter.convertObject("2026-11-20 12:34:56 Asia/Tokyo"));
	}

	@Test
	void timestampWithoutZoneUsesJvmDefaultZone() {
		final Converter<OffsetDateTime> converter = converter(new TimestampWithTimeZoneType());
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 0, TOKYO),
				converter.convertObject("2026-11-20 12:34:56"));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 123_456_789, TOKYO),
				converter.convertObject(java.sql.Timestamp.valueOf("2026-11-20 12:34:56.123456789")));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 0, 0, 0, 0, TOKYO),
				converter.convertObject(LocalDate.of(2026, 11, 20)));
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
		assertEquals(OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 0, ZoneOffset.ofHours(-5)),
				converter.convertObject("2026-11-20 12:34:56"));
	}

	@Test
	void timeTextKeepsOffsetAndFraction() {
		final Converter<OffsetTime> converter = converter(new TimeWithTimeZoneType());
		final OffsetTime expected = OffsetTime.of(12, 34, 56, 123_400_000, IST);
		assertEquals(expected, converter.convertObject("12:34:56.1234 +05:30"));
		assertEquals(expected, converter.convertObject("12:34:56.1234+05:30"));
		assertEquals(expected, converter.convertObject("12:34:56.1234 +0530"));
		assertEquals("12:34:56.1234 +05:30", converter.format(expected));
		assertEquals(OffsetTime.of(12, 34, 0, 0, IST), converter.convertObject("12:34 +05:30"));
		assertEquals(OffsetTime.of(12, 34, 56, 0, TOKYO), converter.convertObject("12:34:56"));
	}

	@Test
	void handlersUseJavaTimeValues() {
		assertInstanceOf(JavaTimeJdbcTypeHandler.class, new TimestampWithTimeZoneType().getJdbcTypeHandler());
		assertInstanceOf(JavaTimeJdbcTypeHandler.class, new TimeWithTimeZoneType().getJdbcTypeHandler());
	}

	@Test
	void handlerReadsTypedAndBindsUnchangedValue() throws Exception {
		final JdbcTypeHandler handler = new TimestampWithTimeZoneType().getJdbcTypeHandler();
		final OffsetDateTime value = OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 123_400_000, IST);
		final List<Object[]> calls = new ArrayList<>();
		final ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (p, m, a) -> {
					if (m.getName().equals("getObject") && a.length == 2 && a[1] == OffsetDateTime.class) {
						return value;
					}
					throw new AssertionError(m.getName());
				});
		assertSame(value, handler.getObject(rs, 1));
		assertSame(value, handler.getObject(rs, "TS"));
		final PreparedStatement stmt = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
					if (m.getName().equals("setObject") && a.length == 3 && a[2] instanceof Integer
							|| m.getName().equals("setNull") && a.length == 2) {
						calls.add(a);
						return null;
					}
					throw new AssertionError(m.getName());
				});
		handler.setObject(stmt, 1, value);
		handler.setObject(stmt, 2, "2026-11-20 12:34:56.1234 +05:30");
		handler.setObject(stmt, 3, null);
		assertSame(value, calls.get(0)[1]);
		assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, calls.get(0)[2]);
		assertEquals(value, calls.get(1)[1]);
		assertEquals(3, calls.get(2)[0]);
		assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, calls.get(2)[1]);
	}

	@Test
	void jdbcRoundTripKeepsOffsetAndFraction() throws Exception {
		final JdbcTypeHandler timestamp = new TimestampWithTimeZoneType().getJdbcTypeHandler();
		final JdbcTypeHandler time = new TimeWithTimeZoneType().getJdbcTypeHandler();
		final OffsetDateTime ts = OffsetDateTime.of(2026, 11, 20, 12, 34, 56, 123_456_789, IST);
		final OffsetTime tm = OffsetTime.of(12, 34, 56, 123_456_789, ZoneOffset.ofHours(-8));
		try (Connection connection = DriverManager
				.getConnection("jdbc:hsqldb:mem:tz_" + UUID.randomUUID().toString().replace("-", ""), "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE T (ID INT, TS TIMESTAMP(9) WITH TIME ZONE, TM TIME(9) WITH TIME ZONE)");
			}
			try (var insert = connection.prepareStatement("INSERT INTO T VALUES (?, ?, ?)")) {
				insert.setInt(1, 1);
				timestamp.setObject(insert, 2, ts);
				time.setObject(insert, 3, tm);
				insert.executeUpdate();
				insert.setInt(1, 2);
				timestamp.setObject(insert, 2, null);
				time.setObject(insert, 3, null);
				insert.executeUpdate();
			}
			try (var select = connection.prepareStatement("SELECT TS, TM FROM T ORDER BY ID");
					var rs = select.executeQuery()) {
				rs.next();
				assertEquals(ts, timestamp.getObject(rs, 1));
				assertEquals(tm, time.getObject(rs, "TM"));
				rs.next();
				assertNull(timestamp.getObject(rs, 1));
				assertNull(time.getObject(rs, 2));
			}
		}
	}
}
