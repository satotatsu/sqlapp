/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class InformixCreateTableFactoryTest {
	@Test
	void placesConstraintNameAfterPrimaryKeyDefinition() {
		final Table table = new Table("CHECKPOINT");
		final Column id = new Column("ID").setDataType(DataType.VARCHAR)
				.setLength(255).setNotNull(true);
		table.getColumns().add(id);
		table.setPrimaryKey("PK_CHECKPOINT", id);

		final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ").toUpperCase();

		assertTrue(sql.contains("PRIMARY KEY ( ID ) CONSTRAINT PK_CHECKPOINT"), sql);
		assertFalse(sql.contains("CONSTRAINT PK_CHECKPOINT PRIMARY KEY"), sql);
	}
}
