/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerTableInterleaveTest {
	private Table child() {
		final var table = new Table("Child").setSchemaName("Sales");
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().get(0));
		table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Parent");
		table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
		return table;
	}

	@Test
	void changesDeleteActionAndRestoresTheExplicitParentDefaultThroughXml() throws Exception {
		final var table = child();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains(", INTERLEAVE IN PARENT `Sales`.`Parent` ON DELETE NO ACTION"), sql);
		final var changed = table.clone();
		changed.getSpecifics().put("on_delete_action", "cascade");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(changed.asXml()));
		final var operations = registry.createSql(table.diff(restored), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals("ALTER TABLE `Sales`.`Child` SET ON DELETE CASCADE", operations.get(0).getSqlText());
		assertEquals("ALTER TABLE `Sales`.`Child` SET ON DELETE NO ACTION",
				registry.createSql(restored.diff(table), SqlType.ALTER).get(0).getSqlText());
		final var explicitDefault = table.clone();
		explicitDefault.getSpecifics().put("on_delete_action", "NO ACTION");
		assertTrue(registry.createSql(table.diff(explicitDefault), SqlType.ALTER).isEmpty());
	}

	@Test
	void changesLegacyDeleteActionButRejectsAmbiguousAndChangedParentRelationships() {
		final var original = child();
		original.getSpecifics().remove(SpannerCreateTableFactory.INTERLEAVE_TYPE);
		original.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "NO ACTION");
		final var changed = original.clone();
		changed.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(changed), SqlType.ALTER).get(0).getSqlText()
				.endsWith("SET ON DELETE CASCADE"));
		final var ambiguous = original.clone();
		ambiguous.getSpecifics().remove(SpannerCreateTableFactory.ON_DELETE_ACTION);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(original.diff(ambiguous), SqlType.ALTER));
		changed.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "OtherParent");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changed), SqlType.ALTER));
	}

	@Test
	void rejectsAddingDeleteActionToUnenforcedInterleave() {
		final var original = child();
		original.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN");
		final var changed = original.clone();
		changed.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect
				.createSqlFactoryRegistry().createSql(original.diff(changed), SqlType.ALTER));
	}

	@Test
	void switchesRelationshipKindAndEnablesCascadeInASeparateOperation() throws Exception {
		final var original = child();
		original.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN");
		original.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Order Parent");
		final var target = original.clone();
		target.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
		target.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(List.of(
				"ALTER TABLE `Sales`.`Child` SET INTERLEAVE IN PARENT `Sales`.`Order Parent` ON DELETE NO ACTION",
				"ALTER TABLE `Sales`.`Child` SET ON DELETE CASCADE"),
				operations.stream().map(operation -> operation.getSqlText()).toList());
		final Table intermediate = operations.get(0).getTarget();
		assertEquals("NO ACTION", intermediate.getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION));
		assertSame(intermediate, operations.get(1).getOriginal());
		assertEquals("CASCADE", restored.getSpecifics().get(SpannerCreateTableFactory.ON_DELETE_ACTION));

		final var reverse = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, reverse.size());
		assertEquals("ALTER TABLE `Sales`.`Child` SET INTERLEAVE IN `Sales`.`Order Parent`",
				reverse.get(0).getSqlText());
		target.getSpecifics().remove(SpannerCreateTableFactory.ON_DELETE_ACTION);
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
	}

	@Test
	void refusesRelationshipChangesThatAlsoChangeTheParentOrColumns() {
		final var original = child();
		final var target = original.clone();
		target.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		target.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "OtherParent");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Parent");
		target.getColumns().add(new Column("value").setDataType(DataType.BIGINT));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void rejectsAmbiguousInvalidAndContradictoryDefinitions() {
		for (int mode = 0; mode < 6; mode++) {
			final var table = child();
			switch (mode) {
			case 0 -> table.getSpecifics().remove(SpannerCreateTableFactory.INTERLEAVE_TYPE);
			case 1 -> table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "unknown");
			case 2 -> table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "SET NULL");
			case 3 -> {
				table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN");
				table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
			}
			case 4 -> table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Child");
			case 5 -> table.getSpecifics().remove(SpannerCreateTableFactory.PARENT_TABLE_NAME);
			}
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		}
	}

	@Test
	void rejectsCyclicBatchDependenciesBeforeReturningSql() {
		final var first = child();
		final var second = new Table("Parent").setSchemaName("Sales");
		second.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		second.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Child");
		second.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN");
		final com.sqlapp.data.db.sql.SqlFactory<Table> factory = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(first, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(List.of(first, second)));
	}

	@Test
	void batchOrderingRetainsForeignKeyDependenciesOfAnInterleaveParent() {
		final var schema = new Schema("Sales");
		schema.setDialect(DialectHolder.defaultDialect);
		final var first = child();
		final var parent = new Table("Parent");
		parent.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		final var ancestor = new Table("Ancestor");
		ancestor.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		parent.getConstraints().addPrimaryKeyConstraint("pk_parent", parent.getColumns().get(0));
		ancestor.getConstraints().addPrimaryKeyConstraint("pk_ancestor", ancestor.getColumns().get(0));
		for (var table : List.of(first, parent, ancestor))
			schema.getTables().add(table);
		parent.getConstraints()
				.add(new ForeignKeyConstraint("fk", parent.getColumns().get(0), ancestor.getColumns().get(0))
						.setDeleteRule(CascadeRule.None).setUpdateRule(CascadeRule.None)
						.setMatchOption(ForeignKeyConstraint.MatchOption.Simple));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(first, SqlType.CREATE);
		final var sql = factory.createSql(List.of(first, parent, ancestor));
		assertEquals(3, sql.size());
		assertTrue(sql.get(0).getSqlText().contains("Ancestor"));
		assertTrue(sql.get(1).getSqlText().contains("Parent") && sql.get(1).getSqlText().contains("FOREIGN KEY"));
		assertTrue(sql.get(2).getSqlText().contains("Child"));
	}
}
