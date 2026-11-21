/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixDmlSafetyTest {
	@Test
	void jdbcBatchBindsQualifiedColumnsInOrderAndLeavesConnectionOpen() throws Exception {
		final var table = new Table("Events");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		final var payload = new Column("Value Text").setDataType(DataType.VARCHAR);
		payload.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "Case Family");
		table.getColumns().add(payload);
		for (int id = 1; id <= 3; id++) {
			final var row = table.newRow();
			row.put("ID", id);
			row.put(payload.getName(), id == 2 ? null : "value" + id);
			table.getRows().add(row);
		}
		final var values = new java.util.TreeMap<Integer, Object>();
		final var batches = new java.util.ArrayList<List<Object>>();
		final var batchSizes = new java.util.ArrayList<Integer>();
		final var closed = new java.util.concurrent.atomic.AtomicBoolean();
		final var pending = new java.util.concurrent.atomic.AtomicInteger();
		final var owner = new java.util.concurrent.atomic.AtomicReference<java.sql.Connection>();
		final var statement = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { java.sql.PreparedStatement.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "getConnection" -> {
						return owner.get();
					}
					case "setInt", "setString", "setObject", "setNull" ->
						values.put((Integer) args[0], method.getName().equals("setNull") ? null : args[1]);
					case "addBatch" -> {
						assertEquals(java.util.Set.of(1, 2), values.keySet());
						batches.add(new java.util.ArrayList<>(values.values()));
						values.clear();
						pending.incrementAndGet();
					}
					case "executeBatch" -> {
						final int size = pending.getAndSet(0);
						batchSizes.add(size);
						final int[] counts = new int[size];
						java.util.Arrays.fill(counts, 1);
						return counts;
					}
					case "close" -> closed.set(true);
					default -> throw new AssertionError(method.getName());
					}
					return null;
				});
		final var connection = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { java.sql.Connection.class }, (proxy, method, args) -> {
					assertEquals("prepareStatement", method.getName());
					final String sql = (String) args[0];
					assertTrue(sql.startsWith("UPSERT INTO"), sql);
					assertTrue(sql.contains("\"Case Family\".\"Value Text\""), sql);
					return statement;
				});
		owner.set(connection);
		final var executor = com.sqlapp.jdbc.bulk.BulkInsertResolver.resolve(DialectHolder.defaultDialect5_3_1,
				com.sqlapp.jdbc.bulk.BulkInsertMethod.JDBC_BATCH);
		final var options = com.sqlapp.jdbc.bulk.BulkOption.builder().batchSize(2).build();
		assertEquals(3, executor.execute(connection, table, options));
		assertEquals(List.of(List.of(1, "value1"), java.util.Arrays.asList(2, null), List.of(3, "value3")), batches);
		assertEquals(List.of(2, 1), batchSizes);
		assertTrue(closed.get());
	}

	@Test
	void rowCollectionDiffRejectsUpdatesAndKeepsDeleteBeforeInsert() {
		final var original = new Table("Events");
		final var id = new Column("ID").setDataType(DataType.INT);
		original.getColumns().add(id);
		original.getColumns().add(new Column("PAYLOAD").setDataType(DataType.VARCHAR));
		original.getConstraints().addPrimaryKeyConstraint("pk", id);
		final var retained = original.newRow();
		retained.put("ID", 1);
		retained.put("PAYLOAD", "original");
		original.getRows().add(retained);
		final var deleted = original.newRow();
		deleted.put("ID", 2);
		original.getRows().add(deleted);
		final var target = original.clone();
		target.getRows().remove(1);
		final var added = target.newRow();
		added.put("ID", 3);
		target.getRows().add(added);
		final var registry = DialectHolder.defaultDialect5_3_1.createSqlFactoryRegistry();
		assertEquals(List.of(SqlType.DELETE, SqlType.INSERT),
				registry.createSql(original.getRows().diff(target.getRows())).stream()
						.map(operation -> operation.getSqlType()).toList());
		target.getRows().get(0).put("PAYLOAD", "changed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.getRows().diff(target.getRows())));
	}

	@Test
	void deletesRowsOnlyUsingCompletePrimaryKeys() {
		final var table = new Table("Events");
		final var id = new Column("ID").setDataType(DataType.INT);
		final var code = new Column("Key Code").setDataType(DataType.VARCHAR);
		table.getColumns().add(id);
		table.getColumns().add(code);
		final var row = table.newRow();
		row.put(id.getName(), 7);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var difference = new DbObjectDifference(row, null, new EqualsHandler());
		final var addition = difference.reverse();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(difference));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(addition));
		table.getConstraints().addPrimaryKeyConstraint("pk", id, code);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(difference));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(addition));
		row.put(code.getName(), "O'Brien");
		final var operations = registry.createSql(difference);
		assertEquals(1, operations.size());
		assertEquals(1, registry.createSql(addition).size());
		assertTrue(
				operations.get(0).getSqlText()
						.matches("(?s)DELETE FROM .* WHERE ID\\s*=\\s*7 AND \"Key Code\"\\s*=\\s*'O''Brien'"),
				operations.get(0).getSqlText());
	}

	@Test
	void qualifiesUpsertTargetsInJdbcBatchAndRowSql() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var table = new Table("events").setSchemaName("Sales");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			final var value = new Column("Value Text").setDataType(DataType.VARCHAR);
			value.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "Case Family");
			table.getColumns().add(value);
			final var batch = new com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor(dialect) {
				String sql() {
					return createInsertSql(table, List.copyOf(table.getColumns()));
				}
			};
			final String sql = batch.sql();
			assertTrue(sql.startsWith("UPSERT INTO"), sql);
			assertTrue(sql.contains("\"Case Family\".\"Value Text\""), sql);
			assertEquals(2, sql.chars().filter(ch -> ch == '?').count(), sql);
			final var row = table.newRow();
			row.put("ID", 1);
			row.put("Value Text", "O'Brien");
			table.getRows().add(row);
			final var registry = dialect.createSqlFactoryRegistry();
			table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().get("ID"));
			final var addition = new DbObjectDifference(null, row, new EqualsHandler());
			final var rowOperations = registry.createSql(addition);
			assertEquals(1, rowOperations.size());
			assertSame(row, rowOperations.get(0).getTarget());
			assertTrue(rowOperations.get(0).getSqlText().contains("\"Case Family\".\"Value Text\""));
			final String rowSql = registry.createSqlNodes(row, SqlType.INSERT).get(0).eval(row).getSql();
			assertTrue(rowSql.matches("(?s).*VALUES\\s*\\(\\s*1\\s*,\\s*'O''Brien'\\s*\\).*"), rowSql);
			assertTrue(registry.createSql(table, SqlType.INSERT_ROWS).get(0).getSqlText()
					.contains("\"Case Family\".\"Value Text\""));
			final String conditional = registry.createSql(table, SqlType.INSERT_SELECT_NOT_EXISTS).get(0).getSqlText();
			assertTrue(conditional.startsWith("UPSERT INTO"), conditional);
			assertTrue(conditional.contains("\"Case Family\".\"Value Text\""), conditional);
			assertTrue(conditional.replaceAll("\\s+", " ").contains("WHERE NOT EXISTS"), conditional);
		}
	}

	@Test
	void rejectsUpdateAndMergeAcrossDirectAndDiffEntryPoints() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_8,
				DialectHolder.defaultDialect4_9, DialectHolder.defaultDialect5_3, DialectHolder.defaultDialect5_3_1)) {
			for (boolean keyOnly : new boolean[] { false, true }) {
				final var table = new Table("Order Details").setSchemaName("Sales");
				table.getColumns().add(new Column("ID").setDataType(DataType.INT));
				if (!keyOnly)
					table.getColumns().add(new Column("V").setDataType(DataType.VARCHAR));
				final var pk = new UniqueConstraint("PK").setPrimaryKey(true);
				pk.getColumns().add("ID");
				table.getConstraints().add(pk);
				final var registry = dialect.createSqlFactoryRegistry();
				for (var type : List.of(SqlType.UPDATE, SqlType.UPDATE_TABLE, SqlType.UPDATE_FOR_APP, SqlType.MERGE,
						SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)) {
					assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table, type),
							type.toString());
					assertThrows(UnsupportedOperationException.class,
							() -> registry.createSql(table.diff(table.clone()), type), type.toString());
				}
				assertTrue(registry.createSql(table, SqlType.INSERT).get(0).getSqlText().startsWith("UPSERT INTO"));
				assertTrue(registry.createSql(table, SqlType.DELETE).get(0).getSqlText().startsWith("DELETE FROM"));
			}
		}
	}
}
