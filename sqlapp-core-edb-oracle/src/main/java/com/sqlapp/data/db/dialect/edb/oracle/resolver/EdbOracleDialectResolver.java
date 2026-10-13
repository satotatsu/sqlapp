/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.edb.oracle.*;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

/** Identifies Advanced Server without claiming other EDB distributions. */
public class EdbOracleDialectResolver extends ProductNameDialectResolver {
	private static final Dialect[] ENGINES = {new EdbOracle14(), new EdbOracle15(), new EdbOracle16(), new EdbOracle17(), new EdbOracle18()};
	private static final Pattern BANNER = Pattern.compile("(?is).*(?:EnterpriseDB\\s+[0-9]+[.]|(?:EDB|EnterpriseDB)\\s+(?:Postgres\\s+)?Advanced\\s+Server).*" );
	public EdbOracleDialectResolver() {
		super("EDB Postgres Advanced Server|EnterpriseDB(?: Advanced Server)?|EPAS|edb-oracle", (major, minor, revision) -> engine(major));
	}
	private static Dialect engine(int major) {
		if (major < 14 || major > 18) {
			throw new IllegalArgumentException("Unsupported EDB Postgres Advanced Server major: " + major + ". Expected 14 through 18.");
		}
		return ENGINES[major - 14];
	}
	@Override protected int order() { return -40; }
	@Override public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String product = metadata.getDatabaseProductName();
			if (match(product)) return engine(metadata.getDatabaseMajorVersion());
			if (!"PostgreSQL".equalsIgnoreCase(product)) return null;
			String version = metadata.getDatabaseProductVersion();
			if (version != null && BANNER.matcher(version).matches()) return engine(metadata.getDatabaseMajorVersion());
			if (version != null && (version.contains("-YB-") || version.contains("CockroachDB"))) return null;
			var connection = metadata.getConnection();
			if (connection == null) {
				throw new IllegalStateException("EDB identification requires a JDBC connection; use an explicit product name for offline resolution.");
			}
			// version() exists on PostgreSQL; do not call potentially absent EDB functions.
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT pg_catalog.version()")) {
				if (!rows.next()) throw new IllegalStateException("Database version query returned no row");
				String banner = rows.getString(1);
				if (banner == null || banner.isBlank()) throw new IllegalStateException("Database version query returned an empty version");
				return BANNER.matcher(banner).matches() ? engine(metadata.getDatabaseMajorVersion()) : null;
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to identify EDB Postgres Advanced Server", e);
		}
	}
}
