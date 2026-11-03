/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.db.dialect.vertica.Vertica;
import com.sqlapp.data.schemas.Column;

class VerticaModernDataTypeTest {
	@Test
	void temporalDdlUsesSixDigitDefaultAndPreservesExplicitPrecision() {
		var dialect = DialectHolder.defaultDialect11_1_1;
		for (String type : new String[] { "TIME", "TIMETZ", "TIMESTAMP", "TIMESTAMPTZ" }) {
			for (Integer precision : new Integer[] { null, 0, 4, 6 }) {
				var table = new com.sqlapp.data.schemas.Table("temporal");
				Column column = new Column("value");
				dialect.setDbType(type, precision == null ? null : precision.longValue(), null, column);
				table.getColumns().add(column);
				String sql = dialect.createSqlFactoryRegistry().createSql(table, com.sqlapp.data.db.sql.SqlType.CREATE)
						.get(0).getSqlText();
				assertTrue(sql.contains("(" + (precision == null ? 6 : precision) + ")"), sql);
			}
		}
	}

	@Test
	void nativeNamesAndTemporalPrecisionRemainRecognizedAlongsideAliases() throws Exception {
		for (int major : new int[] { 7, 8, 9, 11, 12, 25 }) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("Vertica", major, 0, 0);
			String[] names = { "BIGINT", "INTEGER", "INT8", "DOUBLE", "DOUBLE PRECISION", "FLOAT", "REAL",
					"TIMESTAMP(6)", "TIMESTAMP WITHOUT TIME ZONE", "DATETIME", "SMALLDATETIME" };
			for (int i = 0; i < names.length; i++) {
				Column column = new Column("value");
				dialect.setDbType(names[i], null, null, column);
				assertEquals(i < 3 ? DataType.BIGINT : i < 7 ? DataType.DOUBLE : DataType.TIMESTAMP,
						column.getDataType(), names[i]);
				Column restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
				assertEquals(column.getDataType(), restored.getDataType(), names[i]);
				if (names[i].equals("TIMESTAMP(6)")) {
					var table = new com.sqlapp.data.schemas.Table("typed");
					table.getColumns().add(restored);
					String sql = dialect.createSqlFactoryRegistry()
							.createSql(table, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
					assertTrue(sql.contains("TIMESTAMP(6)"), sql);
				}
			}
		}
	}

	@Test
	void longAndNumericTypesRetainDeclaredSizesThroughXmlAndDdl() throws Exception {
		for (int major : new int[] { 7, 8, 9, 11, 12, 25 }) {
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("Vertica", major, 0, 0);
			var table = new com.sqlapp.data.schemas.Table("types");
			for (String name : new String[] { "LONG VARCHAR", "LONG VARBINARY", "NUMERIC", "DECIMAL" }) {
				Column column = new Column(name);
				dialect.setDbType(name, name.startsWith("LONG") ? 32000000L : 1024L,
						name.startsWith("LONG") ? null : 500, column);
				table.getColumns().add(column);
			}
			assertEquals(DataType.LONGVARBINARY, table.getColumns().get("LONG VARBINARY").getDataType());
			com.sqlapp.data.schemas.Table restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(table.asXml()));
			String sql = dialect.createSqlFactoryRegistry().createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE)
					.get(0).getSqlText();
			assertTrue(sql.contains("LONG VARCHAR(32000000)"), sql);
			assertTrue(sql.contains("LONG VARBINARY(32000000)"), sql);
			assertTrue(sql.contains("NUMERIC(1024,500)"), sql);
			assertTrue(sql.contains("DECIMAL(1024,500)"), sql);
		}
	}

	@Test
	void testNativeUuidAndProductName() {
		final Vertica dialect = (Vertica) DialectHolder.defaultDialect90;
		final Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("UUID");
		assertEquals(DataType.UUID, column.getDataType());
		assertEquals("Vertica", dialect.getProductName());
	}

	@Test
	void testUuidIsNotNativeBeforeVersion9() {
		final Column column = new Column();
		column.setDialect(DialectHolder.defaultDialect80);
		column.setDataTypeName("UUID");
		assertEquals(DataType.OTHER, column.getDataType());
	}
}
