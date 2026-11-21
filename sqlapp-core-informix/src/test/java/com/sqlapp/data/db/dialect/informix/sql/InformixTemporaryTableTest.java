/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class InformixTemporaryTableTest {
	@Test
	void createsAndTruncatesTheSameSessionTableAndPreservesSource() {
		final var table = new Table("items").setSchemaName("owner");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey("pk", table.getColumns().get(0));
		table.getIndexes().add("ix", table.getColumns().get(0));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		final var operations = registry.createSql(table, SqlType.CREATE_TEMPORARY);
		assertTrue(operations.get(0).getSqlText().startsWith("CREATE TEMP TABLE \"tmp_items\""), operations.toString());
		assertTrue(operations.get(0).getSqlText().contains("PRIMARY KEY"));
		assertTrue(operations.get(1).getSqlText().contains("ON \"tmp_items\""), operations.toString());
		assertEquals("TRUNCATE TABLE \"tmp_items\"",
				registry.createSql(table, SqlType.TRUNCATE_TEMPORARY).get(0).getSqlText());
		assertEquals("items", table.getName());
		assertEquals("owner", table.getSchemaName());
	}

	@Test
	void rejectsStorageOptionsInsteadOfIgnoringThem() {
		final var table = new Table("items").setTableSpaceName("rootdbs");
		assertThrows(UnsupportedOperationException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE_TEMPORARY));
	}

	@Test
	void exposesNativeSingleSequenceValueSql() {
		final var dialect = DialectHolder.defaultDialect;
		assertTrue(dialect.supportsSequence());
		assertEquals("SELECT \"owner\".\"seq\".NEXTVAL FROM TABLE(SET{1})",
				dialect.getSequenceNextValString("\"owner\".\"seq\""));
		assertThrows(IllegalArgumentException.class, () -> dialect.getSequenceNextValString(" "));
	}
}
