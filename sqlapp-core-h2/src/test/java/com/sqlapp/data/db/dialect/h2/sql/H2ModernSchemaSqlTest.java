/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class H2ModernSchemaSqlTest extends AbstractH2SqlFactoryTest {

	@Test
	void testCreateTableIfNotExists() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		table.getColumns().add(
				new Column("PAYLOAD").setDataType(DataType.JSON));
		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS EVENTS"), sql);
		assertTrue(sql.contains("PAYLOAD JSON"), sql);
	}
}
