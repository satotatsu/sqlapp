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
	void inheritedTriggerComponentsKeepTargetIdentityAndRejectIgnoredConditions() {
		var trigger = new com.sqlapp.data.schemas.Trigger("before insert");
		trigger.setSchemaName("app");
		trigger.setTableSchemaName("app");
		trigger.setTableName("order details");
		trigger.setActionTiming("BEFORE");
		trigger.addEventManipulation("INSERT");
		trigger.setStatement("SET NEW.id = 1");
		SqlFactory<com.sqlapp.data.schemas.Trigger> factory = dialect.createSqlFactoryRegistry().getSqlFactory(trigger,
				SqlType.CREATE);
		String sql = factory.createSql(trigger).get(0).getSqlText();
		assertTrue(sql.contains("ON app.`order details`"), sql);
		trigger.setWhen("NEW.id > 0");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
		trigger.setWhen(null);
		trigger.setActionOrientation("STATEMENT");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
	}

	@Test
	void rejectsMySqlFunctionalSyntaxAndKeepsIndexedGeneratedColumns() {
		Table table = table();
		Index index = new Index("ix_expression");
		index.getColumns().add("lower(code)");
		table.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
		SqlFactory<Index> create = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> create.createSql(index));
		index.getColumns().clear();
		table.getColumns().add("lower_code",
				c -> c.setDataType(DataType.VARCHAR).setLength(30).setFormula("lower(code)"));
		index.getColumns().add(table.getColumns().get("lower_code"));
		String generated = sql(table, SqlType.CREATE);
		assertTrue(generated.contains("INDEX ix_expression ( lower_code"), generated);
		index.getIncludes().add(table.getColumns().get("id"));
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
		index.getIncludes().clear();
		var primary = new com.sqlapp.data.schemas.UniqueConstraint("pk_invalid", true);
		primary.getColumns().add(table.getColumns().get("id"));
		primary.getIndex().setEnable(false);
		table.getConstraints().add(primary);
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
	}

	@Test
	void inheritedTableDefaultsDoNotConvertColumns() {
		var original = new Table("table_defaults");
		original.getColumns().add("id", c -> c.setDataType(DataType.INT));
		original.setCharacterSet("utf8mb4");
		original.setCollation("utf8mb4_general_ci");
		assertTrue(
				sql(original, SqlType.CREATE).contains("DEFAULT CHARACTER SET =utf8mb4 COLLATE =utf8mb4_general_ci"));
		var target = original.clone();
		target.setCollation("utf8mb4_bin");
		SqlFactory<Table> alter = dialect.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
		assertEquals("ALTER TABLE table_defaults DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin",
				alter.createDiffSql(original.diff(target)).get(0).getSqlText());
	}

	@Test
	void inheritedSchemaDefaultsKeepBothOptions() {
		var schema = new com.sqlapp.data.schemas.Schema("app data");
		schema.setCharacterSet("utf8mb4");
		schema.setCollation("utf8mb4_general_ci");
		SqlFactory<com.sqlapp.data.schemas.Schema> create = dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.CREATE);
		assertEquals(
				"CREATE SCHEMA IF NOT EXISTS `app data` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci",
				create.createSql(schema).get(0).getSqlText());
		SqlFactory<com.sqlapp.data.schemas.Schema> alter = dialect.createSqlFactoryRegistry().getSqlFactory(schema,
				SqlType.ALTER);
		alter.getOptions().setSetSearchPathToSchema(false);
		var target = schema.clone();
		target.setCollation("utf8mb4_bin");
		assertEquals("ALTER SCHEMA `app data` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin",
				alter.createDiffSql(schema.diff(target)).get(0).getSqlText());
		target.setCollation(null);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(schema.diff(target)));
	}

	@Test
	void mySqlPrivilegeFactoriesDoNotReplaceMariaDbSelection() {
		var registry = dialect.createSqlFactoryRegistry();
		assertNull(((com.sqlapp.data.db.dialect.mysql.metadata.MySqlCatalogReader) dialect.getCatalogReader())
				.getSchemaPrivilegeReader());
		assertFalse(registry.getSqlFactory(new com.sqlapp.data.schemas.SchemaPrivilege(),
				SqlType.GRANT) instanceof com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantSchemaPrivilegeFactory);
		assertFalse(registry.getSqlFactory(new com.sqlapp.data.schemas.ObjectPrivilege(),
				SqlType.GRANT) instanceof com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantObjectPrivilegeFactory);
		assertFalse(registry.getSqlFactory(new com.sqlapp.data.schemas.ColumnPrivilege(),
				SqlType.GRANT) instanceof com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantColumnPrivilegeFactory);
		assertFalse(registry.getSqlFactory(new com.sqlapp.data.schemas.UserPrivilege(),
				SqlType.GRANT) instanceof com.sqlapp.data.db.dialect.mysql.sql.MySqlGrantUserPrivilegeFactory);
		assertFalse(registry.getSqlFactory(new com.sqlapp.data.schemas.ObjectPrivilege(),
				SqlType.REVOKE) instanceof com.sqlapp.data.db.dialect.mysql.sql.MySqlRevokeObjectPrivilegeFactory);
	}

	@Test
	void spatialSubtypesRemainNativeButMySqlSridRestrictionsAreRejected() {
		var column = new com.sqlapp.data.schemas.Column("location");
		dialect.setDbType("POINT", null, null, column);
		assertEquals(DataType.GEOMETRY, column.getDataType());
		Table table = new Table("places");
		table.setDialect(dialect);
		table.getColumns().add(column);
		assertTrue(sql(table, SqlType.CREATE).contains("location POINT"));
		column.getSpecifics().put(com.sqlapp.data.db.dialect.mysql.util.MySqlSpatialReference.SRID, "4326");
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
	}

	@Test
	void inheritedEventSqlRetainsScheduleAndReplicaState() {
		var event = new com.sqlapp.data.schemas.Event("scheduled job");
		event.setSchemaName("app");
		event.setStatement("SELECT 1");
		event.setDefiner("event owner@localhost");
		event.setIntervalValue(2);
		event.setIntervalField("HOUR");
		event.setStarts(java.sql.Timestamp.valueOf("2030-01-01 00:00:00"));
		event.setEnable(false);
		event.getSpecifics().put(com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateEventFactory.STATUS,
				"SLAVESIDE_DISABLED");
		SqlFactory<com.sqlapp.data.schemas.Event> factory = dialect.createSqlFactoryRegistry().getSqlFactory(event,
				SqlType.CREATE);
		String sql = factory.createSql(event).get(0).getSqlText();
		assertTrue(sql.startsWith("CREATE DEFINER = `event owner`@`localhost` EVENT"), sql);
		assertTrue(sql.contains("EVENT app.`scheduled job`"), sql);
		assertTrue(sql.contains("EVERY 2 HOUR STARTS '2030-01-01 00:00:00'"), sql);
		assertTrue(sql.contains("DISABLE ON SLAVE"), sql);
	}

	@Test
	void userAccountRoutineSqlDoesNotEnableMySqlRoleMetadata() {
		var catalog = dialect.getCatalogReader();
		assertFalse(catalog.getRoleReader() instanceof com.sqlapp.data.db.dialect.mysql.metadata.MySqlRole800Reader);
		assertNull(catalog.getRoleMemberReader());
		assertNull(catalog.getRoutinePrivilegeReader());
		var privilege = new com.sqlapp.data.schemas.RoutinePrivilege();
		privilege.setSchemaName("app");
		privilege.setObjectName("f");
		privilege.getSpecifics().put("ROUTINE_KIND", "FUNCTION");
		privilege.setGranteeName("'reader'@'localhost'");
		privilege.setPrivilege("EXECUTE");
		SqlFactory<com.sqlapp.data.schemas.RoutinePrivilege> factory = dialect.createSqlFactoryRegistry()
				.getSqlFactory(privilege, SqlType.GRANT);
		assertEquals("GRANT EXECUTE ON FUNCTION `app`.`f` TO `reader`@`localhost`",
				factory.createSql(privilege).get(0).getSqlText());
	}

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
