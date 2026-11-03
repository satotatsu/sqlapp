/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.db2.i.Db2I75;

/** Uses DRDA server identifiers as well as explicit JDBC product names. */
public class Db2I75DialectResolver extends ProductNameDialectResolver {
	public Db2I75DialectResolver() {
		super("(?:DB2.*(?:AS/400|ISERIES|FOR (?:IBM )?I(?:\\s.*)?).*|db2-i)", (major, minor, revision) -> {
			if (major != 7 || minor != 5)
				throw new IllegalArgumentException(
						"Supported experimental target: Db2 for i 7.5; received " + major + "." + minor);
			return new Db2I75();
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
			if (version != null && version.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("QSQ"))
				return getDialect(metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion());
			if (match(name))
				return getDialect(metadata.getDatabaseMajorVersion(), metadata.getDatabaseMinorVersion());
			return null;
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot identify Db2 for i", e);
		}
	}
}
