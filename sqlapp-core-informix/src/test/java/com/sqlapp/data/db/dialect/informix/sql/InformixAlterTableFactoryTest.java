/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class InformixAlterTableFactoryTest {
	private Table table() {
		final var table = new Table("items").setSchemaName("owner");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		return table;
	}

	private java.util.List<String> sql(Table original, Table target) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER)
				.stream().map(o -> o.getSqlText().replaceAll("\\s+", " ").strip()).toList();
	}

	@Test
	void modifiesTypeDefaultAndNullabilityWithoutMutatingModels() {
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).setDataType(DataType.BIGINT).setDefaultValue("42").setNotNull(true);
		assertEquals(java.util.List.of("ALTER TABLE \"owner\".\"items\" MODIFY ( \"id\" INT8 DEFAULT 42 NOT NULL)"),
				sql(original, target));
		assertEquals(DataType.INT, original.getColumns().get(0).getDataType());
		assertEquals(java.util.List.of("ALTER TABLE \"owner\".\"items\" MODIFY ( \"id\" INTEGER)"),
				sql(target, original));
		assertTrue(sql(original, original.clone()).isEmpty());
	}

	@Test
	void usesNativeTableAndColumnRenameSyntax() {
		final var original = table();
		final var target = original.clone().setName("renamed");
		assertEquals(java.util.List.of("RENAME TABLE \"owner\".\"items\" TO \"renamed\""), sql(original, target));
		final var renamedColumn = original.clone();
		renamedColumn.getColumns().get(0).setName("new_id");
		assertEquals(java.util.List.of("RENAME COLUMN \"owner\".\"items\".\"id\" TO \"new_id\""),
				sql(original, renamedColumn));
		renamedColumn.getColumns().get(0).setDataType(DataType.BIGINT);
		assertEquals(java.util.List.of("ALTER TABLE \"owner\".\"items\" MODIFY ( \"id\" INT8)",
				"RENAME COLUMN \"owner\".\"items\".\"id\" TO \"new_id\""), sql(original, renamedColumn));
	}

	@Test
	void addsAndDropsColumnsWithNativeSyntax() {
		final var original = table();
		final var target = original.clone();
		target.getColumns().add("value", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		assertEquals(java.util.List.of("ALTER TABLE \"owner\".\"items\" ADD ( \"value\" VARCHAR(30))"),
				sql(original, target));
		assertEquals(java.util.List.of("ALTER TABLE \"owner\".\"items\" DROP ( \"value\")"), sql(target, original));
	}

	@Test
	void rejectsFragmentChangesInBothDirectionsBeforeReturningOperations() {
		final var original = table();
		final var partitioning = new Partitioning();
		partitioning.getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, "E");
		final var partition = new Partition("p1").setTableSpaceName("rootdbs");
		partition.getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION, "id < 100");
		partitioning.getPartitions().add(partition);
		original.setPartitioning(partitioning);
		final var target = original.clone();
		target.getPartitioning().getPartitions().get(0).getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION, "id < 200");
		assertThrows(UnsupportedOperationException.class, () -> sql(original, target));
		assertThrows(UnsupportedOperationException.class, () -> sql(target, original));
		assertTrue(sql(original, original.clone()).isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> sql(table(), table().setTableSpaceName("new_space")));
	}

	@Test
	void rejectsModifyThatWouldDropKeysOrReferencingConstraints() {
		final var original = table();
		original.setPrimaryKey("pk", original.getColumns().get(0));
		final var target = original.clone();
		target.getColumns().get(0).setDataType(DataType.BIGINT);
		assertThrows(UnsupportedOperationException.class, () -> sql(original, target));
	}

	@Test
	void preservesNamedNotNullWhenModifyingType() {
		final var original = table();
		original.getColumns().get(0).setNotNull(true);
		final var notNull = new NotNullConstraint("nn").setColumn(original.getColumns().get(0));
		original.getConstraints().add(notNull);
		final var target = original.clone();
		target.getColumns().get(0).setDataType(DataType.BIGINT);
		assertTrue(sql(original, target).get(0).contains("NOT NULL CONSTRAINT \"nn\""));
	}
}
