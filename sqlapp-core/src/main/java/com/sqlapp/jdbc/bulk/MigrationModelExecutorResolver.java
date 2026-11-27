/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/** Resolves one model executor through the dialect SPI. */
public final class MigrationModelExecutorResolver {
	private MigrationModelExecutorResolver() {
	}

	public static MigrationModelExecutor resolve(final Connection connection) throws SQLException {
		return resolve(DialectResolver.getInstance().getDialect(connection));
	}

	public static MigrationModelExecutor resolve(final Dialect dialect) {
		return find(dialect).orElseThrow(() -> new IllegalArgumentException(
				"No migration model executor supports: " + dialect.getProductName()
						+ ". Models currently run on DuckDB; add sqlapp-core-duckdb and the DuckDB JDBC driver"
						+ " to the runtime classpath and connect to a DuckDB database."));
	}

	public static Optional<MigrationModelExecutor> find(final Dialect dialect) {
		java.util.Objects.requireNonNull(dialect, "dialect");
		final List<MigrationModelExecutorProvider> providers = ServiceLoader
				.load(MigrationModelExecutorProvider.class).stream().map(ServiceLoader.Provider::get)
				.filter(provider -> provider.supports(dialect)).toList();
		if (providers.isEmpty()) {
			return Optional.empty();
		}
		if (providers.size() > 1) {
			throw new IllegalStateException("Multiple migration model executors support: " + dialect.getProductName()
					+ " (" + providers.stream().map(provider -> provider.getClass().getName()).toList() + ")");
		}
		return Optional.of(providers.getFirst().create(dialect));
	}
}
