/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.alloydb.AlloyDB15;
import com.sqlapp.data.db.dialect.alloydb.AlloyDB16;
import com.sqlapp.data.db.dialect.alloydb.AlloyDB17;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

/** Resolves AlloyDB by PostgreSQL engine version without changing the connection. */
public class AlloyDBDialectResolver extends ProductNameDialectResolver {
	private static final Dialect[] ENGINES = { new AlloyDB15(), new AlloyDB16(), new AlloyDB17() };

	/** Creates the service-loaded resolver. */
	public AlloyDBDialectResolver() {
		super("(?:Google )?AlloyDB(?: for PostgreSQL| Omni)?|alloydb-omni", (major, minor, revision) -> engine(major));
	}

	private static Dialect engine(int major) {
		if (major < 15 || major > 17) {
			throw new IllegalArgumentException("Unsupported AlloyDB PostgreSQL engine major: " + major
					+ ". Expected 15 through 17; supply the PostgreSQL engine version.");
		}
		return ENGINES[major - 15];
	}

	@Override
	protected int order() {
		return -30;
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
				throw new IllegalStateException("AlloyDB identification requires a JDBC connection; use an explicit product name for offline resolution.");
			}
			// The registered columnar-engine flag exists even when the engine is disabled.
			// Do not invoke vendor functions or change settings to identify the product.
			try (var statement = connection.createStatement(); var rows = statement.executeQuery(
					"SELECT 1 FROM pg_catalog.pg_settings WHERE name = 'google_columnar_engine.enabled'")) {
				if (!rows.next()) {
					return null;
				}
			}
			return engine(metadata.getDatabaseMajorVersion());
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to identify AlloyDB from JDBC metadata", e);
		}
	}
}
