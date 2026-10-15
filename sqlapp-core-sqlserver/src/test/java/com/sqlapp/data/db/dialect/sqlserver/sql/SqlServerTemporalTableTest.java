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
		table.getColumns()
				.add(new Column("Valid From").setDataType(DataType.TIMESTAMP).setNotNull(true).setHidden(true));
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
	void temporalColumnCheckChangesAreRejectedEvenWhenOnlyStateChanges() {
		Table original = table();
		original.getColumns().get("id").setCheckConstraint(new CheckConstraint("CK_ID", "id > 0"));
		Table target = original.clone();
		target.getColumns().get("id").getCheckConstraint().setEnable(false);
		SqlFactory<Table> alter = registry(16).getSqlFactory(target, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
	}

	@Test
	void createPreservesPeriodHiddenAndQuotedHistoryIdentityAcrossVersions() {
		for (int version : new int[] { 13, 14, 15, 16 }) {
			var operations = registry(version).createSql(table(), SqlType.CREATE);
			String create = operations.get(0).getSqlText();
			assertTrue(create.contains("GENERATED ALWAYS AS ROW START HIDDEN"), create);
			assertTrue(create.contains("GENERATED ALWAYS AS ROW END"), create);
			assertTrue(create.replaceAll("\\s+", " ").contains("PERIOD FOR SYSTEM_TIME ( [Valid From], [Valid To])"),
					create);
			assertFalse(create.contains("TEMPORAL_TYPE"), create);
			String enable = operations.get(operations.size() - 1).getSqlText();
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
		assertTrue(operations.get(operations.size() - 1).getSqlText().contains("SYSTEM_VERSIONING = ON)"));
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
		assertTrue(operations.get(operations.size() - 1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
		assertTrue(operations.stream()
				.anyMatch(operation -> operation.getSqlText().contains("CREATE TABLE [history schema].[Old]]Data]")));
		var sameSchema = new Schema("dbo");
		sameSchema.getTables().add(table());
		sameSchema.getTables().add(historical.clone());
		var schemaSql = registry(13).createSql(sameSchema, SqlType.CREATE);
		assertTrue(schemaSql.get(schemaSql.size() - 1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
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
		for (int version : new int[] { 8, 9, 10, 11, 12 })
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
		assertTrue(on.get(on.size() - 1).getSqlText().contains("SYSTEM_VERSIONING = ON"));
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
		for (int version : new int[] { 13, 14, 15, 16 }) {
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
		for (Column column : List.of(new Column("extra").setDataType(DataType.INT).setNotNull(true),
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

	@Test
	void boundedVariableLengthColumnsCanExpandWhileVersioningRemainsEnabled() {
		for (DataType type : List.of(DataType.VARCHAR, DataType.NVARCHAR, DataType.VARBINARY)) {
			Table original = table();
			original.getColumns().add(new Column("Extra Value").setDataType(type).setLength(40));
			Table target = original.clone();
			target.getColumns().get("Extra Value").setLength(80);
			var sql = alter(original, target);
			assertEquals(1, sql.size());
			assertTrue(sql.get(0).getSqlText().contains("ALTER COLUMN [Extra Value] " + type.name() + "(80) NULL"),
					sql.toString());
			assertFalse(sql.get(0).getSqlText().contains("SYSTEM_VERSIONING"));
		}
	}

	@Test
	void wideningPreservesNullabilityCollationAndDoesNotRestateDefaultOrCheck() {
		Table original = table();
		original.getColumns().add(new Column("note").setDataType(DataType.NVARCHAR).setLength(40).setNotNull(true)
				.setCollation("Latin1_General_100_CI_AS").setDefaultValue("N''").setCheck("LEN(note)>=0"));
		Table target = original.clone();
		target.getColumns().get("note").setLength(80);
		String sql = alter(original, target).get(0).getSqlText();
		assertTrue(sql.contains("NOT NULL"), sql);
		assertTrue(sql.contains("COLLATE"), sql);
		assertFalse(sql.contains("DEFAULT"), sql);
		assertFalse(sql.contains("CHECK"), sql);
	}

	@Test
	void wideningRejectsShrinkMaxAndMixedChanges() {
		Table original = table();
		original.getColumns().add(new Column("note").setDataType(DataType.NVARCHAR).setLength(40));
		for (long length : new long[] { 20, 4001, Integer.MAX_VALUE }) {
			Table target = original.clone();
			target.getColumns().get("note").setLength(length);
			assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		}
		Table mixed = original.clone();
		mixed.getColumns().get("note").setLength(80).setNotNull(true);
		assertThrows(IllegalArgumentException.class, () -> alter(original, mixed));
		Table disabled = original.clone();
		disabled.toSystemVersioning().setEnable(false);
		Table wider = disabled.clone();
		wider.getColumns().get("note").setLength(80);
		assertThrows(IllegalArgumentException.class, () -> alter(disabled, wider));
	}

	@Test
	void combinedSafeAlterPlanPreservesInputsAndVersioningAcrossVersions() throws Exception {
		Table original = table();
		original.getColumns().add(new Column("note").setDataType(DataType.NVARCHAR).setLength(40).setNotNull(true));
		original.getColumns()
				.add(new Column("amount").setDataType(DataType.DECIMAL).setLength(12).setScale(2).setNotNull(true));
		Table target = original.clone();
		target.getColumns().get("note").setLength(80).setNotNull(false);
		target.getColumns().get("amount").setNotNull(false);
		target.getColumns().get("Valid From").setHidden(false);
		target.getColumns().get("Valid To").setHidden(true);
		target.getColumns().add(new Column("Extra Value").setDataType(DataType.VARCHAR).setLength(100));
		String originalXml = original.asXml(), targetXml = target.asXml();
		for (int version : new int[] { 13, 14, 15, 16 }) {
			SqlFactory<Table> factory = registry(version).getSqlFactory(target, SqlType.ALTER);
			var sql = factory.createDiffSql(original.diff(target));
			assertEquals(5, sql.size());
			assertTrue(sql.get(0).getSqlText().contains("NVARCHAR(80) NULL"), sql.toString());
			assertTrue(sql.get(1).getSqlText().contains("DECIMAL(12,2) NULL"), sql.toString());
			assertTrue(sql.get(2).getSqlText().contains("ADD [Extra Value] VARCHAR(100) NULL"), sql.toString());
			assertTrue(sql.get(3).getSqlText().contains("DROP HIDDEN"));
			assertTrue(sql.get(4).getSqlText().contains("ADD HIDDEN"));
			assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("SYSTEM_VERSIONING")));
		}
		assertEquals(originalXml, original.asXml());
		assertEquals(targetXml, target.asXml());
	}

	@Test
	void nullabilityCanRelaxButPrimaryKeysPeriodsAndTighteningAreRejected() {
		Table original = table();
		original.getColumns().add(new Column("value").setDataType(DataType.INT).setNotNull(true));
		Table target = original.clone();
		target.getColumns().get("value").setNotNull(false);
		assertTrue(alter(original, target).get(0).getSqlText().contains("ALTER COLUMN value INT NULL"));
		assertThrows(IllegalArgumentException.class, () -> alter(target, original));
		Table key = original.clone();
		key.getColumns().get("id").setNotNull(false);
		assertThrows(IllegalArgumentException.class, () -> alter(original, key));
		Table period = original.clone();
		period.getColumns().get("Valid From").setNotNull(false);
		assertThrows(IllegalArgumentException.class, () -> alter(original, period));
	}

	@Test
	void unsupportedChangeInCombinedPlanRejectsEntireDiff() {
		Table original = table();
		original.getColumns().add(new Column("note").setDataType(DataType.NVARCHAR).setLength(40));
		Table target = original.clone();
		target.getColumns().get("note").setLength(80);
		target.getColumns().add(new Column("extra").setDataType(DataType.INT));
		target.toSystemVersioning().setHistoryTableName("AnotherHistory");
		assertThrows(IllegalArgumentException.class, () -> alter(original, target));
		target.toSystemVersioning().setHistoryTableName(original.getSystemVersioning().getHistoryTableName());
		target.getColumns().get("id").setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> alter(original, target));
	}

	@Test
	void temporalConfigurationRejectsInvalidPrecisionAndHistoryIdentity() {
		Table mismatched = table();
		mismatched.getColumns().get("Valid From").setLength(3);
		mismatched.getColumns().get("Valid To").setLength(7);
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(mismatched, SqlType.CREATE));
		Table invalidPrecision = table();
		invalidPrecision.getColumns().get("Valid From").setLength(8);
		invalidPrecision.getColumns().get("Valid To").setLength(8);
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(invalidPrecision, SqlType.CREATE));
		Table self = table();
		self.toSystemVersioning().setHistoryTableName(self.getName()).setHistoryTableSchemaName(self.getSchemaName());
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(self, SqlType.CREATE));
		Table blank = table();
		blank.toSystemVersioning().setHistoryTableName(" ");
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(blank, SqlType.CREATE));
	}

	@Test
	void explicitlyNamedDatetime2PeriodsRemainSupported() {
		for (String type : List.of("datetime2", "DATETIME2(7)")) {
			Table table = table();
			table.getColumns().get("Valid From").setDataTypeName(type);
			table.getColumns().get("Valid To").setDataTypeName(type);
			assertDoesNotThrow(() -> registry(13).createSql(table, SqlType.CREATE));
		}
		Table malformed = table();
		malformed.getColumns().get("Valid From").setDataTypeName("DATETIME2_BAD");
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(malformed, SqlType.CREATE));
	}

	@Test
	void retentionCreateAndCombinedAlterPreserveExplicitSettings() throws Exception {
		for (int version : new int[] { 14, 15, 16 }) {
			Table original = table();
			com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
					.historyRetentionPeriod(original.toSystemVersioning(), "6 months");
			var registry = registry(version);
			var create = registry.createSql(original, SqlType.CREATE);
			assertFalse(create.get(0).getSqlText().contains("HISTORY_RETENTION_PERIOD"));
			assertTrue(create.get(create.size() - 1).getSqlText().contains("HISTORY_RETENTION_PERIOD = 6 MONTHS"));
			Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
			assertEquals("6 MONTHS", com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
					.getHistoryRetentionPeriod(restored.getSystemVersioning()));
			Table target = original.clone();
			com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
					.historyRetentionPeriod(target.toSystemVersioning(), "INFINITE");
			target.getColumns().add(new Column("extra").setDataType(DataType.INT));
			SqlFactory<Table> factory = registry.getSqlFactory(target, SqlType.ALTER);
			var sql = factory.createDiffSql(original.diff(target));
			assertEquals(2, sql.size());
			assertTrue(sql.get(0).getSqlText().contains("ADD extra INT NULL"));
			assertTrue(sql.get(1).getSqlText().contains("HISTORY_RETENTION_PERIOD = INFINITE"));
			assertFalse(sql.stream().anyMatch(operation -> operation.getSqlText().contains("SYSTEM_VERSIONING = OFF")));
			Table removed = original.clone();
			removed.toSystemVersioning().getSpecifics().remove("HISTORY_RETENTION_PERIOD");
			assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(removed)));
		}
	}

	@Test
	void retentionValidatesSyntaxVersionAndAnonymousHistory() {
		for (String invalid : List.of("", "0 DAYS", "-1 YEARS", "1 HOUR", "2 DAYS; DROP TABLE x", "2147483648 YEARS"))
			assertThrows(IllegalArgumentException.class,
					() -> com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.historyRetentionPeriod(table().toSystemVersioning(), invalid));
		Table table = table();
		table.toSystemVersioning().setHistoryTableName(null).setHistoryTableSchemaName(null);
		com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
				.historyRetentionPeriod(table.toSystemVersioning(), "  1   day  ");
		var create = registry(14).createSql(table, SqlType.CREATE);
		assertTrue(create.get(create.size() - 1).getSqlText().contains("HISTORY_RETENTION_PERIOD = 1 DAYS"));
		assertFalse(create.get(create.size() - 1).getSqlText().contains("HISTORY_TABLE"));
		assertThrows(IllegalArgumentException.class, () -> registry(13).createSql(table, SqlType.CREATE));
		table.toSystemVersioning().getSpecifics().put("HISTORY_RETENTION_PERIOD", "invalid");
		assertThrows(IllegalArgumentException.class, () -> registry(14).createSql(table, SqlType.CREATE));
	}

	@Test
	void retentionCanBeProvidedWhenEnablingAReadDisabledTable() {
		Table disabled = table();
		disabled.toSystemVersioning().setEnable(false).setHistoryTableName(null).setHistoryTableSchemaName(null);
		disabled.getSpecifics().put("temporal_type", "NON_TEMPORAL_TABLE");
		Table enabled = disabled.clone();
		enabled.toSystemVersioning().setEnable(true).setHistoryTableSchemaName("audit").setHistoryTableName("History");
		com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
				.historyRetentionPeriod(enabled.toSystemVersioning(), "6 MONTHS");
		SqlFactory<Table> factory = registry(14).getSqlFactory(enabled, SqlType.ALTER);
		var sql = factory.createDiffSql(disabled.diff(enabled));
		assertEquals(1, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("HISTORY_TABLE = audit.History"));
		assertTrue(sql.get(0).getSqlText().contains("HISTORY_RETENTION_PERIOD = 6 MONTHS"));
		assertThrows(IllegalArgumentException.class, () -> alter(disabled, enabled));
	}

	@Test
	void periodOnlyModelCanEnableVersioningOrChangeHiddenFlags() {
		Table original = table();
		original.setSystemVersioning(null);
		original.getSpecifics().remove("temporal_type");
		Table hidden = original.clone();
		hidden.getColumns().get("Valid From").setHidden(false);
		assertEquals(1, alter(original, hidden).size());
		Table target = original.clone();
		target.toSystemVersioning().setHistoryTableSchemaName("audit").setHistoryTableName("History");
		assertTrue(alter(original, target).get(0).getSqlText().contains("SYSTEM_VERSIONING = ON"));
	}

	@Test
	void disablingCanClearResetRetentionButCannotApplyANewPolicy() {
		Table original = table();
		com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
				.historyRetentionPeriod(original.toSystemVersioning(), "6 MONTHS");
		Table disabled = original.clone();
		disabled.toSystemVersioning().setEnable(false).getSpecifics().remove("HISTORY_RETENTION_PERIOD");
		SqlFactory<Table> factory = registry(14).getSqlFactory(disabled, SqlType.ALTER);
		assertTrue(
				factory.createDiffSql(original.diff(disabled)).get(0).getSqlText().contains("SYSTEM_VERSIONING = OFF"));
		com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
				.historyRetentionPeriod(disabled.toSystemVersioning(), "3 MONTHS");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(disabled)));
	}

	@Test
	void unsupportedAndOrphanedOptionsFailInsteadOfBeingIgnored() {
		Table unknown = table();
		unknown.toSystemVersioning().getSpecifics().put("TYPO_RETENTION", "6 MONTHS");
		assertThrows(IllegalArgumentException.class, () -> registry(14).createSql(unknown, SqlType.CREATE));
		Table orphan = withoutPeriod(table());
		orphan.toSystemVersioning().setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> registry(14).createSql(orphan, SqlType.CREATE));
		Table original = table();
		Table invalidTarget = original.clone();
		invalidTarget.toSystemVersioning().getSpecifics().put("TYPO_RETENTION", "6 MONTHS");
		SqlFactory<Table> factory = registry(14).getSqlFactory(invalidTarget, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(invalidTarget)));
	}
}
