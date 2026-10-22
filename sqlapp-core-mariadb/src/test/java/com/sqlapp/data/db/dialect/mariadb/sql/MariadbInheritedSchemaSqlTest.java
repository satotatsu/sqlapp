/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

class MariadbInheritedSchemaSqlTest {

	private final Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 11, 0);

	@Test
	void inheritedIndexesKeepPrefixAndMariaDbIgnoredSyntax() {
		Table table = table();
		Index index = new Index("uq_code");
		index.setUnique(true);
		index.setEnable(false);
		index.getColumns().add(table.getColumns().get("code"), Order.Desc).setLength(8L);
		table.getIndexes().add(index);
		String sql = sql(table, SqlType.CREATE);
		assertTrue(sql.contains("UNIQUE INDEX uq_code"), sql);
		assertTrue(sql.contains("code(8) DESC"), sql);
		assertTrue(sql.contains("IGNORED"), sql);
		assertFalse(sql.contains("INVISIBLE"), sql);
		SqlFactory<Index> drop = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.DROP);
		assertEquals("DROP INDEX uq_code ON app.orders", drop.createSql(index).get(0).getSqlText());
	}

	@Test
	void inheritedViewsAndGeneratedColumnsUseNativeSyntax() {
		View view = new View("v");
		view.setDialect(dialect);
		view.setStatement("SELECT code FROM orders");
		view.getSpecifics().put("security_type", "INVOKER");
		view.getSpecifics().put("check_option", "LOCAL");
		String viewSql = sql(view, SqlType.CREATE);
		assertTrue(viewSql.contains("SQL SECURITY INVOKER VIEW"), viewSql);
		assertTrue(viewSql.contains("WITH LOCAL CHECK OPTION"), viewSql);
		Table table = table();
		table.getColumns().add("calculated", c -> c.setDataType(DataType.INT).setFormula("id + 1"));
		String sql = sql(table, SqlType.CREATE);
		assertTrue(sql.contains("GENERATED ALWAYS AS (id + 1) VIRTUAL"), sql);
	}

	@Test
	void checkRemovalUsesMariaDbConstraintKeyword() {
		Table original = table();
		original.getConstraints().add(new CheckConstraint("ck_id", "id > 0"));
		Table target = original.clone();
		target.getConstraints().remove("ck_id");
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		String sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(sql.contains("DROP CONSTRAINT ck_id"), sql);
		assertFalse(sql.contains("DROP CHECK"), sql);
	}

	private Table table() {
		Table table = new Table("orders");
		table.setSchemaName("app");
		table.setDialect(dialect);
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		return table;
	}

	private String sql(Table table, SqlType type) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, type);
		return factory.createSql(table).get(0).getSqlText();
	}
}
