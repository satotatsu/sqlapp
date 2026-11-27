/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.aurora.mysql.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.aurora.mysql.AuroraMySql80;
import com.sqlapp.data.db.dialect.aurora.mysql.AuroraMySql84;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

/** Resolves MySQL compatibility versions separately from Aurora releases. */
public class AuroraMySqlDialectResolver extends ProductNameDialectResolver {
	private static final Dialect MYSQL80 = new AuroraMySql80();
	private static final Dialect MYSQL84 = new AuroraMySql84();

	/** Creates the resolver for Aurora MySQL 3.x and 8.4. */
	public AuroraMySqlDialectResolver() {
		super("(?:Amazon )?Aurora MySQL|aurora-mysql", (major, minor, revision) -> engine(major, minor));
	}

	private static Dialect engine(int major, int minor) {
		if (major == 8 && minor == 0) {
			return MYSQL80;
		}
		if (major == 8 && minor == 4) {
			return MYSQL84;
		}
		throw new IllegalArgumentException("Unsupported Aurora MySQL compatibility version: " + major + "." + minor
				+ ". Expected MySQL 8.0 (Aurora 3.x) or 8.4; do not supply the Aurora 3.x release number.");
	}

	@Override
	protected int order() {
		return -20;
	}

	@Override
	public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String product = metadata.getDatabaseProductName();
			if (match(product)) {
				return engine(metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion());
			}
			if (!"MySQL".equalsIgnoreCase(product)) {
				return null;
			}
			var connection = metadata.getConnection();
			if (connection == null) {
				throw new IllegalStateException(
						"Aurora identification requires a JDBC connection; use an explicit Aurora MySQL name offline.");
			}
			// WHERE uses exact equality: '_' in a LIKE pattern would be a wildcard.
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SHOW VARIABLES WHERE Variable_name = 'aurora_version'")) {
				if (!rows.next()) {
					return null;
				}
				String release = rows.getString(2);
				int major = metadata.getDatabaseMajorVersion();
				int minor = metadata.getDatabaseMinorVersion();
				if (release == null || !(major == 8 && minor == 0 && release.matches("3\\.\\d+\\.\\d+")
						|| major == 8 && minor == 4 && release.matches("8\\.4\\.\\d+"))) {
					throw new IllegalStateException("Unsupported or inconsistent Aurora MySQL release: " + release
							+ " with MySQL " + major + "." + minor);
				}
				return engine(major, minor);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to identify Aurora MySQL from JDBC metadata", e);
		}
	}
}
