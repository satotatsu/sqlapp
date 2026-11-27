/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;

/** Resolves database-specific bulk insert implementations through SPI. */
public final class BulkInsertResolver {
	private BulkInsertResolver() {
	}

	public static BulkInsertExecutor resolve(final Connection connection)
			throws SQLException {
		Objects.requireNonNull(connection, "connection");
		return resolve(DialectResolver.getInstance().getDialect(connection));
	}

	public static BulkInsertExecutor resolve(final Dialect dialect) {
		return resolve(dialect, BulkInsertMethod.NATIVE);
	}

	public static BulkInsertExecutor resolve(final Connection connection, final BulkOption options) throws SQLException {
		Objects.requireNonNull(connection, "connection");
		return resolve(DialectResolver.getInstance().getDialect(connection),
				options == null ? BulkInsertMethod.AUTO : options.getInsertMethod());
	}

	public static BulkInsertExecutor resolve(final Dialect dialect, final BulkInsertMethod method) {
		Objects.requireNonNull(dialect, "dialect");
		Objects.requireNonNull(method, "method");
		if (method == BulkInsertMethod.JDBC_BATCH) return new JdbcBatchBulkInsertExecutor(dialect);
		final List<BulkInsertProvider> providers = ServiceLoader
				.load(BulkInsertProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(provider -> provider.supports(dialect)).toList();
		if (providers.isEmpty()) {
			if (method == BulkInsertMethod.AUTO) return new JdbcBatchBulkInsertExecutor(dialect);
			throw new IllegalArgumentException(
					"No bulk insert provider supports: " + dialect.getProductName());
		}
		if (providers.size() > 1) {
			throw new IllegalStateException(
					"Multiple bulk insert providers support: "
							+ dialect.getProductName() + " ("
							+ providers.stream().map(p -> p.getClass().getName())
									.toList() + ")");
		}
		return providers.get(0).create(dialect);
	}

	public static long execute(final Connection connection, final Table table,
			final BulkOption options) throws SQLException {
		return resolve(connection, options).execute(connection,
				Objects.requireNonNull(table, "table"),
				options == null ? BulkOption.defaults() : options);
	}
}
