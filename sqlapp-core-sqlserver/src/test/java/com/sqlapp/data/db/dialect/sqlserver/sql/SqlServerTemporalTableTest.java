/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class SqlServerTemporalTableTest {
	private Table table() {
		Table table = new Table("Current Data").setSchemaName("dbo");
		table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_current", table.getColumns().get("id"));
		table.getColumns().add(new Column("Valid From").setDataType(DataType.TIMESTAMP).setNotNull(true).setHidden(true));
		table.getColumns().add(new Column("Valid To").setDataType(DataType.TIMESTAMP).setNotNull(true));
		table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME").setPeriodType(TemporalPeriodType.SYSTEM_TIME)
				.setStartColumnName("Valid From").setEndColumnName("Valid To"));
		table.toSystemVersioning().setHistoryTableSchemaName("history schema").setHistoryTableName("Old]Data");
		table.getSpecifics().put("temporal_type", "SYSTEM_VERSIONED_TEMPORAL_TABLE");
		return table;
	}

	private SqlFactoryRegistry registry(int version) {
		var registry = SqlServerDialectResolver.getInstance().getDialect(version, 0).createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		return registry;
	}

	@Test
	void createPreservesPeriodHiddenAndQuotedHistoryIdentityAcrossVersions() {
		for (int version : new int[] {13, 14, 15, 16}) {
			var operations = registry(version).createSql(table(), SqlType.CREATE);
			String create = operations.get(0).getSqlText();
			assertTrue(create.contains("GENERATED ALWAYS AS ROW START HIDDEN"), create);
			assertTrue(create.contains("GENERATED ALWAYS AS ROW END"), create);
			assertTrue(create.replaceAll("\\s+", " ").contains("PERIOD FOR SYSTEM_TIME ( [Valid From], [Valid To])"), create);
			assertFalse(create.contains("TEMPORAL_TYPE"), create);
			String enable = operations.get(operations.size()-1).getSqlText();
			assertTrue(enable.contains("HISTORY_TABLE = [history schema].[Old]]Data]"), enable);
			assertTrue(enable.contains("DATA_CONSISTENCY_CHECK = ON"), enable);
		}
	}

	@Test
	void disabledVersioningRetainsPeriodAndAnonymousHistoryNeedsNoConfiguration() {
		Table table = table();
		table.toSystemVersioning().setEnable(false);
		assertFalse(registry(13).createSql(table, SqlType.CREATE).stream()
				.anyMatch(operation -> operation.getSqlText().contains("SYSTEM_VERSIONING = ON")));
		table.toSystemVersioning().setEnable(true).setHistoryTableName(null).setHistoryTableSchemaName(null);
		var operations = registry(13).createSql(table, SqlType.CREATE);
		assertTrue(operations.get(operations.size()-1).getSqlText().contains("SYSTEM_VERSIONING = ON)"));
	}

	@Test
	void schemaAndCatalogCreateHistoryBeforeEnablingVersioning() {
		Catalog catalog = new Catalog("db");
		Schema current = new Schema("dbo"), history = new Schema("history schema");
		catalog.getSchemas().add(current);
		catalog.getSchemas().add(history);
		current.getTables().add(table());
		Table historical = new Table("Old]Data");
		historical.getColumns().add(new Column("id").setDataType(DataType.INT));
		history.getTables().add(historical);
		var operations = registry(16).createSql(catalog, SqlType.CREATE);
		assertTrue(operations.get(operations.size()-1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
		assertTrue(operations.stream().anyMatch(operation -> operation.getSqlText().contains("CREATE TABLE [history schema].[Old]]Data]")));
		var sameSchema = new Schema("dbo");
		sameSchema.getTables().add(table());
		sameSchema.getTables().add(historical.clone());
		var schemaSql = registry(13).createSql(sameSchema, SqlType.CREATE);
		assertTrue(schemaSql.get(schemaSql.size()-1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
	}

	@Test
	void dropDisablesVersioningWithoutDeletingHistoryImplicitly() {
		var operations = registry(13).createSql(table(), SqlType.DROP);
		assertTrue(operations.get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
		assertTrue(operations.get(1).getSqlText().contains("DROP TABLE"));
		assertFalse(operations.stream().anyMatch(operation -> operation.getSqlText().contains("Old]]Data")));
		SqlFactory<Table> factory = registry(13).getSqlFactory(table(), SqlType.DROP);
		Table history = new Table("history");
		var collection = factory.createSql(List.of(history, table()));
		assertTrue(collection.get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
	}

	@Test
	void invalidConfigurationFailsBeforeGeneratingSql() {
		for (int version : new int[] {8, 9, 10, 11, 12})
            assertThrows(IllegalArgumentException.class, () -> registry(version).createSql(table(), SqlType.CREATE));
		Table table = table();
		table.getTemporalPeriods().get(0).setEndColumnName("missing");
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(table, SqlType.CREATE));
		Table nullable = table();
		nullable.getColumns().get("Valid From").setNotNull(false);
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(nullable, SqlType.CREATE));
		Table missingPeriod = table();
		missingPeriod.getTemporalPeriods().clear();
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(missingPeriod, SqlType.CREATE));
		Table ambiguousHistory = table();
		ambiguousHistory.toSystemVersioning().setHistoryTableSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(ambiguousHistory, SqlType.CREATE));
		Table noKey = table();
		noKey.getConstraints().clear();
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(noKey, SqlType.CREATE));
	}

	private List<SqlOperation> alter(Table original, Table target) {
		SqlFactory<Table> factory = registry(13).getSqlFactory(target, SqlType.ALTER);
		return factory.createDiffSql(original.diff(target));
	}

	@Test
	void versioningCanBeDisabledAndReenabledWithoutLosingHistoryIdentity() {
		Table original = table(), disabled = original.clone();
		disabled.toSystemVersioning().setEnable(false);
		var off = alter(original, disabled);
		assertEquals(1, off.size());
		assertTrue(off.get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
		var on = alter(disabled, original);
		assertEquals(1, on.size());
		assertTrue(on.get(0).getSqlText().contains("HISTORY_TABLE = [history schema].[Old]]Data]"));
		assertTrue(on.get(0).getSqlText().contains("DATA_CONSISTENCY_CHECK = ON"));
		assertTrue(alter(original, original.clone()).isEmpty());
	}

	@Test
	void periodHiddenChangesDoNotInterruptVersioning() {
		Table original = table(), target = original.clone();
		target.getColumns().get("Valid From").setHidden(false);
		target.getColumns().get("Valid To").setHidden(true);
		var sql = alter(original, target);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("DROP HIDDEN"));
		assertTrue(sql.get(1).getSqlText().contains("ADD HIDDEN"));
		assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("SYSTEM_VERSIONING")));
		target.toSystemVersioning().setEnable(false);
		var off = alter(original, target);
		assertTrue(off.get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
		var on = alter(target, original);
		assertTrue(on.get(on.size()-1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
	}

	@Test
	void reenableRequiresExplicitHistoryAndOtherChangesFailBeforeReturningSql() {
		Table original = table();
		original.toSystemVersioning().setHistoryTableName(null).setHistoryTableSchemaName(null).setEnable(false);
		Table enabled = original.clone();
		enabled.toSystemVersioning().setEnable(true);
		assertThrows(IllegalArgumentException.class, () -> alter(original, enabled));
		Table current = table(), structural = current.clone();
		structural.getColumns().add(new Column("extra").setDataType(DataType.INT).setNotNull(true));
		assertThrows(IllegalArgumentException.class, () -> alter(current, structural));
		Table changedHistory = current.clone();
		changedHistory.toSystemVersioning().setHistoryTableName("Other");
		assertThrows(IllegalArgumentException.class, () -> alter(current, changedHistory));
		Table changedPeriod = current.clone();
		changedPeriod.getTemporalPeriods().get(0).setName("Other");
		assertThrows(IllegalArgumentException.class, () -> alter(current, changedPeriod));
		Table unrelated = current.clone();
		unrelated.getColumns().get("id").setHidden(true);
		assertThrows(IllegalArgumentException.class, () -> alter(current, unrelated));
	}
	@Test
	void explicitHistoryCanBeSuppliedAfterReadingADisabledPeriod() {
		Table original = table();
		original.toSystemVersioning().setEnable(false).setHistoryTableName(null).setHistoryTableSchemaName(null);
		original.getSpecifics().put("temporal_type", "NON_TEMPORAL_TABLE");
		Table target = original.clone();
		target.toSystemVersioning().setEnable(true).setHistoryTableSchemaName("audit").setHistoryTableName("History");
		target.getSpecifics().put("temporal_type", "SYSTEM_VERSIONED_TEMPORAL_TABLE");
		var sql = alter(original, target);
		assertEquals(1, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("HISTORY_TABLE = audit.History"));
	}

	private Table withoutPeriod(Table temporal) {
		Table ordinary = temporal.clone();
		ordinary.getTemporalPeriods().clear();
		ordinary.setSystemVersioning(null);
		ordinary.getSpecifics().remove("temporal_type");
		ordinary.getColumns().get("Valid From").setHidden(false);
		ordinary.getColumns().get("Valid To").setHidden(false);
		return ordinary;
	}

	@Test
	void existingColumnsCanBecomeTemporalWithoutReplacingData() {
		Table target = table(), original = withoutPeriod(target);
		var sql = alter(original, target);
		assertEquals(3, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("ADD PERIOD FOR SYSTEM_TIME"));
		assertTrue(sql.get(1).getSqlText().contains("ADD HIDDEN"));
		assertTrue(sql.get(2).getSqlText().contains("SYSTEM_VERSIONING = ON"));
		assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("DROP TABLE")));
		target.setSystemVersioning(null);
		target.getSpecifics().remove("temporal_type");
		assertEquals(2, alter(original, target).size());
	}

	@Test
	void removalDisablesVersioningAndRevealsColumnsBeforeDroppingOnlyPeriod() {
		Table original = table(), target = withoutPeriod(original);
		var sql = alter(original, target);
		assertEquals(3, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
		assertTrue(sql.get(1).getSqlText().contains("DROP HIDDEN"));
		assertTrue(sql.get(2).getSqlText().contains("DROP PERIOD FOR SYSTEM_TIME"));
		assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("DROP TABLE")));
		original.toSystemVersioning().setEnable(false);
		original.getSpecifics().put("temporal_type", "NON_TEMPORAL_TABLE");
		assertEquals(2, alter(original, target).size());
	}

	@Test
	void periodTransitionsRejectMixedStructuralChanges() {
		Table target = table(), original = withoutPeriod(target);
		target.getColumns().get("id").setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		Table current = table(), removed = withoutPeriod(current);
		removed.getColumns().get("Valid From").setHidden(true);
		assertThrows(IllegalArgumentException.class, () -> alter(current, removed));
	}

	@Test
	void nullableColumnsCanBeAppendedWithoutInterruptingHistory() {
		Table original = table(), target = original.clone();
		target.getColumns().add(new Column("Extra Value").setDataType(DataType.NVARCHAR).setLength(80));
		target.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL).setLength(12).setScale(2));
		for (int version : new int[] {13, 14, 15, 16}) {
			SqlFactory<Table> factory = registry(version).getSqlFactory(target, SqlType.ALTER);
			assertEquals(2, factory.createDiffSql(original.diff(target)).size());
		}
		var sql = alter(original, target);
		assertEquals(2, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("ADD [Extra Value] NVARCHAR(80) NULL"), sql.toString());
		assertTrue(sql.get(1).getSqlText().contains("DECIMAL(12,2) NULL"), sql.toString());
		assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("SYSTEM_VERSIONING")));
	}

	@Test
	void columnAdditionsRejectAdvancedPropertiesAndMixedChanges() {
		Table original = table();
		for (Column column : List.of(
				new Column("extra").setDataType(DataType.INT).setNotNull(true),
				new Column("extra").setDataType(DataType.INT).setIdentity(true),
				new Column("extra").setDataType(DataType.INT).setDefaultValue("0"),
				new Column("extra").setDataType(DataType.INT).setFormula("id + 1"),
				new Column("extra").setDataType(DataType.INT).setHidden(true),
				new Column("extra").setDataType(DataType.INT).setCheck("extra > 0"))) {
			Table target = original.clone();
			target.getColumns().add(column);
			assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		}
		Table mixed = original.clone();
		mixed.getColumns().add(new Column("extra").setDataType(DataType.INT));
		mixed.getColumns().get("id").setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> alter(original, mixed));
		Table disabled = original.clone();
		disabled.toSystemVersioning().setEnable(false);
		Table added = disabled.clone();
		added.getColumns().add(new Column("extra").setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> alter(disabled, added));
	}
}
