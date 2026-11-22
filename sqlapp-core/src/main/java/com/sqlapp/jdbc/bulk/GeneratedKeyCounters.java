/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Raises identity/serial generators after keys were imported with explicit
 * values. The required next value is the later (for a negative increment, the
 * smaller) of the source model's next value and the imported key extreme plus
 * the increment. A generator is never moved back from a value the database
 * reports; providers document catalogs that report a restart late (Db2).
 */
public final class GeneratedKeyCounters {
	private GeneratedKeyCounters() {
	}

	/** How a column was handled. */
	public enum Action {
		/** The generator was restarted at {@link Result#next()}. */
		RESTARTED,
		/** The generator was already at or beyond the required value. */
		ALREADY_SAFE,
		/** The database advances the counter past inserted keys by itself. */
		FOLLOWS_INSERTED_KEYS,
		/** The target column has no generator. */
		NO_GENERATOR,
		/** No provider, or the provider requires an explicit request. */
		UNSUPPORTED
	}

	public record Result(String table, String column, Action action, BigInteger before, BigInteger next,
			List<String> statements) {
		public Result {
			statements = List.copyOf(statements);
		}
	}

	/**
	 * @param explicit whether synchronization was requested explicitly (needed by
	 *                 providers such as Db2)
	 * @return whether {@link #synchronize} acts on this dialect
	 */
	public static boolean supported(final Dialect dialect, final boolean explicit) {
		final Optional<GeneratedKeyCounterProvider> provider = GeneratedKeyCounterProvider.find(dialect);
		return provider.isPresent() && (explicit || !provider.get().requiresExplicitRequest());
	}

	/**
	 * Synchronizes one identity/serial column of a populated target table.
	 *
	 * @param sourceNext the source model's next value ({@code identityLastValue}),
	 *                   or null
	 * @param explicit   whether synchronization was requested explicitly
	 */
	public static Result synchronize(final Connection connection, final Table table, final Column column,
			final Long sourceNext, final boolean explicit) throws SQLException {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		final Optional<GeneratedKeyCounterProvider> found = GeneratedKeyCounterProvider.find(dialect);
		if (found.isEmpty() || found.get().requiresExplicitRequest() && !explicit) {
			return new Result(table.getName(), column.getName(), Action.UNSUPPORTED, null, null, List.of());
		}
		final GeneratedKeyCounterProvider provider = found.get();
		if (provider.followsInsertedKeys()) {
			return new Result(table.getName(), column.getName(), Action.FOLLOWS_INSERTED_KEYS, null, null,
					List.of());
		}
		if (!provider.hasGenerator(connection, dialect, table, column)) {
			return new Result(table.getName(), column.getName(), Action.NO_GENERATOR, null, null, List.of());
		}
		final BigInteger step = BigInteger.valueOf(column.getIdentityStep() == null ? 1L : column.getIdentityStep());
		if (step.signum() == 0) {
			throw new IllegalArgumentException(
					"Identity increment must not be zero: " + table.getName() + "." + column.getName());
		}
		final boolean ascending = step.signum() > 0;
		BigInteger required = sourceNext == null ? null : BigInteger.valueOf(sourceNext);
		final BigInteger extreme = extreme(connection, dialect, table, column, ascending);
		if (extreme != null) {
			required = later(required, extreme.add(step), ascending);
		}
		final BigInteger current = provider.currentNext(connection, dialect, table, column);
		if (required == null || current != null && !isBeyond(required, current, ascending)) {
			return new Result(table.getName(), column.getName(), Action.ALREADY_SAFE, current, current, List.of());
		}
		final List<String> statements = provider.restart(connection, dialect, table, column, required);
		return new Result(table.getName(), column.getName(), Action.RESTARTED, current, required, statements);
	}

	private static BigInteger extreme(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final boolean ascending) throws SQLException {
		final var builder = dialect.createSqlBuilder();
		builder._add(ascending ? "SELECT MAX(" : "SELECT MIN(").name(column)._add(") FROM ").name(table, true);
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(builder.toString())) {
			if (!rows.next()) {
				return null;
			}
			final var value = rows.getBigDecimal(1);
			return value == null ? null : value.toBigIntegerExact();
		}
	}

	private static BigInteger later(final BigInteger a, final BigInteger b, final boolean ascending) {
		if (a == null) {
			return b;
		}
		return isBeyond(a, b, ascending) ? a : b;
	}

	private static boolean isBeyond(final BigInteger value, final BigInteger than, final boolean ascending) {
		return ascending ? value.compareTo(than) > 0 : value.compareTo(than) < 0;
	}
}
