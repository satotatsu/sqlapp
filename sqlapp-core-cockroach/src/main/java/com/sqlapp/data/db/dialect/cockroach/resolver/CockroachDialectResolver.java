/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.cockroach.CockroachDB;

/** Recognizes the Cockroach release banner independently of PostgreSQL server_version. */
public class CockroachDialectResolver extends ProductNameDialectResolver {
	private static final Dialect DIALECT = new CockroachDB();
	private static final Pattern VERSION = Pattern.compile("^CockroachDB.*?v([0-9]+)\\.([0-9]+)\\..*", Pattern.CASE_INSENSITIVE);
	public CockroachDialectResolver() {
		super("Cockroach(?:DB)?", (major,minor,revision) -> resolve(major,minor));
	}
	private static Dialect resolve(int major, int minor) {
		if (major < 24 || major == 24 && minor < 3)
			throw new IllegalArgumentException("CockroachDB dialect requires release 24.3 or later");
		return DIALECT;
	}
	@Override public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String product = metadata.getDatabaseProductName();
			if (!"PostgreSQL".equalsIgnoreCase(product) && !match(product)) return null;
			String version = metadata.getDatabaseProductVersion();
			var matcher = VERSION.matcher(version == null ? "" : version);
			if (matcher.matches()) return resolve(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
			// Some PostgreSQL drivers expose only server_version here. version() is authoritative.
			if (metadata.getConnection() != null) {
				try (var sql = metadata.getConnection().createStatement(); var rows = sql.executeQuery("SELECT version()")) {
					if (rows.next()) {
						matcher = VERSION.matcher(rows.getString(1));
						if (matcher.matches()) return resolve(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
					}
				}
			}
			if (match(product)) throw new IllegalArgumentException("Cannot identify CockroachDB release: " + version);
			return null;
		} catch (SQLException e) { throw new IllegalStateException("Failed to identify CockroachDB", e); }
	}
}
