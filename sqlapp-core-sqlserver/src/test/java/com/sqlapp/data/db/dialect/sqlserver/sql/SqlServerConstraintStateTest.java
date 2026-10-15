/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class SqlServerConstraintStateTest {
	private Table table() {
		Table table = new Table("Child Data").setSchemaName("dbo");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		return table;
	}

	private String text(List<SqlOperation> operations) {
		return operations.stream().map(SqlOperation::getSqlText).collect(java.util.stream.Collectors.joining(";"));
	}

	@Test
	void checkStatesAreRestoredByStandaloneTableAndAlterAcrossVersions() throws Exception {
		for (int version : new int[] { 9, 10, 11, 12, 13, 14, 15, 16 }) {
			var registry = SqlServerDialectResolver.getInstance().getDialect(version, 0).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (boolean enabled : new boolean[] { false, true }) {
				Table original = table(), target = original.clone();
				CheckConstraint check = new CheckConstraint("Check Data]", "ID > 0");
				check.setEnable(enabled);
				check.getSpecifics().put("is_not_trusted", "true");
				check.getSpecifics().put("is_not_for_replication", "1");
				target.getConstraints().add(check);
				String standalone = text(registry.createSql(check, SqlType.CREATE));
				assertTrue(standalone.contains("WITH NOCHECK ADD CONSTRAINT [Check Data]]] CHECK NOT FOR REPLICATION"),
						standalone);
				assertEquals(!enabled, standalone.contains("NOCHECK CONSTRAINT"));
				String create = text(registry.createSql(target, SqlType.CREATE));
				assertTrue(create.contains("CHECK NOT FOR REPLICATION"), create);
				assertTrue(create.contains("NOCHECK CONSTRAINT [Check Data]]]"), create);
				assertEquals(enabled, create.contains(" CHECK CONSTRAINT [Check Data]]]"));
				SqlFactory<Table> alter = registry.getSqlFactory(target, SqlType.ALTER);
				assertEquals(standalone, text(alter.createDiffSql(original.diff(target))));
				Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
				assertEquals(create, text(registry.createSql(restored, SqlType.CREATE)));
				assertTrue(alter.createDiffSql(target.diff(target.clone())).isEmpty());
			}
		}
	}

	@Test
	void trustedConstraintsValidateExistingDataAndFalseReplicationIsOmitted() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table table = table();
		CheckConstraint check = new CheckConstraint("CK", "ID > 0");
		check.getSpecifics().put("is_not_trusted", "0");
		check.getSpecifics().put("is_not_for_replication", "false");
		table.getConstraints().add(check);
		String sql = text(registry.createSql(check, SqlType.CREATE));
		assertTrue(sql.contains("WITH CHECK ADD"), sql);
		assertFalse(sql.contains("NOT FOR REPLICATION"));
		assertEquals(1, registry.createSql(table, SqlType.CREATE).size());
	}

	@Test
	void foreignKeyReplicationFollowsReferenceAndCascade() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table parent = new Table("Parent Data"), child = table();
		parent.getColumns().add(new Column("ID").setDataType(DataType.INT));
		ForeignKeyConstraint fk = new ForeignKeyConstraint("FK Data", child.getColumns().get(0),
				parent.getColumns().get(0));
		fk.setDeleteRule(CascadeRule.Cascade);
		fk.setEnable(false);
		fk.getSpecifics().put("is_not_for_replication", "true");
		child.getConstraints().add(fk);
		String sql = text(registry.createSql(fk, SqlType.CREATE));
		assertTrue(sql.contains("WITH NOCHECK ADD"), sql);
		assertTrue(sql.indexOf("ON DELETE CASCADE") < sql.indexOf("NOT FOR REPLICATION"), sql);
		assertTrue(sql.contains("NOCHECK CONSTRAINT [FK Data]"), sql);
	}

	@Test
	void columnChecksRetainNamesReplicationAndStates() throws Exception {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table table = table();
		CheckConstraint check = new CheckConstraint("Column Check", "ID > 0");
		check.getSpecifics().put("is_not_trusted", "true");
		check.getSpecifics().put("is_not_for_replication", "true");
		table.getColumns().get(0).setCheckConstraint(check);
		String sql = text(registry.createSql(table, SqlType.CREATE));
		assertTrue(sql.contains("CONSTRAINT [Column Check] CHECK NOT FOR REPLICATION"), sql);
		assertTrue(sql.contains("NOCHECK CONSTRAINT [Column Check]"), sql);
		assertTrue(sql.contains(" CHECK CONSTRAINT [Column Check]"), sql);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals(sql, text(registry.createSql(restored, SqlType.CREATE)));
		Table original = table(), target = original.clone();
		target.getColumns().add(new Column("Extra").setDataType(DataType.INT).setCheckConstraint(check.clone()));
		SqlFactory<Table> alter = registry.getSqlFactory(target, SqlType.ALTER);
		String added = text(alter.createDiffSql(original.diff(target)));
		assertTrue(added.contains("WITH NOCHECK ADD Extra"), added);
		assertFalse(added.substring(added.indexOf(" ADD ")).contains("WITH NOCHECK"), added);
	}

	@Test
	void existingColumnChecksChangeThroughConstraintsWithoutAlteringStoredColumns() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table original = table();
		original.getColumns().get(0).setCheckConstraint(new CheckConstraint("Column Check", "ID > 0"));
		Table target = original.clone();
		var check = target.getColumns().get(0).getCheckConstraint();
		check.setEnable(false);
		check.getSpecifics().put("is_not_trusted", "true");
		SqlFactory<Table> alter = registry.getSqlFactory(target, SqlType.ALTER);
		String sql = text(alter.createDiffSql(original.diff(target)));
		assertFalse(sql.contains("DROP CONSTRAINT"), sql);
		assertFalse(sql.contains("ADD CONSTRAINT"), sql);
		assertTrue(sql.contains("NOCHECK CONSTRAINT [Column Check]"), sql);
		assertFalse(sql.contains("ALTER COLUMN"), sql);
		Schema originalSchema = new Schema("dbo"), targetSchema = new Schema("dbo");
		originalSchema.getTables().add(original.clone());
		targetSchema.getTables().add(target.clone());
		SqlFactory<Schema> schemaAlter = registry.getSqlFactory(targetSchema, SqlType.ALTER);
		assertTrue(text(schemaAlter.createDiffSql(originalSchema.diff(targetSchema)))
				.contains("NOCHECK CONSTRAINT [Column Check]"));
		check.setExpression("ID > 10");
		sql = text(alter.createDiffSql(original.diff(target)));
		assertTrue(sql.contains("ID > 10"), sql);
		assertTrue(sql.contains("DROP CONSTRAINT [Column Check]"), sql);
		assertFalse(sql.contains("ALTER COLUMN"), sql);
		target.getColumns().get(0).setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
		Table removed = original.clone();
		removed.getColumns().clear();
		sql = text(alter.createDiffSql(original.diff(removed)));
		assertTrue(sql.indexOf("DROP CONSTRAINT") < sql.indexOf("DROP COLUMN"), sql);
	}

	@Test
	void trustAndEnableChangesValidateInPlaceWithoutDroppingTheConstraint() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table original = table();
		CheckConstraint check = new CheckConstraint("CK", "ID > 0");
		check.setEnable(false);
		check.getSpecifics().put("is_not_trusted", "true");
		original.getConstraints().add(check);
		Table target = original.clone();
		target.getConstraints().get("CK").setEnable(true);
		target.getConstraints().get("CK").getSpecifics().put("is_not_trusted", "false");
		SqlFactory<Table> alter = registry.getSqlFactory(target, SqlType.ALTER);
		String sql = text(alter.createDiffSql(original.diff(target)));
		assertTrue(sql.contains("WITH CHECK CHECK CONSTRAINT CK"), sql);
		assertFalse(sql.contains("DROP CONSTRAINT"), sql);
		assertFalse(sql.contains("ADD CONSTRAINT"), sql);
	}

	@Test
	void invalidFlagsContradictoryStatesAndUnnamedStateChangesFail() {
		var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		for (String key : new String[] { "is_not_trusted", "is_not_for_replication" }) {
			Table table = table();
			CheckConstraint check = new CheckConstraint("CK", "ID > 0");
			table.getConstraints().add(check);
			check.getSpecifics().put(key, "tru");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		}
		Table table = table();
		CheckConstraint check = new CheckConstraint("CK", "ID > 0");
		table.getConstraints().add(check);
		check.setEnable(false);
		check.getSpecifics().put("is_not_trusted", "false");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
		check.getSpecifics().remove("is_not_trusted");
		check.setName(null);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(check, SqlType.CREATE));
	}
}
