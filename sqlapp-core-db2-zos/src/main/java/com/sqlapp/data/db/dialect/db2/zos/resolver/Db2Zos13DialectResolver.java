/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.db2.zos.Db2Zos13;

/**
 * Uses DRDA server identifiers as well as explicit JDBC product names. Resolves
 * Db2 12 and 13 for z/OS; other releases are rejected.
 */
public class Db2Zos13DialectResolver extends ProductNameDialectResolver {
	public Db2Zos13DialectResolver() {
		super("(?:DB2.*(?:Z/OS|OS/390).*|db2-zos)", (major, minor, revision) -> {
			if (major == 12)
				return new com.sqlapp.data.db.dialect.db2.zos.Db2Zos12();
			if (major == 13)
				return new Db2Zos13();
			throw new IllegalArgumentException(
					"Supported experimental targets: Db2 12 and 13 for z/OS; received " + major + "." + minor);
		});
	}

	@Override
	protected int order() {
		return -100;
	}

	@Override
	public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String name = metadata.getDatabaseProductName();
			if (name == null || !name.toUpperCase(java.util.Locale.ROOT).startsWith("DB2"))
				return null;
			String version = metadata.getDatabaseProductVersion();
			if (version != null && version.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("DSN"))
				return getDialect(metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion());
			if (match(name))
				return getDialect(metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion());
			return null;
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot identify Db2 for z/OS", e);
		}
	}
}
