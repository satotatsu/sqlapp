/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIndexLifecycleTest {
	private Table table() {
		Table table = new Table("T").setSchemaName("Mixed Owner");
		table.getColumns().add("ID");
		Index index = new Index("Mixed Index");
		index.getColumns().add("ID");
		table.getIndexes().add(index);
		index.getSpecifics().put("oracle.index.status", "VALID");
		index.getSpecifics().put("oracle.index.visibility", "VISIBLE");
		return table;
	}

	private List<String> diff(Table before, Table after) {
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		var factory = registry.getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
	}

	@Test
	void tableVisibilityDiffUsesAlterAndPreservesQualifiedQuotedName() throws Exception {
		Table before = table(), after = before.clone();
		after.getIndexes().get(0).getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		assertEquals(List.of("ALTER INDEX \"Mixed Owner\".\"Mixed Index\" INVISIBLE"), diff(before, restored));
		assertEquals(List.of("ALTER INDEX \"Mixed Owner\".\"Mixed Index\" VISIBLE"), diff(after, before));
	}

	@Test
	void unusableAndRecoveryUseSeparateStateStatements() {
		Table before = table(), after = before.clone();
		after.getIndexes().get(0).getSpecifics().put("oracle.index.status", "UNUSABLE");
		after.getIndexes().get(0).getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		assertEquals(List.of("ALTER INDEX \"Mixed Owner\".\"Mixed Index\" UNUSABLE",
				"ALTER INDEX \"Mixed Owner\".\"Mixed Index\" INVISIBLE"), diff(before, after));
		assertEquals(List.of("ALTER INDEX \"Mixed Owner\".\"Mixed Index\" REBUILD",
				"ALTER INDEX \"Mixed Owner\".\"Mixed Index\" VISIBLE"), diff(after, before));
	}

	@Test
	void directRebuildIsRegisteredForOldAndNewDialects() {
		Index index = table().getIndexes().get(0);
		for (var dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle11gDialect,
				DialectHolder.oracle12cDialect, DialectHolder.oracle26aiDialect)) {
			var operations = dialect.createSqlFactoryRegistry().createSql(index, SqlType.REBUILD);
			assertEquals(1, operations.size());
			assertTrue(operations.get(0).getSqlText().endsWith("REBUILD"));
		}
	}

	@Test
	void structuralAndStateChangesCannotBeCombined() {
		Table before = table(), after = before.clone();
		Index index = after.getIndexes().get(0);
		index.getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		index.setUnique(true);
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		index.setUnique(false);
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void invalidOrRemovedStatesAndOldVisibilityFail() {
		Table before = table(), after = before.clone();
		Index index = after.getIndexes().get(0);
		index.getSpecifics().remove("oracle.index.visibility");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		index.getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		var factory = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.ALTER);
		assertThrows(IllegalArgumentException.class,
				() -> factory.createDiffSql(before.getIndexes().get(0).diff(index)));
		index.getSpecifics().put("oracle.index.status", "BROKEN");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void unsupportedRebuildTargetsFail() {
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		Index partitioned = table().getIndexes().get(0);
		partitioned.setPartitioning(new Partitioning());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(partitioned, SqlType.REBUILD));
		Table temporary = table().setTableType(Table.TableType.Temporary);
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(temporary.getIndexes().get(0), SqlType.REBUILD));
		Index vector = table().getIndexes().get(0).setIndexType(IndexType.Vector);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(vector, SqlType.REBUILD));
	}

	@Test
	void partitionVisibilityCanChangeButWholePartitionedStatusCannot() {
		Table before = table();
		before.getIndexes().get(0).setPartitioning(new Partitioning());
		Table after = before.clone();
		after.getIndexes().get(0).getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		assertEquals(List.of("ALTER INDEX \"Mixed Owner\".\"Mixed Index\" INVISIBLE"), diff(before, after));
		after.getIndexes().get(0).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void unchangedIndexProducesNoSqlAndDirectAlterRequiresADiff() {
		Index before = table().getIndexes().get(0), after = before.clone();
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertTrue(factory.createDiffSql(before.diff(after)).isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(after, SqlType.ALTER));
	}
}
