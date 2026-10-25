/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.PartitioningType;

class Postgres100PartitionParentTest {
	private Table table() {
		var table = new Table("parent");
		table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		table.getColumns().add("Case Key", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		table.toPartitioning();
		table.getPartitioning().setPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitioningColumns().add("Case Key");
		return table;
	}

	@Test
	void generatesQuotedSharedKeysAndPreservesDeparsedExpressions() {
		var table = table();
		table.setDialect(DialectHolder.postgreSQL100);
		var registry = DialectHolder.postgreSQL100.createSqlFactoryRegistry();
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("PARTITION BY RANGE ( \"Case Key\" )"), sql);
		table.getSpecifics().put("postgres.partitionBy", "RANGE ((abs(id)), \"Case Key\" COLLATE \"C\")");
		assertTrue(registry.createSql(table, SqlType.CREATE).getFirst().getSqlText()
				.contains("PARTITION BY RANGE ((abs(id)), \"Case Key\" COLLATE \"C\")"));
	}

	@Test
	void enforcesTenAndElevenBoundaries() {
		var table = table();
		table.setDialect(DialectHolder.postgreSQL96);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.postgreSQL96.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		table.setDialect(DialectHolder.postgreSQL100);
		table.getPartitioning().setPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.postgreSQL100.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		table.setDialect(DialectHolder.postgreSQL110);
		assertTrue(DialectHolder.postgreSQL110.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst()
				.getSqlText().contains("PARTITION BY HASH"));
	}
}
