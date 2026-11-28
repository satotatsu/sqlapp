/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import com.sqlapp.data.converter.AbstractConverter;

/** Phoenix DATE and TIME both retain an epoch millisecond value. */
public final class PhoenixTemporalConverter extends AbstractConverter<Date> {

	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
			.withZone(ZoneOffset.UTC);
	private final boolean time;

	public PhoenixTemporalConverter(boolean time) {
		this.time = time;
	}

	@Override
	public Date convertObject(Object value) {
		if (isSupplier(value))
			return convertObject(getSupplierValue(value));
		if (value == null)
			return getDefaultValue();
		final Instant instant;
		if (value instanceof Date date) {
			if (date instanceof java.sql.Timestamp timestamp && timestamp.getNanos() % 1_000_000 != 0)
				throw new IllegalArgumentException("Phoenix DATE/TIME cannot retain sub-millisecond precision");
			instant = Instant.ofEpochMilli(date.getTime());
		} else if (value instanceof java.util.Calendar calendar) {
			instant = Instant.ofEpochMilli(calendar.getTimeInMillis());
		} else if (value instanceof Long millis) {
			instant = Instant.ofEpochMilli(millis);
		} else if (value instanceof Instant input) {
			instant = input;
		} else if (value instanceof java.time.OffsetDateTime input) {
			instant = input.toInstant();
		} else if (value instanceof java.time.ZonedDateTime input) {
			instant = input.toInstant();
		} else if (value instanceof LocalDateTime input) {
			instant = input.toInstant(ZoneOffset.UTC);
		} else if (value instanceof LocalDate input) {
			instant = input.atStartOfDay().toInstant(ZoneOffset.UTC);
		} else if (value instanceof LocalTime input) {
			instant = input.atDate(LocalDate.ofEpochDay(0)).toInstant(ZoneOffset.UTC);
		} else if (value instanceof String text) {
			if (text.isBlank())
				return getDefaultValue();
			try {
				final String normalized = text.strip().replace(' ', 'T');
				if (normalized.length() == 10)
					return convertObject(LocalDate.parse(normalized));
				if (!normalized.contains("T"))
					return convertObject(LocalTime.parse(normalized));
				return convertObject(DateTimeFormatter.ISO_DATE_TIME.parseBest(normalized,
						java.time.ZonedDateTime::from, LocalDateTime::from));
			} catch (java.time.format.DateTimeParseException e) {
				throw new IllegalArgumentException("Invalid Phoenix DATE/TIME value: " + text, e);
			}
		} else {
			throw new IllegalArgumentException("Unsupported Phoenix DATE/TIME value: " + value.getClass().getName());
		}
		if (instant.getNano() % 1_000_000 != 0)
			throw new IllegalArgumentException("Phoenix DATE/TIME cannot retain sub-millisecond precision");
		return time ? new java.sql.Time(instant.toEpochMilli()) : new java.sql.Date(instant.toEpochMilli());
	}

	@Override
	public String format(Date value) {
		return value == null ? null : FORMAT.format(Instant.ofEpochMilli(value.getTime()));
	}

	@Override
	public Date copy(Object value) {
		return convertObject(value);
	}
}
