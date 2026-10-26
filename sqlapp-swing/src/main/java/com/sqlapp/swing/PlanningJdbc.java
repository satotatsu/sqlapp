/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.sql.Connection;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Local connection setup shared by planning adapters; never persists
 * credentials.
 */
final class PlanningJdbc {
	record Spec(String jdbcUrl, String username, String driverClass) {
		Spec {
			if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
				throw new IllegalArgumentException("Enter a JDBC URL starting with jdbc:.");
			}
		}
	}

	private PlanningJdbc() {
	}

	static HikariDataSource open(final Spec spec, final char[] password) {
		return open(spec, password, true, 1);
	}

	static HikariDataSource openForExecution(final Spec spec, final char[] password) {
		return open(spec, password, false, 1);
	}

	static HikariDataSource openForDataExecution(final Spec spec, final char[] password) {
		// The database lease holds a separate connection while chunks execute.
		return open(spec, password, false, 2);
	}

	private static HikariDataSource open(final Spec spec, final char[] password, final boolean readOnly,
			final int size) {
		return open(spec, password, readOnly, size, JdbcDriverLibraries.SESSION);
	}

	static HikariDataSource open(final Spec spec, final char[] password, final boolean readOnly, final int size,
			final JdbcDriverLibraries libraries) {
		final var configuration = new HikariConfig();
		final var added = libraries.resolve(spec);
		if (added != null)
			configuration.setDataSource(new AddedDriverDataSource(added, spec, password));
		else
			configuration.setJdbcUrl(spec.jdbcUrl());
		if (spec.username() != null && !spec.username().isBlank()) {
			configuration.setUsername(spec.username());
		}
		configuration.setPassword(new String(password));
		if (added == null && spec.driverClass() != null && !spec.driverClass().isBlank()) {
			configuration.setDriverClassName(spec.driverClass().trim());
		}
		configuration.setReadOnly(readOnly);
		configuration.setMaximumPoolSize(size);
		configuration.setMinimumIdle(0);
		configuration.setConnectionTimeout(10_000);
		configuration.setInitializationFailTimeout(-1);
		return new HikariDataSource(configuration);
	}

	static Dialect requireDialect(final Connection connection) {
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		if (dialect == null || dialect.getClass() == Dialect.class) {
			throw new IllegalArgumentException("No supported sqlapp dialect found for this database.");
		}
		return dialect;
	}
}
