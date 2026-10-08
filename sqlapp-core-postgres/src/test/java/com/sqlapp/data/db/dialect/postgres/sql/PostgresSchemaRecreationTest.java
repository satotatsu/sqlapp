/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class PostgresSchemaRecreationTest extends AbstractPostgresSqlFactoryTest {
	@Test
	void retainsArrayDimensionsAndLeavesConstraintNamesUnqualified() {
		Table table = new Table("items").setSchemaName("schemaA");
		table.getColumns().add("tags", c -> c.setDataType(DataType.VARCHAR).setLength(20).setArrayDimension(1));
		table.getColumns().add("matrix", c -> c.setDataType(DataType.INT).setArrayDimension(2));
		table.getColumns().add("scalar", c -> c.setDataType(DataType.INT));
		table.getConstraints().add(new CheckConstraint("items_check", "scalar > 0"));
		sqlFactoryRegistry.getOptions().setDecorateSchemaName(true);
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("VARCHAR(20)[]"), sql);
		assertTrue(sql.contains("INT[][]"), sql);
		assertTrue(sql.contains("scalar INT"), sql);
		String check = sql.substring(sql.indexOf("CONSTRAINT"));
		assertFalse(check.contains("schemaA"), sql);
		assertTrue(check.contains("CHECK"), sql);
		assertEquals(1, table.getColumns().get("tags").getArrayDimension());
	}
	@Test
	void recreatesEnumAndDomainWithSchemaAndEscapedLabels() {
		var registry = sqlFactoryRegistry;
		registry.getOptions().setDecorateSchemaName(true);
		var domain = new com.sqlapp.data.schemas.Domain("positive").setSchemaName("tenant");
		domain.setDataType(DataType.INT).setNullable(false).setDefaultValue("1").setCheck("VALUE > 0");
		SqlFactory<com.sqlapp.data.schemas.Domain> factory = registry.getSqlFactory(domain, SqlType.CREATE);
		String sql = factory.createSql(domain).get(0).getSqlText();
		assertTrue(sql.contains("tenant.positive"), sql);
		assertTrue(sql.contains("CHECK (VALUE > 0)"), sql);
		assertTrue(sql.contains("DEFAULT 1"), sql);
		assertTrue(sql.contains("NOT NULL"), sql);
		var enumeration = new com.sqlapp.data.schemas.Domain("status").setSchemaName("tenant");
		enumeration.setDataType(DataType.ENUM);
		enumeration.getValues().add("it's new");
		String enumSql = factory.createSql(enumeration).get(0).getSqlText();
		assertTrue(enumSql.contains("CREATE TYPE tenant.status AS ENUM"), enumSql);
		assertTrue(enumSql.contains("'it''s new'"), enumSql);
	}
	@Test
	void keyOnlyUpsertDoesNothingOnConflict() {
		Table table = new Table("keys");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey("keys_pkey", table.getColumns().get("id"));
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.MERGE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("DO NOTHING"), sql);
		assertFalse(sql.contains("DO UPDATE"), sql);
		table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR).setLength(20));
		String update = factory.createSql(table).get(0).getSqlText();
		assertTrue(update.contains("DO UPDATE"), update);
		assertTrue(update.contains("SET label"), update);
	}
	@Test
	void coveringPartialIndexPlacesIncludesBeforeStorageAndPredicate() {
		for (int major : new int[] {11, 15}) {
			var registry = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect("postgres", major, 0, null).createSqlFactoryRegistry();
			Table table = new Table("items").setSchemaName("tenant");
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR));
			var index = new com.sqlapp.data.schemas.Index("active_items");
			table.getIndexes().add(index);
			index.getColumns().add("id", com.sqlapp.data.schemas.Order.Desc);
			index.getIncludes().add("label");
			index.getSpecifics().put("fillfactor", "80");
			index.setWhere("length(label) > 0");
			String sql = registry.createSql(index, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("DESC"), sql);
			assertTrue(sql.indexOf("INCLUDE") > 0, sql);
			assertTrue(sql.indexOf("INCLUDE") < sql.indexOf("WITH"), sql);
			assertTrue(sql.indexOf("WITH") < sql.indexOf("WHERE"), sql);
		}
	}

	@Test
	void preservesExplicitNullOrderingAndRendersExpressionKeysAsSql() {
		Table table = new Table("items");
		table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR));
		var index = new com.sqlapp.data.schemas.Index("expression_index");
		index.setIndexType(com.sqlapp.data.schemas.IndexType.Function);
		table.getIndexes().add(index);
		index.getColumns().add("lower(label)", com.sqlapp.data.schemas.Order.Asc);
		index.getColumns().get(0).setNullsOrder(com.sqlapp.data.schemas.NullsOrder.NullsFirst);
		String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("lower(label)"), sql);
		assertFalse(sql.contains("\"lower(label)\""), sql);
		assertTrue(sql.contains("ASC NULLS FIRST"), sql);
		index.getColumns().get(0).setOrder(com.sqlapp.data.schemas.Order.Desc)
				.setNullsOrder(com.sqlapp.data.schemas.NullsOrder.NullsLast);
		assertTrue(sqlFactoryRegistry.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("DESC NULLS LAST"));
	}

	@Test
	void nullsNotDistinctHasCorrectClauseOrderAndIgnoresInapplicableConfiguration() {
		Table table = new Table("items");
		table.getColumns().add("code", c -> c.setDataType(DataType.INT));
		table.getColumns().add("label", c -> c.setDataType(DataType.VARCHAR));
		var index = table.getIndexes().add("unique_code");
		index.setUnique(true);
		index.getColumns().add("code");
		index.getIncludes().add("label");
		index.setWhere("length(label) > 0");
		index.getSpecifics().put(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "true");
		for (int major : new int[] {11, 14, 15, 18}) {
			var registry = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect("postgres", major, 0, null).createSqlFactoryRegistry();
			if (major < 15) {
				assertFalse(registry.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS NOT DISTINCT"));
			} else {
				String sql = registry.createSql(index, SqlType.CREATE).get(0).getSqlText();
				assertTrue(sql.contains("CREATE UNIQUE INDEX"), sql);
				assertTrue(sql.indexOf("NULLS NOT DISTINCT") > sql.indexOf("INCLUDE"), sql);
				assertTrue(sql.indexOf("NULLS NOT DISTINCT") < sql.indexOf("WHERE"), sql);
			}
		}
		var modern = com.sqlapp.data.db.dialect.postgres.DialectHolder.postgreSQL150.createSqlFactoryRegistry();
		index.getSpecifics().put(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "invalid");
		assertFalse(modern.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS NOT DISTINCT"));
		index.getSpecifics().put(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "true");
		index.setUnique(false);
		assertFalse(modern.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS NOT DISTINCT"));
		index.getSpecifics().put(PostgresCreateIndexFactory.NULLS_NOT_DISTINCT, "false");
		assertFalse(modern.createSql(index, SqlType.CREATE).get(0).getSqlText().contains("NULLS NOT DISTINCT"));
	}

	@Test
	void emitsTypedSequencesOnlyFromPostgres10() {
		for (int major : new int[] {9, 10, 11, 15}) {
			var registry = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
					.getDialect("postgres", major, major == 9 ? 6 : 0, null).createSqlFactoryRegistry();
			for (var type : java.util.List.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT)) {
				var sequence = new com.sqlapp.data.schemas.Sequence("typed_seq").setDataType(type);
				String sql = registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText();
				assertEquals(major >= 10, sql.contains(" AS "), sql);
				if (major >= 10) assertTrue(sql.contains("AS " + type.name()), sql);
			}
		}
		var registry = com.sqlapp.data.db.dialect.postgres.DialectHolder.postgreSQL150.createSqlFactoryRegistry();
		var alias = new com.sqlapp.data.schemas.Sequence("alias_seq").setDataTypeName("int4");
		assertTrue(registry.createSql(alias, SqlType.CREATE).get(0).getSqlText().contains("AS INT"));
		alias.setDataType(DataType.DECIMAL);
		assertFalse(registry.createSql(alias, SqlType.CREATE).get(0).getSqlText().contains(" AS "));
	}

}
