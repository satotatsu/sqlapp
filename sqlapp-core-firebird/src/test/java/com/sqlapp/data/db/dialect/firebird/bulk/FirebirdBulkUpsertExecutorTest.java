/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;

class FirebirdBulkUpsertExecutorTest {

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static final class Driver {
		int major = 5, minor = 0;
		boolean autoCommit = true;
		final List<String> sql = new ArrayList<>();
		final List<List<Object>> rows = new ArrayList<>();
		List<Object> current = new ArrayList<>();

		Connection connection() {
			return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
			case "getMetaData" -> proxy(DatabaseMetaData.class, (mp, mm, ma) -> switch (mm.getName()) {
			case "getDatabaseMajorVersion" -> major;
			case "getDatabaseMinorVersion" -> minor;
			default -> throw new AssertionError(mm.getName());
			});
			case "getAutoCommit" -> autoCommit;
			case "setAutoCommit" -> {
				autoCommit = (boolean) a[0];
				yield null;
			}
			case "commit", "rollback" -> null;
			case "prepareStatement" -> statement((String) a[0]);
			default -> throw new AssertionError(m.getName());
			});
		}

		PreparedStatement statement(final String text) {
			sql.add(text);
			return proxy(PreparedStatement.class, (p, m, a) -> switch (m.getName()) {
			case "getConnection" -> connection();
			case "setObject", "setInt", "setString" -> {
				current.add(a[1]);
				yield null;
			}
			case "setNull" -> {
				current.add(null);
				yield null;
			}
			case "addBatch" -> {
				rows.add(current);
				current = new ArrayList<>();
				yield null;
			}
			case "executeBatch" -> {
				final int[] counts = new int[rows.size()];
				java.util.Arrays.fill(counts, 1);
				yield counts;
			}
			case "close" -> null;
			default -> throw new AssertionError(m.getName());
			});
		}
	}

	private static Table table() {
		final Table table = new Table("T");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getColumns().add("A", c -> c.setDataType(DataType.VARCHAR).setLength(10));
		table.getColumns().add("B", c -> c.setDataType(DataType.VARCHAR).setLength(10));
		final var row = table.newRow();
		row.put("ID", 1);
		row.put("A", "a");
		row.put("B", "b");
		table.getRows().add(row);
		return table;
	}

	private static String q(final Dialect dialect, final String name) {
		return dialect.quote(name);
	}

	@Test
	void upsertUpdatesOnlyUpdateColumnsAndKeepsOthers() throws Exception {
		final Dialect dialect = DialectHolder.defaultDialect50;
		final Driver driver = new Driver();
		final var option = BulkUpsertOption.builder().keyColumn("ID").updateColumn("A").build();
		assertEquals(1, new FirebirdBulkUpsertExecutor(dialect).execute(driver.connection(), table(), option));
		final String id = q(dialect, "ID"), a = q(dialect, "A"), b = q(dialect, "B");
		assertEquals("MERGE INTO " + q(dialect, "T") + " TGT USING RDB$DATABASE ON (TGT." + id + " = ?)"
				+ " WHEN MATCHED THEN UPDATE SET " + a + " = ?" + " WHEN NOT MATCHED THEN INSERT (" + id + ", " + a
				+ ", " + b + ") VALUES (?, ?, ?)", driver.sql.get(0));
		assertFalse(driver.sql.get(0).contains(b + " = ?"));
		// key, update columns, then insert columns
		assertEquals(List.of(1, "a", 1, "a", "b"), driver.rows.get(0));
	}

	@Test
	void insertOnlyOmitsMatchedClause() throws Exception {
		final Dialect dialect = DialectHolder.defaultDialect30;
		final Driver driver = new Driver();
		final var option = BulkUpsertOption.builder().keyColumn("ID").updateWhenMatched(false).build();
		new FirebirdBulkUpsertExecutor(dialect).execute(driver.connection(), table(), option);
		assertFalse(driver.sql.get(0).contains("WHEN MATCHED"));
		assertFalse(driver.sql.get(0).contains("SELECT"));
		assertEquals(List.of(1, 1, "a", "b"), driver.rows.get(0));
	}

	@Test
	void updateOnlyUsesPlainUpdate() throws Exception {
		final Dialect dialect = DialectHolder.defaultDialect20;
		final Driver driver = new Driver();
		driver.major = 2;
		driver.minor = 0;
		final var option = BulkUpsertOption.builder().keyColumn("ID").updateColumn("B").insertWhenNotMatched(false)
				.build();
		new FirebirdBulkUpsertExecutor(dialect).execute(driver.connection(), table(), option);
		assertEquals(
				"UPDATE " + q(dialect, "T") + " SET " + q(dialect, "B") + " = ? WHERE " + q(dialect, "ID") + " = ?",
				driver.sql.get(0));
		assertEquals(List.of("b", 1), driver.rows.get(0));
	}

	@Test
	void mergeRequiresFirebird21() throws Exception {
		final var option = BulkUpsertOption.builder().keyColumn("ID").build();
		final Driver legacy = new Driver();
		legacy.major = 2;
		legacy.minor = 0;
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> new FirebirdBulkUpsertExecutor(DialectHolder.defaultDialect20).execute(legacy.connection(),
						table(), option));
		assertEquals(List.of(), legacy.sql);
		final Driver modern = new Driver();
		modern.major = 2;
		modern.minor = 1;
		new FirebirdBulkUpsertExecutor(DialectHolder.defaultDialect20).execute(modern.connection(), table(), option);
		assertEquals(1, modern.sql.size());
		assertThrows(SQLFeatureNotSupportedException.class,
				() -> new FirebirdBulkUpsertExecutor(DialectHolder.defaultDialect).execute(new Driver().connection(),
						table(), option));
	}
}
