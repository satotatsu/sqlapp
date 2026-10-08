/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte11;
import com.sqlapp.data.db.dialect.yugabyte.Yugabyte15;

/** Resolves YSQL by PostgreSQL engine version, never by YugabyteDB release number. */
public class YugabyteDialectResolver extends ProductNameDialectResolver {
	private static final Pattern VERSION = Pattern.compile(
			"^(?:PostgreSQL\\s+)?([0-9]+)\\.[0-9]+(?:\\.[0-9]+)?-YB-.*", Pattern.CASE_INSENSITIVE);
	private static final Dialect PG11 = new Yugabyte11();
	private static final Dialect PG15 = new Yugabyte15();

	public YugabyteDialectResolver() {
		super("YugabyteDB(?: YSQL)?|YSQL", (major, minor, revision) -> resolveEngine(major));
	}

	private static Dialect resolveEngine(int major) {
		return switch (major) {
		case 11 -> PG11;
		case 15 -> PG15;
		default -> throw new IllegalArgumentException("Unsupported YSQL PostgreSQL engine version: " + major
				+ ". Expected 11 or 15; do not supply the YugabyteDB product release number.");
		};
	}

	@Override
	public Dialect resolveDatabaseMetaData(DatabaseMetaData metadata) {
		try {
			String name = metadata.getDatabaseProductName();
			if (!"PostgreSQL".equalsIgnoreCase(name) && !match(name)) {
				return null;
			}
			String version = metadata.getDatabaseProductVersion();
			Matcher matcher = VERSION.matcher(version == null ? "" : version);
			if (matcher.matches()) {
				return resolveEngine(Integer.parseInt(matcher.group(1)));
			}
			if (match(name)) {
				throw new IllegalArgumentException("Cannot identify YSQL engine version from JDBC version: "
						+ version + ". Expected a PostgreSQL engine version containing -YB-.");
			}
			return null;
		} catch (SQLException e) {
			throw new IllegalStateException("Failed to identify YugabyteDB YSQL from JDBC metadata", e);
		}
	}
}
