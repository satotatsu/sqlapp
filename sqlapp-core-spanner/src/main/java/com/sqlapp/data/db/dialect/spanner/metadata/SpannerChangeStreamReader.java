/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.spanner.sql.SpannerChangeStream;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerChangeStream.*;

/**
 * Reads visible GoogleSQL change stream definitions with a fixed number of
 * catalog queries.
 */
public final class SpannerChangeStreamReader {
	private static final Pattern RETENTION = Pattern.compile("([0-9]+)([smhd])");

	public List<SpannerChangeStream> read(final Connection connection) throws SQLException {
		return readIfSupported(connection).orElseGet(List::of);
	}

	public Optional<List<SpannerChangeStream>> readIfSupported(final Connection connection) throws SQLException {
		final Map<String, Definition> streams = new LinkedHashMap<>();
		try {
			query(connection, "SELECT CHANGE_STREAM_NAME, `ALL` FROM INFORMATION_SCHEMA.CHANGE_STREAMS "
					+ "ORDER BY CHANGE_STREAM_NAME", rows -> {
						final String name = rows.getString(1);
						if (streams.putIfAbsent(name, new Definition(flag(rows, 2))) != null)
							throw new SQLException("Duplicate Spanner change stream: " + name);
					});
		} catch (SQLException exception) {
			if (SpannerRoleReader.isMissingCatalogTable(exception, "CHANGE_STREAMS"))
				return Optional.empty();
			throw exception;
		}
		if (streams.isEmpty())
			return Optional.of(List.of());
		query(connection, "SELECT CHANGE_STREAM_NAME, TABLE_SCHEMA, TABLE_NAME, ALL_COLUMNS "
				+ "FROM INFORMATION_SCHEMA.CHANGE_STREAM_TABLES ORDER BY CHANGE_STREAM_NAME, TABLE_SCHEMA, TABLE_NAME",
				rows -> {
					final var definition = definition(streams, rows);
					if (definition.all
							|| definition.targets.putIfAbsent(table(rows), new WatchedTable(flag(rows, 4))) != null)
						throw new SQLException("Conflicting Spanner change stream table metadata");
				});
		query(connection, """
				SELECT c.CHANGE_STREAM_NAME, c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME
				FROM INFORMATION_SCHEMA.CHANGE_STREAM_COLUMNS c
				WHERE NOT EXISTS (
				  SELECT 1 FROM INFORMATION_SCHEMA.INDEX_COLUMNS k
				  WHERE k.INDEX_NAME = 'PRIMARY_KEY' AND k.TABLE_SCHEMA = c.TABLE_SCHEMA
				    AND k.TABLE_NAME = c.TABLE_NAME AND k.COLUMN_NAME = c.COLUMN_NAME
				)
				ORDER BY c.CHANGE_STREAM_NAME, c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME
				""", rows -> {
			final var watched = definition(streams, rows).targets.get(table(rows));
			if (watched == null || watched.all)
				throw new SQLException("Unexpected Spanner change stream column metadata");
			watched.columns.add(rows.getString(4));
		});
		query(connection,
				"SELECT CHANGE_STREAM_NAME, OPTION_NAME, OPTION_VALUE "
						+ "FROM INFORMATION_SCHEMA.CHANGE_STREAM_OPTIONS ORDER BY CHANGE_STREAM_NAME, OPTION_NAME",
				rows -> definition(streams, rows).option(rows.getString(2), rows.getString(3)));
		return Optional.of(streams.entrySet().stream().map(entry -> entry.getValue().build(entry.getKey())).toList());
	}

	private static boolean flag(final ResultSet rows, final int index) throws SQLException {
		final boolean value = rows.getBoolean(index);
		if (rows.wasNull())
			throw new SQLException("Missing Spanner change stream scope flag");
		return value;
	}

	private static Definition definition(final Map<String, Definition> streams, final ResultSet rows)
			throws SQLException {
		final var definition = streams.get(rows.getString(1));
		if (definition == null)
			throw new SQLException("Change stream metadata refers to an unknown stream");
		return definition;
	}

	private static TableName table(final ResultSet rows) throws SQLException {
		return new TableName(rows.getString(2), rows.getString(3));
	}

	private record TableName(String schema, String name) {
	}

	private static final class WatchedTable {
		private final boolean all;
		private final List<String> columns = new ArrayList<>();

		private WatchedTable(final boolean all) {
			this.all = all;
		}
	}

	private static final class Definition {
		private final boolean all;
		private final Map<TableName, WatchedTable> targets = new LinkedHashMap<>();
		private final Map<Filter, Boolean> filters = new EnumMap<>(Filter.class);
		private Duration retention;
		private Capture capture;

		private Definition(final boolean all) {
			this.all = all;
		}

		private void option(final String name, final String literal) throws SQLException {
			if (name == null || literal == null)
				throw new SQLException("Missing Spanner change stream option name or value");
			final String value = literal.replaceAll("^(['\"])(.*)\\1$", "$2");
			try {
				switch (name.toLowerCase(Locale.ROOT)) {
				case "retention_period" -> retention = retention(value);
				case "value_capture_type" -> capture = Capture.valueOf(value);
				default -> {
					final var filter = Filter.valueOf(name.toUpperCase(Locale.ROOT));
					if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
						throw new IllegalArgumentException("Expected a boolean filter");
					filters.put(filter, Boolean.valueOf(value));
				}
				}
			} catch (IllegalArgumentException | ArithmeticException exception) {
				throw new SQLException("Unsupported Spanner change stream option: " + name + "=" + literal, exception);
			}
		}

		private SpannerChangeStream build(final String name) {
			final var scope = all ? Scope.ALL : targets.isEmpty() ? Scope.NONE : Scope.TABLES;
			final var watched = targets.entrySet().stream().map(entry -> new Target(entry.getKey().schema(),
					entry.getKey().name(), entry.getValue().all ? null : entry.getValue().columns)).toList();
			return new SpannerChangeStream(name, scope, watched, new Options(retention, capture, filters));
		}
	}

	private static Duration retention(final String text) {
		final var matcher = RETENTION.matcher(text);
		if (!matcher.matches())
			throw new IllegalArgumentException("Invalid change stream retention: " + text);
		final long amount = Long.parseLong(matcher.group(1));
		return switch (matcher.group(2)) {
		case "d" -> Duration.ofDays(amount);
		case "h" -> Duration.ofHours(amount);
		case "m" -> Duration.ofMinutes(amount);
		default -> Duration.ofSeconds(amount);
		};
	}

	@FunctionalInterface
	private interface RowConsumer {
		void accept(ResultSet rows) throws SQLException;
	}

	private static void query(final Connection connection, final String sql, final RowConsumer consumer)
			throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			while (rows.next())
				consumer.accept(rows);
		}
	}
}
