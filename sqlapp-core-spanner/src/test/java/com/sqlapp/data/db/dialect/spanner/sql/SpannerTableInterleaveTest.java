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
	void defaultsExplicitParentRelationshipToNoActionAndKeepsAlterGuard() {
		final var table = child();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		final String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains(", INTERLEAVE IN PARENT `Sales`.`Parent` ON DELETE NO ACTION"), sql);
		final var changed = table.clone();
		changed.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table.diff(changed), SqlType.ALTER));
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
