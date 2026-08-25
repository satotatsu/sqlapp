/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

class PostgresBulkDataReaderTest {
	@Test
	void streamsCsvAndEscapesTextNullEmptyAndBinary() throws Exception {
		final Table table = createTable();
		try (PostgresBulkDataReader reader = new PostgresBulkDataReader(table,
				BulkOption.defaults())) {
			assertEquals(4, reader.getColumns().size());
			assertEquals("name", reader.getColumns().get(0).getName());
			final StringWriter writer = new StringWriter();
			reader.transferTo(writer);
			assertEquals("\"a,\"\"b\"\"\nline\",,\"\",\"\\x00ff\"\n",
					writer.toString());
			assertEquals(1, reader.getRowCount());
		}
	}

	@Test
	void includesIdentityOnlyWhenRequested() throws Exception {
		final Table table = createTable();
		try (PostgresBulkDataReader omitted = new PostgresBulkDataReader(table,
				BulkOption.defaults());
				PostgresBulkDataReader included = new PostgresBulkDataReader(table,
						BulkOption.builder().keepIdentity(true).build())) {
			assertFalse(omitted.getColumns().stream()
					.anyMatch(column -> "id".equals(column.getName())));
			assertTrue(included.getColumns().stream()
					.anyMatch(column -> "id".equals(column.getName())));
		}
	}

	@Test
	void resolvesProviderAndRejectsUnsupportedVendorOptions() {
		assertTrue(BulkInsertResolver.resolve(DialectHolder.postgreSQL180)
				instanceof PostgresBulkInsertExecutor);
		final PostgresBulkInsertExecutor executor = new PostgresBulkInsertExecutor(
				DialectHolder.postgreSQL180);
		assertThrows(NullPointerException.class,
				() -> executor.execute(null, createTable(), BulkOption.defaults()));
		final Connection connection = (Connection) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> null);
		assertThrows(IllegalArgumentException.class,
				() -> executor.execute(connection, createTable(),
						BulkOption.builder().tableLock(true).build()));
	}

	private Table createTable() {
		final Table table = new Table("copy_target");
		table.setDialect(DialectHolder.postgreSQL180);
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
				.setIdentity(true));
		table.getColumns().add(new Column("name").setDataType(DataType.NVARCHAR));
		table.getColumns().add(new Column("nullable").setDataType(DataType.NVARCHAR));
		table.getColumns().add(new Column("empty_value").setDataType(DataType.NVARCHAR));
		table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
		table.getColumns().add(new Column("calculated").setDataType(DataType.INT)
				.setFormula("id + 1"));
		table.getRows().add(row -> {
			row.put("id", 10L);
			row.put("name", "a,\"b\"\nline");
			row.put("nullable", null);
			row.put("empty_value", "");
			row.put("payload", new byte[] { 0, (byte) 0xff });
			row.put("calculated", 11);
		});
		return table;
	}
}
