/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class MariadbSequenceTypeSqlTest {
	private String sql(int major, int minor, Sequence sequence) {
		return DialectResolver.getInstance().getDialect("MariaDB", major, minor, 0)
				.createSqlFactoryRegistry().createSql(sequence, SqlType.CREATE).get(0).getSqlText();
	}
	@Test
	void legacyBigintOmitsUnsupportedAsClause() {
		for (int[] version : new int[][] { {10, 3}, {10, 5}, {11, 4} }) {
			assertFalse(sql(version[0], version[1], new Sequence("seq").setDataType(DataType.BIGINT)).contains(" AS "));
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setDataType(DataType.INT)));
		}
	}
	@Test
	void modernIntegerTypesRetainSignednessWithoutCatalogPrecision() {
		for (int[] version : new int[][] { {11, 5}, {11, 8}, {12, 1} }) {
			String ddl = sql(version[0], version[1], new Sequence("seq").setDataType(DataType.UBIGINT).setPrecision(64));
			assertTrue(ddl.contains(" AS BIGINT UNSIGNED"), ddl);
			assertFalse(ddl.contains("BIGINT("), ddl);
			assertThrows(IllegalArgumentException.class,
					() -> sql(version[0], version[1], new Sequence("seq").setDataType(DataType.VARCHAR)));
		}
	}
}
