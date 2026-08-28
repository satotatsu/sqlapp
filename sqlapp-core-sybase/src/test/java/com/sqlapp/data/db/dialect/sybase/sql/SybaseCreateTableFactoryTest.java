/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SybaseCreateTableFactoryTest extends AbstractSybaseSqlFactoryRegistryTest {
	@Test
	void explicitlyMarksNullableColumns() {
		final Table table = new Table("CHECKPOINT");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("VALUE").setDataType(DataType.VARCHAR)
				.setLength(100).setNotNull(false));

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ").toUpperCase();

		assertTrue(sql.contains("VALUE VARCHAR(100) NULL"), sql);
	}
}
