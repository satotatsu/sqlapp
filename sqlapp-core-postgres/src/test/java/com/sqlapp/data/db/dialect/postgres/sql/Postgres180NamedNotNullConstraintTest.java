/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;

class Postgres180NamedNotNullConstraintTest {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void testCreateNamedNotNullConstraintOnPostgres18() {
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		table.getColumns().add(column);
		table.getConstraints().add(new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID", column));

		SqlFactory factory = DialectHolder.postgreSQL180
				.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		String sql = ((SqlOperation) factory.createSql(table).get(0))
				.getSqlText();
		assertTrue(sql.contains(
				"CONSTRAINT \"NN_CUSTOMERS_CUSTOMER_ID\" NOT NULL \"CUSTOMER_ID\""),
				sql);
		assertFalse(sql.contains("\"CUSTOMER_ID\" BIGINT NOT NULL"), sql);
		assertTrue(column.isNotNull());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void testEarlierVersionKeepsUnnamedNotNullBehavior() {
		Table table = new Table("CUSTOMERS");
		table.getColumns().add(new Column("CUSTOMER_ID")
				.setDataType(DataType.BIGINT).setNotNull(true));
		table.getConstraints().add(new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID",
				table.getColumns().get("CUSTOMER_ID")));

		SqlFactory factory = DialectHolder.postgreSQL170
				.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		String sql = ((SqlOperation) factory.createSql(table).get(0))
				.getSqlText();
		assertTrue(sql.contains("\"CUSTOMER_ID\" BIGINT NOT NULL"), sql);
		assertFalse(sql.contains("NN_CUSTOMERS_CUSTOMER_ID"), sql);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	void testNamedNotNullNoInherit() {
		Table table = new Table("CUSTOMERS");
		Column column = new Column("CUSTOMER_ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		table.getColumns().add(column);
		table.getConstraints().add(new NotNullConstraint(
				"NN_CUSTOMERS_CUSTOMER_ID", column).setNoInherit(true));

		SqlFactory factory = DialectHolder.postgreSQL180
				.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		String sql = ((SqlOperation) factory.createSql(table).get(0))
				.getSqlText();
		assertTrue(sql.contains(
				"CONSTRAINT \"NN_CUSTOMERS_CUSTOMER_ID\" NOT NULL \"CUSTOMER_ID\" NO INHERIT"),
				sql);
	}
}
