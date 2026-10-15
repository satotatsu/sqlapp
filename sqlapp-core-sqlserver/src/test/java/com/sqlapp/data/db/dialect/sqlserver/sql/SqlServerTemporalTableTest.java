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

	@Test
	void temporalAlterFailsExplicitlyInsteadOfLosingChanges() {
		Table original = table(), target = original.clone();
		target.toSystemVersioning().setEnable(false);
		SqlFactory<Table> factory = registry(13).getSqlFactory(target, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(target)));
	}
}
