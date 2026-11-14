/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 *
 * sqlapp-core-duckdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-duckdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-duckdb.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.duckdb;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.metadata.DuckDbCatalogReader;
import com.sqlapp.data.db.dialect.duckdb.sql.DuckDbSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbJdbcTypeHandlers;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * DuckDB 1.0 baseline dialect.
 * <p>
 * DuckDB keeps identifier case as written but resolves unquoted identifiers
 * case-insensitively. A database file (or in-memory database) is exposed as a
 * catalog and contains schemas; the default schema is {@code main}. Newer
 * capabilities are enabled by the version subclasses in {@link DuckDbVersions}.
 * </p>
 */
public class DuckDb extends Dialect {

	private static final long serialVersionUID = 1L;

	/**
	 * Keywords that DuckDB 1.0 to 2.0 reject as unquoted table, column, index or
	 * alias names (verified against each release).
	 */
	private static final Set<String> RESERVED_KEYWORDS = Set
			.of(("ALL ANALYSE ANALYZE AND ANTI ANY ARRAY AS ASC ASOF ASYMMETRIC AT AUTHORIZATION BINARY BOTH BY CASE "
					+ "CAST CHECK COLLATE COLLATION COLUMN CONCURRENTLY CONSTRAINT CREATE CROSS DEFAULT DEFERRABLE DESC "
					+ "DESCRIBE DISTINCT DO ELSE END EXCEPT FALSE FETCH FOR FOREIGN FREEZE FROM FULL GLOB GRANT GROUP "
					+ "HAVING ILIKE IN INITIALLY INNER INTERSECT INTO IS ISNULL JOIN LAMBDA LATERAL LEADING LEFT LIKE LIMIT "
					+ "NATURAL NOT NOTNULL NULL OFFSET ON ONLY OR ORDER OUTER OVERLAPS PIVOT PIVOT_LONGER PIVOT_WIDER "
					+ "PLACING POSITIONAL PRIMARY QUALIFY REFERENCES RETURNING RIGHT SELECT SEMI SHOW SIMILAR SOME "
					+ "SUMMARIZE SYMMETRIC TABLE TABLESAMPLE THEN TO TRAILING TRUE UNION UNIQUE UNPACK UNPIVOT USING "
					+ "VARIADIC VERBOSE WHEN WHERE WINDOW WITH").split(" "));

	/** Schemas that DuckDB creates for catalog compatibility. */
	private static final String[] SYSTEM_SCHEMA = new String[] { "information_schema", "pg_catalog" };

