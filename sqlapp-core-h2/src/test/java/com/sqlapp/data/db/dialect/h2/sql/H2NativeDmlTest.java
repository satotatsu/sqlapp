/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class H2NativeDmlTest {
	static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	static Table read(Connection connection, String schema, String name) {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
				.getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	static int write(Connection connection, SqlFactoryRegistry registry, Table table, SqlType type, Object data)
			throws Exception {
		try (var parameters = registry.createSqlNodes(table, type).get(0).eval(data);
				var statement = parameters.createStatement(connection)) {
			parameters.setBind(statement);
			return statement.executeUpdate();
		}
	}

	static Stream<Arguments> cases() {
		return Stream.of("PUBLIC", "odd\" schema").flatMap(schema -> Stream
				.of(SqlType.INSERT, SqlType.INSERT_ROWS, SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)
				.flatMap(type -> Stream.of("primary", "composite", "unique").flatMap(
						key -> Stream.of(false, true).map(rollback -> Arguments.of(schema, type, key, rollback)))));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void executesBoundWritesWithGeneratedColumnsAndSchemaIsolation(String schema, SqlType type, String key,
			boolean rollback) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			if (!schema.equals("PUBLIC")) {
				statement.execute("CREATE SCHEMA " + quote(schema));
				statement.execute("CREATE TABLE PUBLIC.T(ID INT PRIMARY KEY,K VARCHAR(12),\"VALUE\" VARCHAR(40))");
				statement.execute("INSERT INTO PUBLIC.T VALUES(1,'k1','untouched')");
			}
			String prefix = quote(schema) + ".";
			String constraint = switch (key) {
			case "composite" -> "PRIMARY KEY(ID,K)";
			case "unique" -> "UNIQUE(K)";
			default -> "PRIMARY KEY(ID)";
			};
			statement.execute("CREATE TABLE " + prefix
					+ "T(ID INT,K VARCHAR(12) NOT NULL,\"VALUE\" VARCHAR(40),G INT GENERATED ALWAYS AS(LENGTH(\"VALUE\")),"
					+ constraint + ")");
			statement.execute("INSERT INTO " + prefix + "T(ID,K,\"VALUE\") VALUES(1,'k1','old')");
			var table = read(connection, schema, "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			boolean insert = type == SqlType.INSERT || type == SqlType.INSERT_ROWS;
			var first = table.getRows().newElement();
			first.put("ID", insert ? 2 : 1);
			first.put("K", insert ? "k2" : "k1");
			first.put("VALUE", "changed");
			var second = table.getRows().newElement();
			second.put("ID", 3);
			second.put("K", "k3");
			second.put("VALUE", "new");
			if (type == SqlType.MERGE_TABLE) {
				String stage = registry.getTableOptions().getTempTableName().apply(table);
				var staging = table.clone();
				staging.setName(stage);
				staging.getConstraints().clear();
				staging.getIndexes().clear();
				for (var operation : registry.createSql(staging, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO " + prefix + quote(stage)
						+ "(ID,K,\"VALUE\") VALUES(1,'k1','changed'),(3,'k3','new')");
			}
			connection.setAutoCommit(false);
			if (type == SqlType.INSERT || type == SqlType.MERGE) {
				assertEquals(1, write(connection, registry, table, type, first));
				assertEquals(1, write(connection, registry, table, type, second));
			} else if (type == SqlType.MERGE_TABLE) {
				assertEquals(2, statement.executeUpdate(registry.createSql(table, type).get(0).getSqlText()));
			} else {
				table.getRows().add(first);
				table.getRows().add(second);
				assertEquals(2, write(connection, registry, table, type, table));
			}
			try (var rows = statement.executeQuery("SELECT count(*),sum(G) FROM " + prefix + "T")) {
				assertTrue(rows.next());
				assertEquals(insert ? 3 : 2, rows.getInt(1));
				assertEquals(insert ? 13 : 10, rows.getInt(2));
			}
			assertFalse(connection.getAutoCommit());
			if (rollback)
				connection.rollback();
			else
				connection.commit();
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "T")) {
				assertTrue(rows.next());
				assertEquals(rollback ? 1 : insert ? 3 : 2, rows.getInt(1));
			}
			if (!schema.equals("PUBLIC"))
				try (var rows = statement.executeQuery("SELECT \"VALUE\" FROM PUBLIC.T")) {
					assertTrue(rows.next());
					assertEquals("untouched", rows.getString(1));
				}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "ALWAYS", "BY DEFAULT" })
	void insertsGeneratedOrExplicitIdentityValuesWithoutWritingGeneratedColumns(String generation) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID BIGINT GENERATED " + generation
					+ " AS IDENTITY,G BIGINT GENERATED ALWAYS AS(ID*2))");
			var table = read(connection, "PUBLIC", "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			assertEquals(1, write(connection, registry, table, SqlType.INSERT, table.getRows().newElement()));
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.INSERT_ROWS));
			var explicit = table.getRows().newElement();
			explicit.put("ID", 40);
			table.getRows().add(explicit);
			if (generation.equals("ALWAYS"))
				assertThrows(IllegalArgumentException.class,
						() -> write(connection, registry, table, SqlType.INSERT, explicit));
			else {
				assertEquals(1, write(connection, registry, table, SqlType.INSERT, explicit));
			}
			try (var rows = statement.executeQuery("SELECT max(G) FROM T")) {
				assertTrue(rows.next());
				assertEquals(generation.equals("ALWAYS") ? 2 : 80, rows.getLong(1));
			}
		}
	}

	@Test
	void locksOnlyTheSelectedTableUntilCallerRollback() throws Exception {
		String url = "jdbc:h2:mem:lock_" + java.util.UUID.randomUUID();
		try (var connection = DriverManager.getConnection(url);
				var other = DriverManager.getConnection(url);
				var statement = connection.createStatement();
				var contender = other.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY)");
			statement.execute("INSERT INTO T VALUES(1)");
			contender.execute("SET LOCK_TIMEOUT 100");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			connection.setAutoCommit(false);
			String sql = registry.createSql(read(connection, "PUBLIC", "T"), SqlType.LOCK).get(0).getSqlText();
			try (var rows = statement.executeQuery(sql)) {
				assertTrue(rows.next());
			}
			assertThrows(SQLException.class, () -> contender.executeUpdate("UPDATE T SET ID=2 WHERE ID=1"));
			connection.rollback();
			assertEquals(1, contender.executeUpdate("UPDATE T SET ID=2 WHERE ID=1"));
		}
	}

	@Test
	void rejectsKeylessAndUnsupportedMergePoliciesBeforeExecution() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT NOT NULL)");
			var table = read(connection, "PUBLIC", "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			for (var type : List.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE))
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, type));
			statement.execute("ALTER TABLE T ADD PRIMARY KEY(ID)");
			var keyed = read(connection, "PUBLIC", "T");
			registry.getTableOptions().setMergeTableWithDelete(true);
			for (var type : List.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE))
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(keyed, type));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "MERGE", "MERGE_ROWS", "MERGE_TABLE" })
	void mergesKeyOnlyRowsWithoutAnEmptyUpdateClause(String operation) throws Exception {
		SqlType type = SqlType.valueOf(operation);
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY)");
			statement.execute("INSERT INTO T VALUES(1)");
			var table = read(connection, "PUBLIC", "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			for (int id : new int[] { 1, 2 }) {
				var row = table.getRows().newElement();
				row.put("ID", id);
				table.getRows().add(row);
			}
			if (type == SqlType.MERGE) {
				assertEquals(0, write(connection, registry, table, type, table.getRows().get(0)));
				assertEquals(1, write(connection, registry, table, type, table.getRows().get(1)));
			} else if (type == SqlType.MERGE_ROWS)
				assertEquals(1, write(connection, registry, table, type, table));
			else {
				String stage = registry.getTableOptions().getTempTableName().apply(table);
				statement.execute("CREATE TABLE " + quote(stage) + "(ID INT)");
				statement.execute("INSERT INTO " + quote(stage) + " VALUES(1),(2)");
				assertEquals(1, statement.executeUpdate(registry.createSql(table, type).get(0).getSqlText()));
			}
			try (var rows = statement.executeQuery("SELECT count(*) FROM T")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
	}

	static Stream<Arguments> identities() {
		return Stream.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE)
				.flatMap(type -> Stream.of("ALWAYS", "BY DEFAULT").map(generation -> Arguments.of(type, generation)));
	}

	static Stream<Arguments> oversizedValues() {
		return Stream.of(SqlType.MERGE, SqlType.MERGE_ROWS)
				.flatMap(type -> Stream.of("VARCHAR(3)", "VARBINARY(3)").map(dataType -> Arguments.of(type, dataType)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "CHAR(4)", "VARCHAR_IGNORECASE(4)", "BINARY(4)" })
	void matchesFixedLengthAndCaseInsensitiveKeys(String keyType) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			boolean binary = keyType.startsWith("BINARY");
			statement.execute("CREATE TABLE T(K " + keyType + " PRIMARY KEY,V INT)");
			statement.execute("INSERT INTO T VALUES(" + (binary ? "X'01'" : "'a'") + ",1)");
			var table = read(connection, "PUBLIC", "T");
			var row = table.getRows().newElement();
			row.put("K", binary ? new byte[] { 1 } : keyType.startsWith("VARCHAR_IGNORECASE") ? "A" : "a");
			row.put("V", 2);
			table.getRows().add(row);
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			assertEquals(1, write(connection, registry, table, SqlType.MERGE, row));
			try (var rows = statement.executeQuery("SELECT count(*),max(V) FROM T")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
				assertEquals(2, rows.getInt(2));
			}
		}
	}

	@ParameterizedTest
	@MethodSource("oversizedValues")
	void rejectsOversizedMergeValuesWithoutSilentlyTruncatingThem(SqlType type, String dataType) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY,V " + dataType + ")");
			var table = read(connection, "PUBLIC", "T");
			var row = table.getRows().newElement();
			row.put("ID", 1);
			row.put("V", dataType.startsWith("VARCHAR") ? "oversized" : new byte[] { 1, 2, 3, 4 });
			table.getRows().add(row);
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			assertThrows(SQLException.class,
					() -> write(connection, registry, table, type, type == SqlType.MERGE_ROWS ? table : row));
			try (var rows = statement.executeQuery("SELECT count(*) FROM T")) {
				assertTrue(rows.next());
				assertEquals(0, rows.getInt(1));
			}
			statement.execute(
					"INSERT INTO T VALUES(1," + (dataType.startsWith("VARCHAR") ? "'old'" : "X'010203'") + ")");
			assertThrows(SQLException.class,
					() -> write(connection, registry, table, type, type == SqlType.MERGE_ROWS ? table : row));
			try (var rows = statement.executeQuery("SELECT V FROM T")) {
				assertTrue(rows.next());
				if (dataType.startsWith("VARCHAR"))
					assertEquals("old", rows.getString(1));
				else
					assertArrayEquals(new byte[] { 1, 2, 3 }, rows.getBytes(1));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "D", "INTEGER ARRAY[3]", "ROW(X VARCHAR(3))" })
	void usesTypedStagingForValuesWhoseCompositeCastsCanTruncate(String dataType) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE DOMAIN D AS VARCHAR(3)");
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY,C " + dataType + ")");
			var table = read(connection, "PUBLIC", "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			for (var type : List.of(SqlType.MERGE, SqlType.MERGE_ROWS))
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, type));
			var staging = table.clone();
			staging.setName(registry.getTableOptions().getTempTableName().apply(table));
			staging.getConstraints().clear();
			staging.getIndexes().clear();
			for (var operation : registry.createSql(staging, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			String value = dataType.equals("D") ? "'ok'" : dataType.startsWith("ROW") ? "ROW('ok')" : "ARRAY[1,2]";
			statement.execute("INSERT INTO " + quote(staging.getName()) + " VALUES(1," + value + ")");
			assertEquals(1,
					statement.executeUpdate(registry.createSql(table, SqlType.MERGE_TABLE).get(0).getSqlText()));
			try (var rows = statement.executeQuery("SELECT count(*) FROM T")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	@ParameterizedTest
	@MethodSource("identities")
	void mergesNaturalKeysWithoutReplacingExistingIdentityValues(SqlType type, String generation) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID BIGINT GENERATED " + generation
					+ " AS IDENTITY PRIMARY KEY,K VARCHAR(12) NOT NULL UNIQUE,V VARCHAR(40))");
			statement.execute("INSERT INTO T(K,V) VALUES('a','old')");
			var table = read(connection, "PUBLIC", "T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			registry.getTableOptions().setMergeKeyColumnsMatchingStrategy(
					item -> com.sqlapp.data.db.sql.ColumnSelectionStrategy.UNIQUE_KEY);
			for (String key : new String[] { "a", "b" }) {
				var row = table.getRows().newElement();
				row.put("K", key);
				row.put("V", "changed");
				if (generation.equals("BY DEFAULT"))
					row.put("ID", key.equals("a") ? 101 : 102);
				table.getRows().add(row);
			}
			if (type == SqlType.MERGE) {
				for (var row : table.getRows())
					assertEquals(1, write(connection, registry, table, type, row));
			} else if (type == SqlType.MERGE_ROWS)
				assertEquals(2, write(connection, registry, table, type, table));
			else {
				String stage = registry.getTableOptions().getTempTableName().apply(table);
				statement.execute("CREATE TABLE " + quote(stage) + "(ID BIGINT,K VARCHAR(12),V VARCHAR(40))");
				statement.execute("INSERT INTO " + quote(stage) + " VALUES(101,'a','changed'),(102,'b','changed')");
				assertEquals(2, statement.executeUpdate(registry.createSql(table, type).get(0).getSqlText()));
			}
			try (var rows = statement.executeQuery("SELECT ID,V FROM T WHERE K='a'")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getLong(1));
				assertEquals("changed", rows.getString(2));
			}
			try (var rows = statement.executeQuery("SELECT ID FROM T WHERE K='b'")) {
				assertTrue(rows.next());
				assertEquals(generation.equals("ALWAYS") ? 2 : 102, rows.getLong(1));
			}
		}
	}
}
