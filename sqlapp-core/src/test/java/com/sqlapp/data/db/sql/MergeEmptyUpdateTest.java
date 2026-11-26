/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;

class MergeEmptyUpdateTest extends AbstractStandardFactoryTest {
	public static class MergeTableFactory extends AbstractMergeTableFactory<com.sqlapp.util.AbstractSqlBuilder<?>> {
	}

	public static class MergeRowsFactory extends AbstractMergeRowsFactory<com.sqlapp.util.AbstractSqlBuilder<?>> {
	}

	@Test
	void allKeyTablesOmitMatchedUpdateAcrossSharedMergeFactories() {
		final var table = new Table("keyonly");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey("pk", table.getColumns().get(0));
		sqlFactoryRegistry.registerSqlFactory(Table.class, SqlType.MERGE_TABLE, MergeTableFactory.class);
		sqlFactoryRegistry.registerSqlFactory(Table.class, SqlType.MERGE_ROWS, MergeRowsFactory.class);
		for (var type : new SqlType[] { SqlType.MERGE, SqlType.MERGE_TABLE, SqlType.MERGE_ROWS }) {
			final String sql = sqlFactoryRegistry.createSql(table, type).get(0).getSqlText();
			assertFalse(sql.contains("WHEN MATCHED"), sql);
			assertTrue(sql.contains("WHEN NOT MATCHED"), sql);
		}
	}

	@Test
	void nonUpdateableColumnsDoNotCreateAnEmptyMatchedClause() {
		final var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("value", c -> c.setDataType(DataType.VARCHAR).setLength(20));
		table.setPrimaryKey("pk", table.getColumns().get(0));
		sqlFactoryRegistry.getTableOptions().setUpdateableColumn(c -> false);
		assertFalse(sqlFactoryRegistry.createSql(table, SqlType.MERGE).get(0).getSqlText().contains("WHEN MATCHED"));
	}
}
