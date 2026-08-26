/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class BulkUpsertPlanTest {
	@Test
	void rejectsDuplicateSourceKeysByDefault() {
		final Table table = tableWithDuplicateKeys();
		final var rows = BulkUpsertPlan.resolve(table, BulkUpsertOption.defaults())
				.createStagingTable("stage").getRows().iterator();
		rows.next();
		final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, rows::next);
		assertEquals("Duplicate bulk upsert key at source rows 1 and 2", ex.getMessage());
	}

	@Test
	void canKeepFirstDuplicateSourceKeyWhileStreaming() {
		final Table table = tableWithDuplicateKeys();
		table.getRows().add(row -> { row.put("id", 2); row.put("name", "two"); });
		final var rows = BulkUpsertPlan.resolve(table, BulkUpsertOption.builder()
				.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_FIRST).build())
				.createStagingTable("stage").getRows().iterator();
		assertEquals("first", rows.next().get("name"));
		assertEquals(2, (Integer) rows.next().get("id"));
		assertEquals(false, rows.hasNext());
	}

	private static Table tableWithDuplicateKeys() {
		final Table table = new Table("target");
		final Column id = new Column("id").setDataType(DataType.INT);
		table.getColumns().add(id);
		table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("pk_target", id);
		table.getRows().add(row -> { row.put("id", 1); row.put("name", "first"); });
		table.getRows().add(row -> { row.put("id", 1); row.put("name", "second"); });
		return table;
	}
}
