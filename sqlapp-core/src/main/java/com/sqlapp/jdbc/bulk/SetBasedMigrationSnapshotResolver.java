/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/** Resolves one set-based SCD2 implementation through the dialect SPI. */
public final class SetBasedMigrationSnapshotResolver {
	private SetBasedMigrationSnapshotResolver() {
	}

	public static SetBasedMigrationSnapshotExecutor resolve(final Connection connection) throws SQLException {
		return resolve(DialectResolver.getInstance().getDialect(connection));
	}

	public static SetBasedMigrationSnapshotExecutor resolve(final Dialect dialect) {
		return find(dialect).orElseThrow(() -> new IllegalArgumentException(
				"No set-based snapshot provider supports: " + dialect.getProductName()));
	}

	/** Returns empty when callers should use the streaming JDBC fallback. */
	public static Optional<SetBasedMigrationSnapshotExecutor> find(final Connection connection) throws SQLException {
		return find(DialectResolver.getInstance().getDialect(connection));
	}

	/** Returns empty when the dialect intentionally has no atomic set-based provider. */
	public static Optional<SetBasedMigrationSnapshotExecutor> find(final Dialect dialect) {
		java.util.Objects.requireNonNull(dialect, "dialect");
		final List<SetBasedMigrationSnapshotProvider> providers = ServiceLoader
				.load(SetBasedMigrationSnapshotProvider.class).stream().map(ServiceLoader.Provider::get)
				.filter(provider -> provider.supports(dialect)).toList();
		if (providers.isEmpty()) {
			return Optional.empty();
		}
		if (providers.size() > 1) {
			throw new IllegalStateException("Multiple set-based snapshot providers support: " + dialect.getProductName()
					+ " (" + providers.stream().map(provider -> provider.getClass().getName()).toList() + ")");
		}
		return Optional.of(providers.getFirst().create(dialect));
	}
}
