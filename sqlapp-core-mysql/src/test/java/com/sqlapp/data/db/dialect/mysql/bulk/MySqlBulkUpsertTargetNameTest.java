package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mysql.cj.jdbc.JdbcStatement;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;

class MySqlBulkUpsertTargetNameTest {
	private boolean autoCommit;
	private boolean failInsert;
	private boolean failUpdate;
	private final List<String> transactions = new ArrayList<>();

	private Connection connection(List<String> sql) {
		java.io.InputStream[] input = { null };
		var statement = (JdbcStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { JdbcStatement.class }, (p, method, args) -> switch (method.getName()) {
				case "unwrap" -> p;
				case "execute" -> {
					sql.add((String) args[0]);
					yield false;
				}
				case "executeUpdate" -> {
					sql.add((String) args[0]);
					if ((failInsert && ((String) args[0]).startsWith("INSERT INTO "))
							|| (failUpdate && ((String) args[0]).startsWith("UPDATE target_table ")))
						throw new java.sql.SQLException("unique conflict", "23000", 1062);
					yield 1;
				}
				case "setLocalInfileInputStream" -> {
					input[0] = (java.io.InputStream) args[0];
					yield null;
				}
				case "executeLargeUpdate" -> {
					sql.add((String) args[0]);
					input[0].readAllBytes();
					yield 0L;
				}
				case "executeQuery" -> Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { java.sql.ResultSet.class }, (r, rm, ra) -> switch (rm.getName()) {
						case "next" -> true;
						case "getLong" -> 0L;
						case "close" -> null;
						default -> throw new AssertionError(rm.getName());
						});
				case "getWarnings", "close" -> null;
				default -> throw new AssertionError(method.getName());
				});
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(p, method, args) -> switch (method.getName()) {
				case "createStatement" -> statement;
				case "getAutoCommit" -> autoCommit;
				case "setAutoCommit" -> {
					autoCommit = (boolean) args[0];
					transactions.add("auto=" + autoCommit);
					yield null;
				}
				case "commit", "rollback" -> {
					transactions.add(method.getName());
					yield null;
				}
				default -> throw new AssertionError(method.getName());
				});
	}

	@ParameterizedTest
	@CsvSource({ "def,app,app.target_table", "app,,app.target_table", ",app,app.target_table",
			"app,app,app.target_table", ",,target_table", "def,,def.target_table", "def,app db,`app db`.target_table" })
	void resolvesTargetThroughoutExecution(String catalog, String schema, String expected) throws Exception {
		for (int[] version : new int[][] { { 5, 1 }, { 5, 6 }, { 5, 7 }, { 8, 0 }, { 8, 4 }, { 9, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("mysql", version[0], version[1], 0);
			for (boolean insert : new boolean[] { true, false }) {
				var table = table(catalog, schema);
				var sql = new ArrayList<String>();
				new MySqlBulkUpsertExecutor(dialect).execute(connection(sql), table,
						BulkUpsertOption.builder().insertWhenNotMatched(insert).stagingTableName("stage").build());
				assertTrue(sql.getFirst().endsWith(" FROM " + expected + " WHERE 1 = 0"), sql.toString());
				assertTrue(sql.get(insert ? 3 : 2).startsWith("UPDATE " + expected + " "), sql.toString());
				if (insert)
					assertTrue(sql.get(4).startsWith("INSERT INTO " + expected + " "), sql.toString());
				assertEquals(catalog, table.getCatalogName());
				assertEquals(schema, table.getSchemaName());
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "other,app", "APP,app" })
	void rejectsAmbiguousDatabaseBeforeExecutingSql(String catalog, String schema) {
		var dialect = DialectResolver.getInstance().getDialect("mysql", 8, 4, 0);
		var sql = new ArrayList<String>();
		assertThrows(IllegalArgumentException.class,
				() -> new MySqlBulkUpsertExecutor(dialect).execute(connection(sql), table(catalog, schema), null));
		assertTrue(sql.isEmpty());
	}

	@Test
	void updatesAndInsertsByExplicitMatchKeyRatherThanAnyUniqueKey() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("mysql", 8, 4, 0);
		var sql = new ArrayList<String>();
		long affected = new MySqlBulkUpsertExecutor(dialect).execute(connection(sql), table(null, null),
				BulkUpsertOption.builder().keyColumn("payload").stagingTableName("stage").build());
		var dml = sql.stream().filter(q -> q.startsWith("UPDATE target_table ") || q.startsWith("INSERT INTO "))
				.toList();
		assertEquals(2, dml.size(), sql.toString());
		assertTrue(dml.get(0).contains("target.payload = source.payload"));
		assertTrue(dml.get(0).endsWith("SET target.id = source.id WHERE source.sqlapp_bulk_matched = 1"));
		assertTrue(sql.get(2).contains("LEFT JOIN target_table AS target ON target.payload = source.payload"));
		assertTrue(dml.get(1).endsWith("WHERE source.sqlapp_bulk_matched = 0"));
		assertFalse(dml.stream().anyMatch(q -> q.contains("ON DUPLICATE KEY") || q.contains("IGNORE")));
		assertEquals(2, affected);
	}

	@Test
	void insertOnlySkipsOnlyRowsMatchingTheRequestedKey() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("mysql", 5, 7, 0);
		var sql = new ArrayList<String>();
		new MySqlBulkUpsertExecutor(dialect).execute(connection(sql), table(null, null),
				BulkUpsertOption.builder().updateWhenMatched(false).stagingTableName("stage").build());
		assertFalse(sql.stream().anyMatch(q -> q.startsWith("UPDATE target_table ")));
		var insert = sql.stream().filter(q -> q.startsWith("INSERT INTO ")).findFirst().orElseThrow();
		assertTrue(sql.get(2).contains("ON target.id = source.id"));
		assertTrue(insert.endsWith("WHERE source.sqlapp_bulk_matched = 0"));
		assertFalse(insert.contains("ON DUPLICATE KEY"));
	}

	@Test
	void keyOnlyUpsertDoesNotIssueAnEmptyOrNoopUpdate() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("mysql", 8, 0, 0);
		var sql = new ArrayList<String>();
		new MySqlBulkUpsertExecutor(dialect).execute(connection(sql), table(null, null),
				BulkUpsertOption.builder().keyColumn("id").keyColumn("payload").stagingTableName("stage").build());
		assertFalse(sql.stream().anyMatch(q -> q.startsWith("UPDATE target_table ")));
		var insert = sql.stream().filter(q -> q.startsWith("INSERT INTO ")).findFirst().orElseThrow();
		assertTrue(sql.get(2).contains("target.id = source.id AND target.payload = source.payload"));
		assertFalse(insert.contains("ON DUPLICATE KEY"));
	}

	@Test
	void rollsBackEarlierUpdateWhenInsertConflictsInOwnedTransaction() {
		autoCommit = true;
		failInsert = true;
		var sql = new ArrayList<String>();
		assertThrows(java.sql.SQLException.class,
				() -> new MySqlBulkUpsertExecutor(DialectResolver.getInstance().getDialect("mysql", 8, 4, 0))
						.execute(connection(sql), table(null, null), null));
		assertTrue(sql.stream().anyMatch(q -> q.startsWith("UPDATE target_table ")));
		assertTrue(transactions.contains("rollback"));
		assertFalse(transactions.contains("commit"));
		assertTrue(sql.getLast().startsWith("DROP TEMPORARY TABLE "));
		assertTrue(autoCommit);
	}

	@Test
	void leavesCallerTransactionOpenOnInsertConflict() {
		failInsert = true;
		var sql = new ArrayList<String>();
		assertThrows(java.sql.SQLException.class,
				() -> new MySqlBulkUpsertExecutor(DialectResolver.getInstance().getDialect("mysql", 8, 4, 0))
						.execute(connection(sql), table(null, null), null));
		assertTrue(transactions.isEmpty());
		assertFalse(autoCommit);
	}

	@Test
	void updateConflictStopsBeforeInsert() {
		failUpdate = true;
		var sql = new ArrayList<String>();
		assertThrows(java.sql.SQLException.class,
				() -> new MySqlBulkUpsertExecutor(DialectResolver.getInstance().getDialect("mysql", 8, 4, 0))
						.execute(connection(sql), table(null, null), null));
		assertFalse(sql.stream().anyMatch(q -> q.startsWith("INSERT INTO ")));
	}

	@Test
	void keepsInternalMatchMarkerSeparateFromUserColumns() throws Exception {
		var sql = new ArrayList<String>();
		var table = table(null, null);
		table.getColumns().add("SQLAPP_BULK_MATCHED", c -> c.setDataType(DataType.INT));
		new MySqlBulkUpsertExecutor(DialectResolver.getInstance().getDialect("mysql", 8, 4, 0)).execute(connection(sql),
				table, BulkUpsertOption.builder().stagingTableName("stage").build());
		assertTrue(sql.getFirst().contains("(sqlapp_bulk_matched_1 TINYINT NOT NULL DEFAULT 0) AS SELECT "));
		assertTrue(sql.stream().filter(q -> q.startsWith("INSERT INTO ")).findFirst().orElseThrow()
				.endsWith("WHERE source.sqlapp_bulk_matched_1 = 0"));
	}

	@Test
	void preservesTimestampInstantsInBothTargetAssignments() throws Exception {
		var sql = new ArrayList<String>();
		var table = table(null, null);
		table.getColumns().add("ts", c -> c.setDataType(DataType.TIMESTAMPVERSION));
		new MySqlBulkUpsertExecutor(DialectResolver.getInstance().getDialect("mysql", 8, 0, 19))
				.execute(connection(sql), table, BulkUpsertOption.builder().stagingTableName("stage").build());
		for (String prefix : new String[] { "UPDATE target_table", "INSERT INTO target_table" }) {
			String statement = sql.stream().filter(q -> q.startsWith(prefix)).findFirst().orElseThrow();
			assertTrue(statement.contains("UNIX_TIMESTAMP(source.ts)"), statement);
			assertTrue(statement.contains("'+00:00'"), statement);
		}
	}

	private Table table(String catalog, String schema) {
		var table = new Table("target_table").setCatalogName(catalog).setSchemaName(schema);
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("payload", c -> c.setDataType(DataType.INT));
		table.setPrimaryKey(table.getColumns().get("id"));
		return table;
	}
}
