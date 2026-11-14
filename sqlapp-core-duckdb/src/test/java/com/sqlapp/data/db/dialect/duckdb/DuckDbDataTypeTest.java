/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.schemas.Column;

/**
 * Type names reported by duckdb_columns().data_type are read and written back
 * unchanged.
 */
class DuckDbDataTypeTest {

	private final Dialect dialect = DialectHolder.defaultDialect;

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "BOOLEAN|BOOLEAN|BOOLEAN", "TINYINT|TINYINT|TINYINT",
			"SMALLINT|SMALLINT|SMALLINT", "INTEGER|INT|INTEGER", "BIGINT|BIGINT|BIGINT", "HUGEINT|HUGEINT|HUGEINT",
			"UTINYINT|UTINYINT|UTINYINT", "USMALLINT|USMALLINT|USMALLINT", "UINTEGER|UINT|UINTEGER",
			"UBIGINT|UBIGINT|UBIGINT", "FLOAT|REAL|FLOAT", "DOUBLE|DOUBLE|DOUBLE", "VARCHAR|VARCHAR|VARCHAR",
			"BLOB|BLOB|BLOB", "DATE|DATE|DATE", "TIME|TIME|TIME", "TIME WITH TIME ZONE|TIME_WITH_TIMEZONE|TIMETZ",
			"TIMESTAMP|TIMESTAMP|TIMESTAMP", "TIMESTAMP WITH TIME ZONE|TIMESTAMP_WITH_TIMEZONE|TIMESTAMPTZ",
			"INTERVAL|INTERVAL|INTERVAL", "UUID|UUID|UUID", "JSON|JSON|JSON", "UHUGEINT|OTHER|UHUGEINT",
			"TIMESTAMP_NS|OTHER|TIMESTAMP_NS", "INTEGER[]|OTHER|INTEGER[]",
			"STRUCT(x INTEGER, y VARCHAR)|OTHER|STRUCT(x INTEGER, y VARCHAR)",
			"MAP(VARCHAR, INTEGER)|OTHER|MAP(VARCHAR, INTEGER)" })
	void roundTripsTypeNames(final String duckDbType, final DataType dataType, final String definition) {
		final Column column = new Column("c");
		column.setDialect(dialect);
		dialect.setDbType(duckDbType, null, null, column);
		assertEquals(dataType, column.getDataType());
		assertEquals(definition, new DuckDbSqlBuilder(dialect).addTypeDefinition(column).toString());
	}

	@Test
	void roundTripsDecimalAndEnum() {
		final Column decimal = new Column("d");
		decimal.setDialect(dialect);
		dialect.setDbType("DECIMAL(10,2)", 10L, 2, decimal);
		assertEquals(DataType.DECIMAL, decimal.getDataType());
		assertEquals("DECIMAL(10,2)", new DuckDbSqlBuilder(dialect).addTypeDefinition(decimal).toString());

		final Column enumColumn = new Column("e").setDataType(DataType.ENUM);
		enumColumn.getValues().addAll(List.of("a", "b,c", "d'e"));
		assertEquals("ENUM('a', 'b,c', 'd''e')",
				new DuckDbSqlBuilder(dialect).addTypeDefinition(enumColumn).toString());
	}

	@Test
	void quotesOnlyWhenRequired() {
		assertEquals("Name", dialect.quote("Name"));
		assertEquals("\"select\"", dialect.quote("select"));
		assertEquals("\"at\"", dialect.quote("at"));
		assertEquals("\"Join\"", dialect.quote("Join"));
		assertEquals("\"a b\"", dialect.quote("a b"));
		assertEquals("\"x\"\"y\"", dialect.quote("x\"y"));
		assertEquals("x\"y", dialect.unQuote("\"x\"\"y\""));
	}
}
