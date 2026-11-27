/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.util.CommonUtils;

/**
 * Copies rows of every supported DuckDB type from one in-memory database to
 * another through the sqlapp streaming read path
 * ({@link JdbcDynamicRowIteratorHandler}) and write path
 * ({@link BulkInsertResolver}), then compares the values with DuckDB's own text
 * rendering.
 */
class DuckDbDataTransferTest {

	private static final String DDL = """
			CREATE TABLE src (
			  id INTEGER PRIMARY KEY
			, c_bool BOOLEAN
			, c_tinyint TINYINT
			, c_smallint SMALLINT
			, c_int INTEGER
			, c_bigint BIGINT
			, c_hugeint HUGEINT
			, c_utinyint UTINYINT
			, c_usmallint USMALLINT
			, c_uint UINTEGER
			, c_ubigint UBIGINT
			, c_float FLOAT
			, c_double DOUBLE
			, c_decimal DECIMAL(18,3)
			, c_decimal38 DECIMAL(38,10)
			, c_varchar VARCHAR
			, c_blob BLOB
			, c_date DATE
			, c_time TIME
			, c_timestamp TIMESTAMP
			, c_timestamptz TIMESTAMPTZ
			, c_interval INTERVAL
			, c_uuid UUID
			, c_json JSON
			, c_enum ENUM('a', 'b,c', 'd''e')
			)""";

	private static final String[] ROWS = {
			"""
					INSERT INTO src VALUES (1, true, -128, -32768, -2147483648, -9223372036854775808,
					  -170141183460469231731687303715884105727, 0, 0, 0, 0, -1.5, -2.25, -123456789012345.678,
					  '1234567890123456789012345678.0123456789', 'ascii', '\\x00\\xFF'::BLOB, DATE '0001-01-01', TIME '00:00:00',
					  TIMESTAMP '1970-01-01 00:00:00', TIMESTAMPTZ '2000-01-01 00:00:00+00', INTERVAL 1 DAY,
					  '00000000-0000-0000-0000-000000000000', '{"a":1}', 'a')""",
			"""
					INSERT INTO src VALUES (2, false, 127, 32767, 2147483647, 9223372036854775807,
					  170141183460469231731687303715884105727, 255, 65535, 4294967295, 18446744073709551615, 3.25, 1.0E300,
					  999999999999999.999, '-0.0000000001', '日本語 ''quote''', ''::BLOB, DATE '9999-12-31', TIME '23:59:59.999999',
					  TIMESTAMP '2026-11-14 12:34:56.123456', TIMESTAMPTZ '2026-11-14 12:34:56.123456+09',
					  INTERVAL '1 year 2 months 3 days 04:05:06.789', 'ffffffff-ffff-ffff-ffff-ffffffffffff', '[1,2,3]', 'd''e')""",
			"INSERT INTO src (id) VALUES (3)" };

	@Test
	void copiesEveryTypeThroughRowIteratorAndBulkInsert() throws Exception {
		try (Connection source = DriverManager.getConnection("jdbc:duckdb:");
				Connection target = DriverManager.getConnection("jdbc:duckdb:");
				Statement sourceStatement = source.createStatement();
				Statement targetStatement = target.createStatement()) {
			// Compare instants in a fixed zone in both databases.
			sourceStatement.execute("SET TimeZone = 'UTC'");
			targetStatement.execute("SET TimeZone = 'UTC'");
			sourceStatement.execute(DDL);
			for (final String row : ROWS) {
				sourceStatement.execute(row);
			}
			final Dialect dialect = DialectResolver.getInstance().getDialect(source);
			assumeTrue(((DuckDb) dialect).supportsConstraintMetadata(), "schema reading requires DuckDB 1.1 or later");
			final var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("main");
			final Schema schema = CommonUtils.first(reader.getAllFull(source));
			final Table table = schema.getTables().get("src");
			for (final var operation : dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE)) {
				targetStatement.execute(operation.getSqlText());
			}
			// Same streaming read path as ExportDataCommand and bulk migration jobs.
			table.setRowIteratorHandler(new JdbcDynamicRowIteratorHandler(source));
			assertEquals(ROWS.length, BulkInsertResolver.execute(target, table, BulkOption.defaults()));
			assertEquals(asText(sourceStatement), asText(targetStatement));
		}
	}

	private static List<String> asText(final Statement statement) throws Exception {
		final List<String> result = new ArrayList<>();
		try (ResultSet rows = statement.executeQuery("SELECT * FROM src ORDER BY id")) {
			final int count = rows.getMetaData().getColumnCount();
			while (rows.next()) {
				for (int i = 1; i <= count; i++) {
					result.add(rows.getMetaData().getColumnName(i) + "=" + rows.getString(i));
				}
			}
		}
		return result;
	}
}
