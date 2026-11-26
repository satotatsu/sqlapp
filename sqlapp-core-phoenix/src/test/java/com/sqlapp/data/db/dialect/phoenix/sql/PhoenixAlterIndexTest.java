/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixAlterIndexTest {
	private Table table(boolean view, String state) {
		final Table table = view ? new View("Order Details").setStatement("SELECT * FROM base")
				: new Table("Order Details");
		table.setSchemaName("Sales");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("V").setDataType(DataType.INT));
		final var index = new Index("Index Details");
		index.getColumns().add("ID");
		if (state != null)
			index.getSpecifics().put(PhoenixAlterIndexFactory.STATE, state);
		table.getIndexes().add(index);
		return table;
	}

	@Test
	void altersStateThroughStandaloneAndTableViewDiffsWithoutDroppingIndexes() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (boolean view : new boolean[] { false, true }) {
				for (String[] transition : new String[][] { { "a", "DISABLE", "DISABLE" }, { "a", "x", "DISABLE" },
						{ "a", "d", "UNUSABLE" }, { "ACTIVE", "unusable", "UNUSABLE" },
						{ "UNUSABLE", "USABLE", "USABLE" } }) {
					final var original = table(view, transition[0]);
					final Table target = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
					target.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, transition[1]);
					final String before = original.asXml(), after = target.asXml();
					final var registry = dialect.createSqlFactoryRegistry();
					registry.getOptions().setDecorateSchemaName(true);
					final var standalone = registry
							.createSql(original.getIndexes().get(0).diff(target.getIndexes().get(0)), SqlType.ALTER);
					final var highLevel = registry.createSql(original.diff(target), SqlType.ALTER);
					assertEquals(1, highLevel.size());
					assertEquals(1, standalone.size());
					assertEquals("ALTER INDEX \"Index Details\" ON \"Sales\".\"Order Details\" " + transition[2],
							highLevel.get(0).getSqlText());
					assertEquals(standalone.get(0).getSqlText(), highLevel.get(0).getSqlText());
					assertEquals(SqlType.ALTER, highLevel.get(0).getSqlType());
					assertEquals(before, original.asXml());
					assertEquals(after, target.asXml());
				}
			}
		}
	}

	@Test
	void treatsEquivalentStatesAsNoOpsAndKeepsColumnChanges() {
		final var original = table(false, "a");
		final var target = original.clone();
		target.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, "ACTIVE");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).isEmpty());
		assertTrue(registry.createSql(original.getIndexes().get(0).diff(target.getIndexes().get(0)), SqlType.ALTER)
				.isEmpty());
		target.getColumns().add(new Column("EXTRA").setDataType(DataType.INT));
		target.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, "UNUSABLE");
		final var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(2, operations.size());
		assertTrue(operations.stream().anyMatch(op -> op.getSqlText().contains(" ADD ")));
		assertTrue(operations.stream().anyMatch(op -> op.getSqlText().endsWith(" UNUSABLE")));
		assertFalse(operations.stream().anyMatch(op -> op.getSqlText().startsWith("DROP")));
	}

	@Test
	void rejectsUnknownStatesAndDisabledReactivationWithoutImplicitRebuild() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String source : new String[] { "DISABLE", "x", "INACTIVE", "PENDING_DISABLE", "", null }) {
			final var original = table(false, source);
			final var target = original.clone();
			target.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, "ACTIVE");
			assertThrows(RuntimeException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		for (String targetState : new String[] { "REBUILD", "BUILDING", "garbage", "", null }) {
			final var original = table(false, "ACTIVE");
			final var target = original.clone();
			if (targetState == null)
				target.getIndexes().get(0).getSpecifics().remove(PhoenixAlterIndexFactory.STATE);
			else
				target.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, targetState);
			assertThrows(RuntimeException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		final var disabled = table(false, "DISABLE");
		assertTrue(registry.createSql(disabled.getIndexes().get(0), SqlType.REBUILD).get(0).getSqlText()
				.endsWith(" REBUILD"));
	}

	@Test
	void directOperationsAreConservativeAndStructuralChangesKeepTheExistingReplacementPath() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String state : List.of("DISABLE", "UNUSABLE")) {
			final var table = table(false, state);
			assertTrue(registry.createSql(table.getIndexes().get(0), SqlType.ALTER).get(0).getSqlText()
					.endsWith(" " + state));
		}
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(table(false, "USABLE").getIndexes().get(0), SqlType.ALTER));
		final var orphan = new Index("orphan");
		orphan.getSpecifics().put(PhoenixAlterIndexFactory.STATE, "DISABLE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(orphan, SqlType.ALTER));
		final var original = table(false, "ACTIVE");
		final var target = original.clone();
		target.getIndexes().get(0).getColumns().clear();
		target.getIndexes().get(0).getColumns().add("V");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.getIndexes().get(0).diff(target.getIndexes().get(0)), SqlType.ALTER));
		final var replaced = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(2, replaced.size());
		assertTrue(replaced.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(replaced.get(1).getSqlText().startsWith("CREATE INDEX"));
		final var moved = original.clone().setSchemaName("Other");
		moved.getIndexes().get(0).getSpecifics().put(PhoenixAlterIndexFactory.STATE, "UNUSABLE");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.getIndexes().get(0).diff(moved.getIndexes().get(0)), SqlType.ALTER));
	}
}
