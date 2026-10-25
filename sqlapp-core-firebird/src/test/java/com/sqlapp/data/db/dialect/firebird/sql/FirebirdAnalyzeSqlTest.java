/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class FirebirdAnalyzeSqlTest {
	@Test
	void indexStatisticsUseQuotingAcrossVersions() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect20,
				DialectHolder.defaultDialect25, DialectHolder.defaultDialect30, DialectHolder.defaultDialect40,
				DialectHolder.defaultDialect50)) {
			var sql = dialect.createSqlFactoryRegistry().createSql(new Index("quoted \" index"), SqlType.ANALYZE);
			assertEquals(1, sql.size());
			assertEquals("SET STATISTICS INDEX \"quoted \"\" index\"", sql.get(0).getSqlText());
		}
	}

	@Test
	void rejectsInactiveMissingAndDiffIndexTargets() {
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		Index index = new Index("inactive").setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.ANALYZE));
		assertFalse(index.isEnable());
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index(" "), SqlType.ANALYZE));
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(index.diff(index.clone().setEnable(true)), SqlType.ANALYZE));
	}

	@Test
	void tableComposesActiveIndexesAndConstraintIndexesExactlyOnce() {
		Table table = new Table("table");
		table.getIndexes().add(new Index("same index"));
		table.getIndexes().add(new Index("inactive").setEnable(false));
		table.getIndexes().add(new Index("IX"));
		table.getIndexes().add(new Index("ix"));
		var unique = new UniqueConstraint("unique constraint", false);
		unique.getSpecifics().put("firebird.indexName", "same index");
		table.getConstraints().add(unique);
		var foreign = new ForeignKeyConstraint("foreign constraint");
		foreign.getSpecifics().put("firebird.catalog.indexName", "RDB$FOREIGN1");
		table.getConstraints().add(foreign);
		var sql = DialectHolder.defaultDialect25.createSqlFactoryRegistry().createSql(table, SqlType.ANALYZE);
		assertEquals(
				java.util.List.of("SET STATISTICS INDEX \"same index\"", "SET STATISTICS INDEX IX",
						"SET STATISTICS INDEX \"ix\"", "SET STATISTICS INDEX RDB$FOREIGN1"),
				sql.stream().map(operation -> operation.getSqlText()).toList());
		assertFalse(table.getIndexes().get("inactive").isEnable());
	}

	@Test
	void tableRejectsUnknownBackingIndexNamesRatherThanGuessing() {
		Table table = new Table("table");
		table.getConstraints().add(new UniqueConstraint("named constraint", true));
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.ANALYZE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Table("only name"), SqlType.ANALYZE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new View("view"), SqlType.ANALYZE));
	}

	@Test
	void allInactiveIndexesProduceNoStatementsAndConflictingConstraintStateFails() {
		Table table = new Table("table");
		table.getIndexes().add(new Index("inactive").setEnable(false));
		var registry = DialectHolder.defaultDialect50.createSqlFactoryRegistry();
		assertTrue(registry.createSql(table, SqlType.ANALYZE).isEmpty());
		var constraint = new UniqueConstraint("constraint", true);
		constraint.getSpecifics().put("firebird.indexName", "inactive");
		table.getConstraints().add(constraint);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.ANALYZE));
	}
}
