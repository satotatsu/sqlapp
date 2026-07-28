/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

class MySqlModernSchemaSqlTest extends AbstractMySqlSqlFactoryTest {

	@Override
	protected int getMajorVersion() {
		return 8;
	}

	@Override
	protected int getMinorVersion() {
		return 4;
	}

	@Test
	void testInvisibleColumnExpressionIndexAndCheckConstraint() {
		Table table = new Table("ORDERS");
		table.setDialect(dialect);
		table.getColumns().add("ID", column -> column.setDataType(DataType.INT));
		table.getColumns().add("LEGACY_CODE",
				column -> column.setDataType(DataType.VARCHAR).setLength(20).setHidden(true));
		Index index = new Index("IDX_ORDERS_CODE");
		index.getColumns().add("lower(`LEGACY_CODE`)");
		index.setEnable(false);
		table.getIndexes().add(index);
		table.getConstraints().add(new CheckConstraint("CK_ORDERS_ID", "`ID` > 0").setEnable(false));

		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("LEGACY_CODE` VARCHAR(20) INVISIBLE"), sql);
		assertTrue(sql.contains("((lower(`LEGACY_CODE`))"), sql);
		assertTrue(sql.contains("INVISIBLE"), sql);
		assertTrue(sql.contains("NOT ENFORCED"), sql);
	}
}
