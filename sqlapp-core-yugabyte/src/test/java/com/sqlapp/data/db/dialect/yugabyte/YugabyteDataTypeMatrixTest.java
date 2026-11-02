/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class YugabyteDataTypeMatrixTest {
	private java.util.List<Dialect> dialects() {
		var result = new java.util.ArrayList<Dialect>();
		for (int major : new int[] { 11, 15 })
			result.add(DialectResolver.getInstance().getDialect("YugabyteDB", major, 0, null));
		return result;
	}

	@ParameterizedTest
	@CsvSource({ "real, REAL, FLOAT4", "float4, REAL, FLOAT4",
			"float8, DOUBLE, DOUBLE PRECISION", "double precision, DOUBLE, DOUBLE PRECISION",
			"float, DOUBLE, DOUBLE PRECISION", "float(1), REAL, FLOAT4", "float(24), REAL, FLOAT4",
			"float(25), DOUBLE, DOUBLE PRECISION", "float(53), DOUBLE, DOUBLE PRECISION",
			"'numeric(12,3)', NUMERIC, 'NUMERIC(12,3)'", "character varying(12), VARCHAR, VARCHAR(12)",
			"time(3) without time zone, TIME, TIME(3)",
			"timestamp(3) with time zone, TIMESTAMP_WITH_TIMEZONE, TIMESTAMP(3) WITH TIME ZONE",
			"uuid, UUID, UUID", "bytea, BLOB, BYTEA", "jsonb, JSONB, JSONB" })
	void inheritedTypesResolveAndGenerateValidNames(String input, DataType type, String expected) {
		for (var dialect : dialects()) {
			assertEquals("yugabyte", dialect.getSimpleName());
			for (String suffix : new String[] { "", "[]" }) {
				String context = dialect.getClass().getSimpleName() + ": " + input + suffix;
				var column = new Column("C");
				assertTrue(dialect.setDbType(input + suffix, null, null, column), context);
				assertEquals(type, column.getDataType(), context);
				assertEquals(suffix.isEmpty() ? 0 : 1, column.getArrayDimension(), context);
				assertEquals(expected + suffix, dialect.createSqlBuilder().addTypeDefinition(column).toString(), context);
				var table = new Table("TYPE_MATRIX");
				table.getColumns().add(column);
				assertTrue(dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst()
						.getSqlText().contains(expected + suffix), context);
			}
		}
	}

	@Test
	void portableDoubleKeepsCorrectSqlWithRetainedSourceNames() {
		for (var dialect : dialects())
			for (String source : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
				var column = new Column("C").setDataType(DataType.DOUBLE).setDataTypeName(source);
				assertEquals("DOUBLE PRECISION", dialect.createSqlBuilder().addTypeDefinition(column).toString());
				assertEquals(source, column.getDataTypeName());
			}
	}
}
