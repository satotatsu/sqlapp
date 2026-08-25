/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mysql.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

class MySqlBulkDataInputStreamTest {
	@Test
	void streamsDelimitedRowsAndOmitsGeneratedColumns() throws Exception {
		final Table table = createTable();
		try (MySqlBulkDataInputStream input = new MySqlBulkDataInputStream(
				table, BulkOption.defaults())) {
			assertEquals(4, input.getColumns().size());
			assertEquals("name", input.getColumns().get(0).getName());
			assertEquals("山田,\"太郎\"\nline\\\\path\u001f\\N\u001f\u001f00ff\u001e",
					new String(input.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals(1, input.getRowCount());
		}
	}

	@Test
	void includesIdentityOnlyWhenRequestedAndResolvesProvider() throws Exception {
		final Table table = createTable();
		try (MySqlBulkDataInputStream omitted = new MySqlBulkDataInputStream(
				table, BulkOption.defaults());
				MySqlBulkDataInputStream included = new MySqlBulkDataInputStream(
						table, BulkOption.builder().keepIdentity(true).build())) {
			assertFalse(omitted.getColumns().stream()
					.anyMatch(column -> "id".equals(column.getName())));
			assertTrue(included.getColumns().stream()
					.anyMatch(column -> "id".equals(column.getName())));
		}
		assertTrue(BulkInsertResolver.resolve(DialectHolder.mysql840Dialect)
				instanceof MySqlBulkInsertExecutor);
	}

	private Table createTable() {
		final Table table = new Table("load_target");
		table.setDialect(DialectHolder.mysql840Dialect);
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
				.setIdentity(true));
		table.getColumns().add(new Column("name").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("nullable_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("empty_value").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
		table.getColumns().add(new Column("calculated").setDataType(DataType.INT)
				.setFormula("id + 1"));
		table.getRows().add(row -> {
			row.put("id", 10L);
			row.put("name", "山田,\"太郎\"\nline\\path");
			row.put("nullable_value", null);
			row.put("empty_value", "");
			row.put("payload", new byte[] { 0, (byte) 0xff });
			row.put("calculated", 11);
		});
		return table;
	}
}
