/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2SecurityAndMviewSqlTest extends AbstractDb2SqlFactoryTest {
	@Test
	void registeredSchemaAndCatalogAlterComposePermissionChanges() {
		Schema original = new Schema("APP"), target = new Schema("APP");
		var before = new Db2RowPermissions.Permission("APP", "P", "APP", "T", "ID>0", null, true, true, null, null,
				null);
		var after = new Db2RowPermissions.Permission("APP", "P", "APP", "T", "ID>10", null, true, true, null, null,
				null);
		Db2RowPermissions.set(original, List.of(before),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", true, false)));
		Db2RowPermissions.set(target, List.of(after),
				List.of(new Db2RowPermissions.AccessControl("APP", "T", false, false)));
		SqlFactory<Schema> factory = sqlFactoryRegistry.getSqlFactory(target, SqlType.ALTER);
		var statements = factory.createDiffSql(original.diff(target)).stream().map(o -> o.getSqlText()).toList();
		assertEquals("DROP PERMISSION \"APP\".\"P\"", statements.getFirst());
		assertTrue(statements.get(1).contains("ID>10 ENFORCED FOR ALL ACCESS ENABLE"), statements.toString());
		assertEquals("ALTER TABLE \"APP\".\"T\" DEACTIVATE ROW ACCESS CONTROL", statements.getLast());
		Catalog oldCatalog = new Catalog(), newCatalog = new Catalog();
		oldCatalog.getSchemas().add(original);
		newCatalog.getSchemas().add(target);
		SqlFactory<Catalog> catalogFactory = sqlFactoryRegistry.getSqlFactory(newCatalog, SqlType.ALTER);
		var catalogSql = catalogFactory.createDiffSql(oldCatalog.diff(newCatalog)).stream().map(o -> o.getSqlText())
				.toList();
		assertEquals(statements, catalogSql);
		assertFalse(factory.createDiffSql(target.diff(original)).isEmpty());
	}

	@Test
	void constraintsExcludeBackingIndexOrderingAndPreserveNotEnforced() {
		Table table = new Table("T").setSchemaName("APP").setDialect(dialect);
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT).setNullable(false));
		UniqueConstraint constraint = new UniqueConstraint("UK_T", false);
		table.getConstraints().add(constraint);
		constraint.getColumns().add(table.getColumns().get("ID"), Order.Desc);
		constraint.getIndex().setName("IX_BACKING");
		Index index = new Index("IX_BACKING").setUnique(true);
		table.getIndexes().add(index);
		index.getColumns().add(table.getColumns().get("ID"), Order.Desc);
		var statements = sql(table, SqlType.CREATE);
		assertEquals(1, statements.size());
		assertFalse(statements.getFirst().contains("DESC"), statements.toString());
		constraint.setEnable(false);
		var modern = DialectResolver.getInstance().getDialect("DB2", 11, 5, 0).createSqlFactoryRegistry();
		SqlFactory<UniqueConstraint> factory = modern.getSqlFactory(constraint, SqlType.CREATE);
		assertTrue(factory.createSql(constraint).getFirst().getSqlText().endsWith("NOT ENFORCED"));
		var legacy = DialectResolver.getInstance().getDialect("DB2", 10, 1, 0).createSqlFactoryRegistry();
		SqlFactory<UniqueConstraint> oldFactory = legacy.getSqlFactory(constraint, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> oldFactory.createSql(constraint));
	}

	private <T> List<String> sql(T object, SqlType type) {
		SqlFactory<T> factory = sqlFactoryRegistry.getSqlFactory(object, type);
		return factory.createSql(object).stream().map(o -> o.getSqlText().replaceAll("\\s+", " ").trim()).toList();
	}

	@Test
	void mqtUsesCompleteNativeDdlAndSeparateIndexesAndComments() {
		Mview mqt = new Mview("MQ");
		mqt.setSchemaName("APP");
		mqt.setDialect(dialect);
		String ddl = "CREATE TABLE APP.MQ AS (SELECT ID FROM APP.T) DATA INITIALLY DEFERRED REFRESH DEFERRED";
		mqt.setDefinition(ddl);
		mqt.setRemarks("MQT comment");
		mqt.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		mqt.getIndexes().add("IX_MQ", mqt.getColumns().get("ID"));
		var operations = sql(mqt, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertEquals(ddl, operations.getFirst());
		assertTrue(operations.get(1).contains("CREATE INDEX APP.IX_MQ ON APP.MQ"));
		assertEquals("COMMENT ON TABLE APP.MQ IS 'MQT comment'", operations.get(2));
		assertEquals(List.of("DROP TABLE APP.MQ"), sql(mqt, SqlType.DROP));
		mqt.setDefinition((String) null);
		assertThrows(IllegalArgumentException.class, () -> sql(mqt, SqlType.CREATE));
	}

	@Test
	void catalogCreatesAllTablesAndPermissionsBeforeActivation() {
		Catalog catalog = new Catalog();
		Schema a = new Schema("A"), b = new Schema("B");
		catalog.getSchemas().add(a);
		catalog.getSchemas().add(b);
		for (Schema schema : catalog.getSchemas()) {
			Table table = new Table("T");
			table.setDialect(dialect);
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			schema.getTables().add(table);
			Db2RowPermissions.set(schema,
					List.of(new Db2RowPermissions.Permission(schema.getName(), "P", "B", "T", "ID > 0", null, true,
							true, null, null, null)),
					List.of(new Db2RowPermissions.AccessControl(schema.getName(), "T", true, false)));
		}
		List<String> statements = sql(catalog, SqlType.CREATE);
		int lastPermission = -1, firstActivation = Integer.MAX_VALUE, lastTable = -1;
		for (int i = 0; i < statements.size(); i++) {
			if (statements.get(i).startsWith("CREATE TABLE"))
				lastTable = i;
			if (statements.get(i).startsWith("CREATE PERMISSION"))
				lastPermission = i;
			if (statements.get(i).contains("ACTIVATE ROW"))
				firstActivation = Math.min(firstActivation, i);
		}
		assertTrue(lastTable >= 0 && lastPermission > lastTable && firstActivation > lastPermission,
				statements.toString());
	}

	@Test
	void schemaIncludesMasksAndDoesNotAppendAnExtraCaseEnd() {
		Schema schema = new Schema("APP");
		Table table = new Table("T");
		table.getColumns().add("C", c -> c.setDataType(DataType.INT));
		schema.getTables().add(table);
		Mask mask = new Mask("M").setSchemaName("APP").setTableSchemaName("APP").setTableName("T").setColumnName("C");
		mask.setStatement("CASE WHEN C > 0 THEN C ELSE 0 END");
		schema.getMasks().add(mask);
		var operations = sql(schema, SqlType.CREATE);
		assertTrue(operations.stream().anyMatch(s -> s.contains("CREATE OR REPLACE MASK APP.M ON APP.T")),
				operations.toString());
		assertFalse(operations.stream().anyMatch(s -> s.contains("END END")));
		Schema changed = schema.clone();
		changed.getMasks().get("M").setEnable(false);
		SqlFactory<Schema> alterFactory = sqlFactoryRegistry.getSqlFactory(changed, SqlType.ALTER);
		var alterations = alterFactory.createDiffSql(schema.diff(changed)).stream().map(o -> o.getSqlText()).toList();
		assertTrue(
				alterations.stream()
						.anyMatch(s -> s.contains("CREATE OR REPLACE MASK") && s.stripTrailing().endsWith("DISABLE")),
				alterations.toString());
		mask.setDefinition("CREATE MASK APP.M ON APP.T FOR COLUMN C RETURN 0 DISABLE");
		assertEquals("CREATE MASK APP.M ON APP.T FOR COLUMN C RETURN 0 DISABLE", sql(mask, SqlType.CREATE).getFirst());
	}

	@Test
	void rejectsAccessControlsOnLegacyDialect() {
		var old = DialectResolver.getInstance().getDialect("DB2", 9, 7, 0).createSqlFactoryRegistry();
		Schema schema = new Schema("APP");
		Db2RowPermissions.set(schema, List.of(), List.of(new Db2RowPermissions.AccessControl("APP", "T", true, false)));
		SqlFactory<Schema> factory = old.getSqlFactory(schema, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(schema));
	}
}
