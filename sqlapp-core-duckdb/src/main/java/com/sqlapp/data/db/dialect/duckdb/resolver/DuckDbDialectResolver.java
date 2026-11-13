/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.resolver;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.resolver.VersionResolver;

/**
 * Dialect resolver for DuckDB.
 * <p>
 * The DuckDB JDBC driver reports the library version as
 * {@code getDatabaseProductVersion()} (for example {@code v1.4.4}). The
 * version is parsed from that string so that the resolution does not depend on
 * {@code getDatabaseMajorVersion()} / {@code getDatabaseMinorVersion()}.
 * </p>
 */
public class DuckDbDialectResolver extends ProductNameDialectResolver {

	private static final Pattern PRODUCT_NAME = Pattern.compile("DuckDB", Pattern.CASE_INSENSITIVE);

	private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*");

	public DuckDbDialectResolver() {
		super("DuckDB", new DuckDbVersionResolver());
	}

	@Override
	public Dialect resolveDatabaseMetaData(final DatabaseMetaData metadata) {
		try {
			final String productName = metadata.getDatabaseProductName();
			if (productName == null || !PRODUCT_NAME.matcher(productName.trim()).matches()) {
				return null;
			}
			final String version = metadata.getDatabaseProductVersion();
			if (version == null) {
				return null;
			}
			final Matcher matcher = VERSION.matcher(version.trim());
			if (!matcher.matches()) {
				return null;
			}
			final Integer revision = matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3));
			return getDialect(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), revision);
		} catch (final SQLException e) {
			throw new IllegalStateException("Failed to read DuckDB product metadata", e);
		}
	}

	/**
	 * Version resolver for DuckDB.
	 */
	static class DuckDbVersionResolver implements VersionResolver {

		private static final long serialVersionUID = 1L;

		@Override
		public Dialect getDialect(final int majorVersion, final int minorVersion, final Integer revision) {
			if (majorVersion > 1) {
				return DialectHolder.duckdb14;
			}
			if (majorVersion < 1) {
				return DialectHolder.duckdb10;
			}
			if (minorVersion >= 4) {
				return DialectHolder.duckdb14;
			}
			if (minorVersion >= 2) {
				return DialectHolder.duckdb12;
			}
			if (minorVersion >= 1) {
				return DialectHolder.duckdb11;
			}
			return DialectHolder.duckdb10;
		}
	}
}
