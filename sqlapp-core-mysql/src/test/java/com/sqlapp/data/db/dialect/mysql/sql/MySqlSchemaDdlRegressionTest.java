/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

class MySqlSchemaDdlRegressionTest {

	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void uniqueIndexesKeepUniquenessPrefixAndLocalName(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table table = table(dialect);
		Index index = new Index("uq_code");
		index.setUnique(true);
		index.getColumns().add(table.getColumns().get("code"), Order.Desc).setLength(8L);
		table.getIndexes().add(index);
		SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		factory.getOptions().setDecorateSchemaName(true);
		String sql = factory.createSql(index).get(0).getSqlText();
		assertTrue(sql.contains("CREATE UNIQUE INDEX uq_code ON app.orders"), sql);
		assertTrue(sql.contains("code(8) DESC"), sql);
		assertFalse(sql.contains("INDEX app.uq_code"), sql);
		String tableSql = sql(dialect, table, SqlType.CREATE);
		assertTrue(tableSql.contains("UNIQUE INDEX uq_code"), tableSql);
		assertTrue(tableSql.contains("code(8) DESC"), tableSql);
		SqlFactory<Index> drop = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.DROP);
		assertEquals("DROP INDEX uq_code ON app.orders", drop.createSql(index).get(0).getSqlText());
	}

	@Test
	void functionalUniqueIndexKeepsEveryKeyPart() {
		Dialect dialect = dialect(8, 4);
		Table table = table(dialect);
		Index index = new Index("uq_expression");
		index.setUnique(true);
		index.getColumns().add(table.getColumns().get("id"), Order.Asc);
		index.getColumns().add("lower(code)").setOrder(Order.Desc);
		table.getIndexes().add(index);
		String sql = sql(dialect, table, SqlType.CREATE);
		assertTrue(sql.contains("UNIQUE INDEX uq_expression"), sql);
		assertTrue(sql.contains("id, (lower(code)) DESC"), sql);
	}

	@ParameterizedTest
	@CsvSource({ "5,7", "8,0", "8,4", "9,0" })
	void generatedColumnsAreCreatedAlteredAndExcludedFromDml(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		assertTrue(dialect.supportsColumnFormula());
		Table original = table(dialect);
		Table target = original.clone();
		target.getColumns().add("calculated",
				c -> c.setDataType(DataType.INT).setFormula("id + 1").setFormulaPersisted(true));
		Column generated = target.getColumns().get("calculated");
		String sql = sql(dialect, target, SqlType.CREATE);
		assertTrue(sql.contains("INT GENERATED ALWAYS AS (id + 1) STORED"), sql);
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		String alteration = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(alteration.contains("GENERATED ALWAYS AS (id + 1) STORED"), alteration);
		for (SqlType type : new SqlType[] { SqlType.INSERT, SqlType.UPDATE, SqlType.MERGE }) {
			String dml = sql(dialect, target, type);
			assertFalse(dml.contains("calculated"), dml);
			assertFalse(dml.contains("id + 1"), dml);
		}
		generated.setDefaultValue("1");
		assertThrows(IllegalArgumentException.class, () -> sql(dialect, target, SqlType.CREATE));
	}

	@Test
	void oldDialectsRejectGeneratedColumnsAndOptimizeUsesOptimize() {
		Dialect old = dialect(5, 5);
		assertFalse(old.supportsColumnFormula());
		Table table = table(old);
		table.getColumns().get("code").setFormula("id + 1");
		assertThrows(IllegalArgumentException.class, () -> sql(old, table, SqlType.CREATE));
		Dialect modern = dialect(8, 4);
		String sql = sql(modern, table(modern), SqlType.OPTIMIZE);
		assertTrue(sql.startsWith("OPTIMIZE TABLE"), sql);
		assertFalse(sql.contains("ANALYZE"), sql);
	}

	@Test
	void checkChangesProduceDropAndAddClauses() {
		Dialect dialect = dialect(8, 4);
		Table original = table(dialect);
		original.getConstraints().add(new CheckConstraint("ck_id", "id > 0"));
		Table target = original.clone();
		target.getConstraints().remove("ck_id");
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		String deleted = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(deleted.contains("DROP CHECK ck_id"), deleted);
		target.getConstraints().add(new CheckConstraint("ck_id", "id >= 0"));
		String modified = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(modified.contains("DROP CHECK ck_id"), modified);
		assertTrue(modified.replaceAll("\\s+", "").contains("CHECK(id>=0)"), modified);
	}

	@Test
	void droppingConstraintBackedIndexDoesNotSkipUnrelatedIndex() {
		Dialect dialect = dialect(8, 4);
		Table original = table(dialect);
		original.getConstraints().addUniqueConstraint("a_unique", original.getColumns().get("code"));
		Index independent = new Index("z_independent");
		independent.getColumns().add(original.getColumns().get("id"), Order.Asc);
		original.getIndexes().add(independent);
		Table target = original.clone();
		target.getConstraints().remove("a_unique");
		target.getIndexes().remove("z_independent");
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		String sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(sql.contains("DROP INDEX a_unique"), sql);
		assertTrue(sql.contains("DROP INDEX z_independent"), sql);
	}

	private static Table table(Dialect dialect) {
		Table table = new Table("orders");
		table.setDialect(dialect);
		table.setSchemaName("app");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		table.getConstraints().addPrimaryKeyConstraint("PRIMARY", "id");
		return table;
	}

	private static String sql(Dialect dialect, Table table, SqlType type) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, type);
		return factory.createSql(table).get(0).getSqlText();
	}

	private static Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 9);
	}
}