	protected DuckDb(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		// VARCHAR (DuckDB ignores declared lengths)
		getDbDataTypes().addVarchar("VARCHAR", LEN_2GB, type -> {
			type.setCreateFormat("VARCHAR");
			type.addColumnTypeMatcher("TEXT").addColumnTypeMatcher("STRING").addColumnTypeMatcher("BPCHAR");
		});
		// CHAR is an alias of VARCHAR in DuckDB
		getDbDataTypes().addChar("CHAR", LEN_2GB, type -> {
			type.setCreateFormat("VARCHAR");
		});
		// BLOB
		getDbDataTypes().addBlob("BLOB", LEN_2GB, type -> {
			type.setCreateFormat("BLOB");
			type.addColumnTypeMatcher("BYTEA").addColumnTypeMatcher("BINARY").addColumnTypeMatcher("VARBINARY");
			type.setLiteral("from_hex('", "')");
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.BlobHandler());
		});
		// BOOLEAN
		getDbDataTypes().addBoolean("BOOLEAN", type -> {
			type.addColumnTypeMatcher("BOOL", "LOGICAL");
		});
		// signed integers
		getDbDataTypes().addTinyInt("TINYINT", type -> {
			type.addColumnTypeMatcher("INT1");
		});
		getDbDataTypes().addSmallInt("SMALLINT", type -> {
			type.addColumnTypeMatcher("INT2", "SHORT");
		});
		getDbDataTypes().addInt("INTEGER", type -> {
			type.addColumnTypeMatcher("INT", "INT4", "SIGNED");
		});
		getDbDataTypes().addBigInt("BIGINT", type -> {
			type.addColumnTypeMatcher("INT8", "LONG");
		});
		getDbDataTypes().addHugeIntType("HUGEINT", type -> {
			type.addColumnTypeMatcher("INT128");
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.BigIntegerHandler());
		});
		// unsigned integers
		getDbDataTypes().addUTinyInt("UTINYINT", type -> {
		});
		getDbDataTypes().addUSmallInt("USMALLINT", type -> {
		});
		getDbDataTypes().addUInt("UINTEGER", type -> {
		});
		getDbDataTypes().addUBigInt("UBIGINT", type -> {
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.BigIntegerHandler());
		});
		// floating point
		getDbDataTypes().addReal("FLOAT", type -> {
			type.setColumnTypeMatcher("FLOAT", "FLOAT4", "REAL");
		});
		getDbDataTypes().addDouble("DOUBLE", type -> {
			type.setColumnTypeMatcher("DOUBLE", "DOUBLE PRECISION", "FLOAT8");
		});
		// fixed point (width 1 to 38)
		getDbDataTypes().addDecimal(type -> {
			type.setMaxPrecision(38);
		});
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(38);
		});
		// date and time (DuckDB rejects precision modifiers on these types)
		getDbDataTypes().addDate(type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction());
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.DateHandler());
		});
		getDbDataTypes().addTime(type -> {
			type.setCreateFormat("TIME");
			type.setDefaultValueLiteral(getCurrentTimeFunction());
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.TimeHandler());
		});
		getDbDataTypes().addTimeWithTimeZone("TIMETZ", type -> {
			type.setCreateFormat("TIMETZ");
			type.addColumnTypeMatcher("TIME WITH TIME ZONE");
			type.setDefaultValueLiteral(getCurrentTimeWithTimeZoneFunction());
		});
		getDbDataTypes().addTimestamp(type -> {
			type.setCreateFormat("TIMESTAMP");
			type.addColumnTypeMatcher("DATETIME", "TIMESTAMP WITHOUT TIME ZONE");
			type.setDefaultValueLiteral(getCurrentTimestampFunction());
		});
		getDbDataTypes().addTimestampWithTimeZone("TIMESTAMPTZ", type -> {
			type.setCreateFormat("TIMESTAMPTZ");
			type.addColumnTypeMatcher("TIMESTAMP WITH TIME ZONE");
			type.setDefaultValueLiteral(getCurrentTimestampWithTimeZoneFunction());
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.TimestampWithTimeZoneHandler());
		});
		// INTERVAL
		getDbDataTypes().addInterval(type -> {
			type.setCreateFormat("INTERVAL");
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.IntervalHandler());
		});
		// UUID
		getDbDataTypes().addUUID("UUID", type -> {
			type.setLiteral("'", "'::UUID");
			type.setJdbcTypeHandler(new DuckDbJdbcTypeHandlers.UuidHandler());
		});
		// JSON (json extension type; auto-loaded by DuckDB)
		getDbDataTypes().addJsonType(type -> {
		});
		// ENUM
		getDbDataTypes().addEnum();
	}

	@Override
	public String getProductName() {
		return "DuckDB";
	}

	@Override
	public String getSimpleName() {
		return "duckdb";
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new DuckDbCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new DuckDbSqlFactoryRegistry(this);
	}

	@Override
	public DuckDbSqlSplitter createSqlSplitter() {
		return new DuckDbSqlSplitter(this);
	}

	@Override
	public DuckDbSqlBuilder createSqlBuilder() {
		return new DuckDbSqlBuilder(this);
	}

	/**
	 * Whether {@code duckdb_constraints()} exposes constraint names and referenced
	 * tables (DuckDB 1.1+). Constraint metadata is read only when this holds.
	 *
	 * @return whether catalog constraint names are available
	 */
	public boolean supportsConstraintMetadata() {
		return false;
	}

	/**
	 * Whether {@code ALTER TABLE ... ADD PRIMARY KEY} is available (DuckDB 1.2+).
	 * DuckDB cannot add UNIQUE, CHECK or FOREIGN KEY constraints to an existing
	 * table in any version.
	 *
	 * @return whether a primary key can be added to an existing table
	 */
	public boolean supportsAlterTableAddPrimaryKey() {
		return false;
	}

	@Override
	public boolean supportsMerge() {
		return false;
	}

	@Override
	public boolean supportsLimit() {
		return true;
	}

	@Override
	public boolean supportsLimitOffset() {
		return true;
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public boolean supportsWithRecursive() {
		return true;
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		return "SELECT nextval('" + sequenceName.replace("'", "''") + "')";
	}

	/**
	 * DuckDB has no identity columns; use a sequence default
	 * ({@code DEFAULT nextval('seq')}) instead.
	 */
	@Override
	public boolean supportsIdentity() {
		return false;
	}

	@Override
	public boolean supportsCatalog() {
		return true;
	}

	@Override
	public boolean supportsSchema() {
		return true;
	}

	@Override
	public String defaultSchema() {
		return "main";
	}

	@Override
	public String[] getSystemSchema() {
		return SYSTEM_SCHEMA.clone();
	}

	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.NonConvert;
	}

	@Override
	public boolean storesMixedCaseIdentifiers() {
		return true;
	}

	@Override
	public char getOpenQuote() {
		return '"';
	}

	@Override
	public char getCloseQuote() {
		return '"';
	}

	@Override
	public boolean needQuote(final String target) {
		if (target == null || target.isEmpty() || isQuoted(target)) {
			return false;
		}
		return !target.matches("[A-Za-z_][A-Za-z0-9_]*") || RESERVED_KEYWORDS.contains(target.toUpperCase(Locale.ROOT));
	}

	@Override
	protected String doQuote(final String target) {
		return "\"" + target.replace("\"", "\"\"") + "\"";
	}

	@Override
	public String unQuote(final String target) {
		return isQuoted(target) ? target.substring(1, target.length() - 1).replace("\"\"", "\"") : target;
	}

	@Override
	public String getCurrentDateFunction() {
		return "CURRENT_DATE";
	}

	@Override
	public String getCurrentDateTimeFunction() {
		return "LOCALTIMESTAMP";
	}

	@Override
	public String getCurrentTimeFunction() {
		return "LOCALTIME";
	}

	@Override
	public String getCurrentTimeWithTimeZoneFunction() {
		return "CURRENT_TIME";
	}

	@Override
	public String getCurrentTimestampFunction() {
		return "LOCALTIMESTAMP";
	}

	@Override
	public String getCurrentTimestampWithTimeZoneFunction() {
		return "CURRENT_TIMESTAMP";
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return true;
	}

	@Override
	public boolean supportsColumnFormula() {
		return true;
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparison() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparisonIn() {
		return true;
	}

	@Override
	public boolean isDdlRollbackable() {
		return true;
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		return super.equals(obj);
	}
}
