/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

class OracleIdentityInsertTableFactoryTest extends AbstractOracleSqlFactoryTest {
	private SqlFactory<Table> sqlFactory;

	@Override
	protected int getMajorVersion() {
		return 23;
	}

	@BeforeEach
	void before() {
		sqlFactory = sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.INSERT);
	}

	@Test
	void testByDefaultIdentityUsesDefaultWhenValueIsMissing() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		addRow(table).put("txt", "generated");

		String sql = CommonUtils.first(sqlFactory.createSql(table)).getSqlText();

		assertTrue(sql.contains("default"));
	}

	@Test
	void testByDefaultIdentityUsesExplicitValue() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		Row row = addRow(table);
		row.put("id", 100L);
		row.put("txt", "explicit");

		String sql = CommonUtils.first(sqlFactory.createSql(table)).getSqlText();

		assertTrue(sql.contains("/*id*/0"));
	}

	@Test
	void testByDefaultIdentityRejectsMixedValues() {
		Table table = createIdentityTable(IdentityGenerationType.ByDefault);
		addRow(table).put("txt", "generated");
		Row explicit = addRow(table);
		explicit.put("id", 100L);
		explicit.put("txt", "explicit");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> sqlFactory.createSql(table));

		assertTrue(exception.getMessage().contains("cannot mix"));
	}

	@Test
	void testAlwaysIdentityRejectsExplicitValue() {
		Table table = createIdentityTable(IdentityGenerationType.Always);
		Row row = addRow(table);
		row.put("id", 100L);
		row.put("txt", "explicit");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> sqlFactory.createSql(table));

		assertTrue(exception.getMessage().contains("GENERATED ALWAYS"));
	}

	private Table createIdentityTable(final IdentityGenerationType generationType) {
		Table table = new Table("identity_table");
		table.setDialect(dialect);
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
				.setIdentity(true).setIdentityGenerationType(generationType));
		table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(50));
		table.setPrimaryKey(table.getColumns().get("id"));
		return table;
	}

	private Row addRow(final Table table) {
		Row row = table.newRow();
		table.getRows().add(row);
		return row;
	}
}
