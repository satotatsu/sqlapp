/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SpannerAlterTableFactoryTest {
	@Test
	void restartsExistingIdentityCountersOnlyWhenExplicitlyRequested() {
		final var original = table();
		original.getColumns().get("id").setIdentity(true);
		final var target = original.clone();
		target.getColumns().get("id").getSpecifics().put(SpannerAlterTableFactory.IDENTITY_RESTART_COUNTER_WITH, "12345");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `id` ALTER IDENTITY RESTART COUNTER WITH 12345",
				registry.createSql(original.diff(target), SqlType.ALTER).getFirst().getSqlText());
		target.getColumns().get("id").getSpecifics().put(SpannerAlterTableFactory.IDENTITY_RESTART_COUNTER_WITH, "0");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(target.diff(original), SqlType.ALTER));
	}

	@Test
	void changesScalarStringAndBytesWithoutDroppingTheColumn() {
		final var original = table();
		original.getColumns().get("value").setDataType(DataType.VARCHAR).setLength(30).setNotNull(true);
		final var target = original.clone();
		target.getColumns().get("value").setDataType(DataType.VARBINARY).setLength(40);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` BYTES(40) NOT NULL",
				registry.createSql(original.diff(target), SqlType.ALTER).getFirst().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` STRING(30) NOT NULL",
				registry.createSql(target.diff(original), SqlType.ALTER).getFirst().getSqlText());
		original.getColumns().get("value").setDefaultValue("'value'");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void renamesSelfReferencingTablesWithoutChangingTheirForeignKeys() throws Exception {
		final var original = table();
		original.getConstraints().add(new ForeignKeyConstraint("SelfReference",
				original.getColumns().get("value"), original.getColumns().get("id")));
		final var target = original.clone().setName("Renamed");
		final var targetReference = (ForeignKeyConstraint) target.getConstraints()
				.get("SelfReference");
		targetReference.setRelatedTableName("Renamed");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals("ALTER TABLE `Sales`.`events` RENAME TO `Sales`.`Renamed`",
				registry.createSql(original.diff(restored), SqlType.ALTER).getFirst().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`Renamed` RENAME TO `Sales`.`events`",
				registry.createSql(restored.diff(original), SqlType.ALTER).getFirst().getSqlText());
		assertEquals("events", ((ForeignKeyConstraint) original.getConstraints()
				.get("SelfReference")).getRelatedTableName());
		assertEquals("Renamed", ((ForeignKeyConstraint) restored.getConstraints()
				.get("SelfReference")).getRelatedTableName());
		final var changed = restored.clone();
		((ForeignKeyConstraint) changed.getConstraints().get("SelfReference"))
				.setDeleteRule(CascadeRule.Cascade);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changed), SqlType.ALTER));
		final var changedColumns = restored.clone();
		final var reference = (ForeignKeyConstraint) changedColumns.getConstraints().get("SelfReference");
		reference.getRelatedColumns().clear();
		reference.getRelatedColumns().add("value");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changedColumns), SqlType.ALTER));
		final var changedSchema = restored.clone();
		final var external = new Table("Renamed").setSchemaName("Archive");
		external.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		final var schemaReference = new ForeignKeyConstraint("SelfReference",
				changedSchema.getColumns().get("value"), external.getColumns().get("id"));
		changedSchema.getConstraints().remove(changedSchema.getConstraints().get("SelfReference"));
		changedSchema.getConstraints().add(schemaReference);
		assertEquals("Archive", schemaReference.getRelatedTableSchemaName());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changedSchema), SqlType.ALTER));
		final var changedTable = restored.clone();
		((ForeignKeyConstraint) changedTable.getConstraints().get("SelfReference"))
				.setRelatedTableName("Other");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changedTable), SqlType.ALTER));
	}

	@Test
	void renamesOnlyTheTableThroughXml() throws Exception {
		final var original = table();
		final var index = new com.sqlapp.data.schemas.Index("ById");
		index.getColumns().add("id");
		original.getIndexes().add(index);
		final var target = original.clone().setName("Order");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals("ALTER TABLE `Sales`.`events` RENAME TO `Sales`.`Order`",
				operations.getFirst().getSqlText());
		assertEquals(SqlType.ALTER, operations.getFirst().getSqlType());
		assertEquals("ALTER TABLE `Sales`.`Order` RENAME TO `Sales`.`events`",
				registry.createSql(restored.diff(original), SqlType.ALTER).getFirst().getSqlText());
		assertTrue(registry.createSql(restored.diff(restored), SqlType.ALTER).isEmpty());
		assertEquals("events", original.getIndexes().getFirst().getTableName());
		assertEquals("Order", restored.getIndexes().getFirst().getTableName());
		final var alteredIndex = restored.clone();
		alteredIndex.getIndexes().getFirst().setUnique(true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(alteredIndex), SqlType.ALTER));
	}

	@Test
	void rejectsRenameWithOtherChangesOrMissingNames() {
		final var original = table();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var target = original.clone().setName("Renamed");
		target.getColumns().add(new Column("extra").setDataType(DataType.BIGINT));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		final var moved = original.clone().setName("Renamed").setSchemaName("Archive");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(moved), SqlType.ALTER));
		final var blank = original.clone().setName(" ");
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(blank), SqlType.ALTER));
	}

	@Test
	void setsAndDropsCommitTimestampOnUpdateWithoutRestatingTheColumn() throws Exception {
		final var original = table();
		final var updatedAt = original.getColumns().get("value");
		updatedAt.setDataType(DataType.TIMESTAMP).setDefaultValue("PENDING_COMMIT_TIMESTAMP()");
		updatedAt.getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		final var target = original.clone();
		target.getColumns().get("value").setOnUpdate("pending_commit_timestamp ( )");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` SET ON UPDATE (PENDING_COMMIT_TIMESTAMP())",
				add.getFirst().getSqlText());
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` DROP ON UPDATE",
				drop.getFirst().getSqlText());
	}

	@Test
	void changesDefaultAndOnUpdateTogetherWhileRetainingNullability() {
		final var original = table();
		final var column = original.getColumns().get("value");
		column.setDataType(DataType.TIMESTAMP).setNotNull(true);
		column.getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		final var target = original.clone();
		target.getColumns().get("value").setDefaultValue("PENDING_COMMIT_TIMESTAMP()")
				.setOnUpdate("pending_commit_timestamp ( )");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var add = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` TIMESTAMP NOT NULL"
				+ " DEFAULT (PENDING_COMMIT_TIMESTAMP()) ON UPDATE (PENDING_COMMIT_TIMESTAMP())",
				add.getFirst().getSqlText());
		final var drop = registry.createSql(target.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `value` TIMESTAMP NOT NULL",
				drop.getFirst().getSqlText());
	}

	@Test
	void rejectsOnUpdateWithoutACompatibleExistingDefaultAndColumnOption() {
		final var original = table();
		final var updatedAt = original.getColumns().get("value");
		updatedAt.setDataType(DataType.TIMESTAMP);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var target = original.clone();
		target.getColumns().get("value").setOnUpdate("PENDING_COMMIT_TIMESTAMP()");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		for (final Table table : java.util.List.of(original, target)) {
			final var column = table.getColumns().get("value");
			column.getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
			column.setDefaultValue("CURRENT_TIMESTAMP()");
		}
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void setsAndClearsIdentitySkipRangeOnPrimaryKeyThroughXml() throws Exception {
		final var original = table();
		original.getColumns().get("id").setIdentity(true).setIdentityStartValue(2000L);
		final var target = original.clone();
		final var identity = target.getColumns().get("id");
		identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, 100L);
		identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 200L);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `id` ALTER IDENTITY SET SKIP RANGE 100, 200",
				add.getFirst().getSqlText());
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`events` ALTER COLUMN `id` ALTER IDENTITY SET NO SKIP RANGE",
				drop.getFirst().getSqlText());
	}

	@Test
	void rejectsIncompleteRangesAndMixedCounterChanges() {
		final var original = table();
		final var target = original.clone();
		final var identity = target.getColumns().get("id");
		identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, 100L);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		original.getColumns().get("id").setIdentity(true);
		identity.setIdentity(true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 50L);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		identity.getSpecifics().put(SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 150L);
		identity.setIdentityStartValue(123L);
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	private Table table() {
		final var table = new Table("events").setSchemaName("Sales");
		final var id = new Column("id").setDataType(DataType.BIGINT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("value").setDataType(DataType.BIGINT));
		table.getConstraints().addPrimaryKeyConstraint("pk", id);
		return table;
	}

	@Test
	void addsAndDropsColumnsThroughXmlWithNativeSyntax() throws Exception {
		final var original = table();
		final var target = original.clone();
		target.getColumns()
				.add(new Column("Order Count").setDataType(DataType.BIGINT).setNotNull(true).setDefaultValue("0"));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`events` ADD COLUMN IF NOT EXISTS `Order Count` INT64 NOT NULL DEFAULT (0)",
				add.get(0).getSqlText());
		assertEquals("0", restored.getColumns().get("Order Count").getDefaultValue());
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`events` DROP COLUMN `Order Count`", drop.get(0).getSqlText());
		registry.getOptions().setCreateIfNotExists(false);
		assertFalse(registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText()
				.contains("IF NOT EXISTS"));
	}

	@Test
	void setsChangesAndDropsDefaultWithoutRestatingColumnType() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		registry.getOptions().setQuateColumnName(true);
		final var original = table();
		final var first = original.clone();
		first.getColumns().get("value").setDefaultValue("1 + 2");
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` SET DEFAULT (1 + 2)",
				registry.createSql(original.diff(first), SqlType.ALTER).get(0).getSqlText());
		final var second = first.clone();
		second.getColumns().get("value").setDefaultValue("(4)");
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` SET DEFAULT ((4))",
				registry.createSql(first.diff(second), SqlType.ALTER).get(0).getSqlText());
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` DROP DEFAULT",
				registry.createSql(first.diff(original), SqlType.ALTER).get(0).getSqlText());
		assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
	}

	@Test
	void rejectsUnsupportedColumnAndTableChangesInsteadOfLosingSemantics() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		final var type = original.clone();
		type.getColumns().get("value").setDataType(DataType.DOUBLE).setDefaultValue("1");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(type), SqlType.ALTER));
		final var rename = original.clone();
		rename.getColumns().get("value").setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(rename), SqlType.ALTER));
		final var options = original.clone();
		options.getSpecifics().put(SpannerCreateTableFactory.FULLTEXT_DICTIONARY_TABLE, true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(options), SqlType.ALTER));
		final var formula = original.clone();
		formula.getColumns().get("value").setFormula("id + 1");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(formula), SqlType.ALTER));
		final var droppedKey = original.clone();
		droppedKey.getColumns().remove("id");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(droppedKey), SqlType.ALTER));
	}

	@Test
	void setsChangedTableOptionsFromXmlAndKeepsColumnChanges() throws Exception {
		final var original = table();
		original.getSpecifics().put("columnar_policy", "enabled");
		final var target = original.clone();
		target.getSpecifics().remove("columnar_policy");
		target.getSpecifics().put("locality_group", "cold");
		target.getColumns().get("value").setDefaultValue("0");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(2, operations.size());
		assertEquals("ALTER TABLE `Sales`.`events` SET OPTIONS (locality_group = 'cold', columnar_policy = NULL)",
				operations.get(0).getSqlText());
		assertTrue(operations.get(1).getSqlText().contains("SET DEFAULT (0)"));
	}

	@Test
	void changesDictionaryStalenessAndRejectsUndocumentedResets() {
		final var original = table();
		original.getSpecifics().put(SpannerCreateTableFactory.FULLTEXT_DICTIONARY_TABLE, true);
		final var target = original.clone();
		target.getSpecifics().put("fulltext_dictionary_staleness", "15m");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.endsWith("SET OPTIONS (fulltext_dictionary_staleness = '15m')"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(target.diff(original), SqlType.ALTER));
		target.getSpecifics().put("columnar_policy", "unknown");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void rejectsUnsafeNotNullAndOnUpdateDefaultChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		for (final var column : java.util.List.of(new Column("required").setDataType(DataType.BIGINT).setNotNull(true),
				new Column("required").setDataType(DataType.BIGINT).setArrayDimension(1).setNotNull(true)
						.setDefaultValue("[]"))) {
			final var target = original.clone();
			target.getColumns().add(column);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		original.getColumns().get("value").setDefaultValue("1").setOnUpdate("1");
		final var removed = original.clone();
		removed.getColumns().get("value").setDefaultValue(null);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(removed), SqlType.ALTER));
	}
}
