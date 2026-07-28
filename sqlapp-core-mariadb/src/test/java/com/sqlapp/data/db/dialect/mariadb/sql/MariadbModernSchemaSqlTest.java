/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

class MariadbModernSchemaSqlTest extends AbstractMariadbSqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 11;
	}

	@Override
	protected int getMinorVersion() {
		return 8;
	}

	@Test
	void testInvisibleColumnAndIgnoredIndex() {
		Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT));
		table.getColumns().add("LEGACY_CODE",
				column -> column.setDataType(DataType.VARCHAR).setLength(20).setHidden(true));
		Index index = new Index("IDX_ORDERS_CODE");
		index.getColumns().add(table.getColumns().get("LEGACY_CODE"));
		index.setEnable(false);
		table.getIndexes().add(index);

		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("LEGACY_CODE` VARCHAR(20) INVISIBLE"), sql);
		assertTrue(sql.contains("INDEX `IDX_ORDERS_CODE`"), sql);
		assertTrue(sql.contains("`LEGACY_CODE` ) IGNORED"), sql);
	}
}
