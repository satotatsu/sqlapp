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

/** Resolves database-specific bulk upsert implementations through SPI. */
public final class BulkUpsertResolver {
	private BulkUpsertResolver() {
	}

	public static BulkUpsertExecutor resolve(final Connection connection)
			throws SQLException {
		Objects.requireNonNull(connection, "connection");
		return resolve(DialectResolver.getInstance().getDialect(connection));
	}

	public static BulkUpsertExecutor resolve(final Dialect dialect) {
		Objects.requireNonNull(dialect, "dialect");
		final List<BulkUpsertProvider> providers = ServiceLoader
				.load(BulkUpsertProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(provider -> provider.supports(dialect)).toList();
		if (providers.isEmpty()) {
			throw new IllegalArgumentException(
					"No bulk upsert provider supports: " + dialect.getProductName());
		}
		if (providers.size() > 1) {
			throw new IllegalStateException(
					"Multiple bulk upsert providers support: "
							+ dialect.getProductName() + " ("
							+ providers.stream().map(p -> p.getClass().getName())
									.toList()
							+ ")");
		}
		return providers.get(0).create(dialect);
	}

	public static long execute(final Connection connection, final Table table,
			final BulkUpsertOption options) throws SQLException {
		return resolve(connection).execute(connection,
				Objects.requireNonNull(table, "table"),
				options == null ? BulkUpsertOption.defaults() : options);
	}
}
