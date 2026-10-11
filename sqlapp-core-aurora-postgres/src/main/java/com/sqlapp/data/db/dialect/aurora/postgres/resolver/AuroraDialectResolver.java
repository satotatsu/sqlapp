/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.postgres.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgres14;
import com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgres15;
import com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgres16;
import com.sqlapp.data.db.dialect.aurora.postgres.AuroraPostgres17;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

/**
 * Identifies Aurora without invoking missing functions on ordinary PostgreSQL.
 */
public class AuroraDialectResolver extends ProductNameDialectResolver {
	private static final Dialect[] ENGINES = { new AuroraPostgres14(), new AuroraPostgres15(), new AuroraPostgres16(),
			new AuroraPostgres17() };

	public AuroraDialectResolver() {
		super("(?:Amazon )?Aurora PostgreSQL|aurora-postgresql", (major, minor, revision) -> engine(major));
	}

	private static Dialect engine(int major) {
		if (major < 14 || major > 17) {
			throw new IllegalArgumentException("Unsupported Aurora PostgreSQL engine major: " + major
					+ ". Expected 14 through 17, using the PostgreSQL engine version.");
		}
		return ENGINES[major - 14];
	}

	@Override
	protected int order() {
		return -20;
	}

	@Override
	public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String name = metadata.getDatabaseProductName();
			if (match(name)) {
				return engine(metadata.getDatabaseMajorVersion());
			}
			if (!"PostgreSQL".equalsIgnoreCase(name)) {
				return null;
			}
			String version = metadata.getDatabaseProductVersion();
			if (version != null && (version.contains("-YB-") || version.contains("CockroachDB"))) {
				return null;
			}
			var connection = metadata.getConnection();
			if (connection == null) {
				throw new IllegalStateException(
						"Aurora identification requires a JDBC connection; use an explicit product name for offline resolution.");
			}
			// AWS pg-collector uses this setting to guard aurora_version(). No
			// missing-function error
			// may abort a caller-owned transaction on ordinary PostgreSQL or RDS
			// PostgreSQL.
			try (var statement = connection.createStatement();
					var rows = statement
							.executeQuery("SELECT 1 FROM pg_catalog.pg_settings WHERE name = 'rds.extensions' "
									+ "AND setting LIKE '%aurora_stat_utils%'")) {
				if (!rows.next()) {
					return null;
				}
			}
			String namespace;
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery(
							"SELECT n.nspname FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
									+ "WHERE p.proname = 'aurora_version' AND p.pronargs = 0 AND n.nspname IN ('pg_catalog', 'public') "
									+ "ORDER BY n.nspname = 'pg_catalog' DESC")) {
				if (!rows.next()) {
					throw new IllegalStateException("Aurora marker exists but aurora_version() is unavailable.");
				}
				namespace = rows.getString(1);
			}
			if (!"pg_catalog".equals(namespace) && !"public".equals(namespace)) {
				throw new IllegalStateException("Unexpected Aurora function namespace");
			}
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT " + namespace + ".aurora_version()")) {
				if (!rows.next() || rows.getString(1) == null || rows.getString(1).isBlank()) {
					throw new IllegalStateException("aurora_version() returned no version");
				}
			}
			return engine(metadata.getDatabaseMajorVersion());
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to identify Aurora PostgreSQL from JDBC metadata", e);
		}
	}
}
