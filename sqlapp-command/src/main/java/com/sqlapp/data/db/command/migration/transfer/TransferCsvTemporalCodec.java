/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * Strict local wall-clock CSV values; no guessed timezone or precision
 * reduction.
 */
final class TransferCsvTemporalCodec {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", java.util.Locale.ROOT);
	private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder()
			.append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').append(TIME)
			.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).toFormatter(java.util.Locale.ROOT)
			.withResolverStyle(java.time.format.ResolverStyle.STRICT);

	private TransferCsvTemporalCodec() {
	}

	static boolean temporal(Column column) {
		return switch (column.getDataType().getJdbcType()) {
		case DATE, TIME, TIMESTAMP, TIME_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE -> true;
		default -> false;
		};
	}

	static void validate(Column column) {
		final var type = column.getDataType();
		if (type == DataType.DATE)
			return;
		// The Schema model stores temporal fractional precision in length, not numeric
		// scale.
		if (type == DataType.TIMESTAMP && column.getLength() != null && column.getLength() >= 0
				&& column.getLength() <= 9)
			return;
		if (type == DataType.TIME && Long.valueOf(0).equals(column.getLength()))
			return;
		throw new IllegalArgumentException(
				"CSV temporal values require DATE, standard TIMESTAMP or TIME with explicitly zero fractional precision. "
						+ "Fractional TIME, timezone types and vendor temporal aliases require a separate conversion path.");
	}

	static boolean supported(Column column) {
		try {
			validate(column);
			return true;
		} catch (IllegalArgumentException failure) {
			return false;
		}
	}

	/**
	 * Use the driver's text accessor for export-only values, preserving fractional
	 * fields and offsets.
	 */
	static com.sqlapp.data.schemas.Table exportView(com.sqlapp.data.schemas.Table table,
			TransferJobConfiguration.TemporalPolicy policy) {
		final var view = table.clone();
		for (var column : view.getColumns())
			if (temporal(column) && !supported(column)) {
				if (policy == TransferJobConfiguration.TemporalPolicy.ERROR)
					validate(column);
				column.setDataType(DataType.VARCHAR).setDataTypeName(null).setLength(null).setScale(null);
			}
		return view;
	}

	static String encode(Column column, Object value) {
		validate(column);
		if (value == null)
			return null;
		final String result;
		if (column.getDataType() == DataType.DATE && value instanceof java.sql.Date date)
			result = date.toLocalDate().toString();
		else if (column.getDataType() == DataType.TIME && value instanceof java.sql.Time time)
			result = TIME.format(time.toLocalTime());
		else if (column.getDataType() == DataType.TIMESTAMP && value instanceof java.sql.Timestamp timestamp)
			result = TIMESTAMP.format(timestamp.toLocalDateTime());
		else
			throw new IllegalArgumentException("JDBC temporal value does not match its canonical Schema type.");
		decode(column, result); // Ensure this runtime can represent the local fields without normalization.
		return result;
	}

	static Object decode(Column column, String text) {
		validate(column);
		if (text == null)
			return null;
		if (column.getDataType() == DataType.DATE) {
			if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
				throw invalid();
			final var local = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
			if (local.getYear() == 0)
				throw invalid();
			final var value = java.sql.Date.valueOf(local);
			if (!value.toLocalDate().equals(local))
				throw invalid();
			return value;
		}
		if (column.getDataType() == DataType.TIME) {
			if (!text.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}"))
				throw invalid();
			final var local = LocalTime.parse(text, DateTimeFormatter.ISO_LOCAL_TIME);
			return java.sql.Time.valueOf(local);
		}
		if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?"))
			throw invalid();
		final var local = LocalDateTime.parse(text, TIMESTAMP);
		if (local.getYear() == 0)
			throw invalid();
		if (local.getNano() % (int) Math.pow(10, 9 - column.getLength()) != 0)
			throw invalid();
		final var value = java.sql.Timestamp.valueOf(local);
		if (!value.toLocalDateTime().equals(local))
			throw invalid();
		return value;
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException(
				"Invalid or unrepresentable local temporal CSV value; timezone inference and normalization are disabled.");
	}
}
