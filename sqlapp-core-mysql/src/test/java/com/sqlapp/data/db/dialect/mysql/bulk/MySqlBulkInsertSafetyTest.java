package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mysql.cj.jdbc.JdbcStatement;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mysql.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;

class MySqlBulkInsertSafetyTest {
	private final List<String> calls = new ArrayList<>();
	private InputStream input;
	private SQLWarning warning;
	private long affected = 1;
	private long warningCount;
	private boolean commitFails;
	private boolean resetFails;
	private boolean consume = true;
	private boolean autoCommit = true;
	private boolean rollbackFails;
	private boolean detachFails;
	private String engine = "InnoDB";
	private String loadSql;
	private String timeZone;
	private String loadedText;

	@Test
	void usesNamedUtcForLegacyTimestampLoads() throws Exception {
		timeZone = "Etc/UTC";
		Table table = new Table("target");
		table.getColumns().add("ts", c -> c.setDataType(DataType.TIMESTAMPVERSION));
		table.getRows()
				.add(row -> row.put("ts", Timestamp.from(java.time.Instant.parse("2026-11-01T05:30:00.123456Z"))));
		new MySqlBulkInsertExecutor(DialectHolder.mysql570Dialect).execute(connection(), table, BulkOption.defaults());
		assertTrue(loadedText.startsWith("2026-11-01 05:30:00.123456"), loadedText);
		assertFalse(loadedText.contains("+00:00"), loadedText);
	}

	private Connection connection() {
		var statement = (JdbcStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { JdbcStatement.class }, (p, m, a) -> switch (m.getName()) {
				case "unwrap" -> p;
				case "execute" -> {
					calls.add((String) a[0]);
					yield false;
				}
				case "setLocalInfileInputStream" -> {
					input = (InputStream) a[0];
					calls.add(input == null ? "detach" : "attach");
					if (input == null && detachFails)
						throw new SQLException("detach failure");
					yield null;
				}
				case "executeLargeUpdate", "executeUpdate" -> {
					loadSql = (String) a[0];
					calls.add("load");
					if (consume)
						loadedText = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
					if (m.getName().equals("executeLargeUpdate"))
						yield affected;
					yield (int) affected;
				}
				case "executeQuery" -> Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { java.sql.ResultSet.class }, (r, rm, ra) -> switch (rm.getName()) {
						case "next" -> true;
						case "getLong" -> warningCount;
						case "getString" -> timeZone;
						case "close" -> null;
						default -> throw new AssertionError(rm.getName());
						});
				case "getWarnings" -> {
					calls.add("warnings");
					yield warning;
				}
				case "close" -> {
					calls.add("close statement");
					yield null;
				}
				default -> throw new AssertionError(m.getName());
				});
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(p, m, a) -> switch (m.getName()) {
				case "createStatement" -> statement;
				case "getAutoCommit" -> autoCommit;
				case "setAutoCommit" -> {
					calls.add("auto=" + a[0]);
					if (Boolean.TRUE.equals(a[0]) && resetFails)
						throw new SQLException("reset failure");
					yield null;
				}
				case "commit" -> {
					calls.add("commit");
					if (commitFails)
						throw new SQLException("commit failure");
					yield null;
				}
				case "rollback" -> {
					calls.add("rollback");
					if (rollbackFails)
						throw new SQLException("rollback failure");
					yield null;
				}
				case "prepareStatement" -> Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { PreparedStatement.class }, (q, method, args) -> switch (method.getName()) {
						case "setString", "close" -> null;
						case "executeQuery" -> {
							int[] next = { 0 };
							yield Proxy.newProxyInstance(getClass().getClassLoader(),
									new Class<?>[] { ResultSet.class }, (r, rm, ra) -> switch (rm.getName()) {
									case "next" -> next[0]++ == 0;
									case "getString" -> engine;
									case "close" -> null;
									default -> throw new AssertionError(rm.getName());
									});
						}
						default -> throw new AssertionError(method.getName());
						});
				default -> throw new AssertionError(m.getName());
				});
	}

	private Table table() {
		var table = new Table("target");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getRows().add(row -> row.put("id", 1));
		return table;
	}

	private long execute(BulkOption options) throws SQLException {
		return new MySqlBulkInsertExecutor(DialectHolder.mysql840Dialect).execute(connection(), table(), options);
	}

	@Test
	void rejectsWarningsEvenWhenCountsMatch() {
		warning = new SQLWarning("value truncated", "01000", 1265);
		var failure = assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
		assertTrue(failure.getMessage().contains("1265"), failure.getMessage());
		assertTrue(failure.getMessage().contains("AUTOCOMMIT"), failure.getMessage());
		assertTrue(calls.contains("detach"));
	}

	@Test
	void rejectsCountMismatch() {
		affected = 0;
		var failure = assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
		assertTrue(failure.getMessage().contains("sent=1"), failure.getMessage());
	}

	@Test
	void rejectsIncompleteInput() {
		consume = false;
		assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
	}

	@Test
	void commitsOnlyAfterValidationAndResourceCleanup() throws Exception {
		assertEquals(1, execute(BulkOption.builder().useTransaction(true).build()));
		assertTrue(calls.indexOf("warnings") < calls.indexOf("commit"));
		assertTrue(calls.lastIndexOf("close statement") < calls.indexOf("commit"));
		assertEquals("auto=true", calls.getLast());
	}

	@Test
	void rollsBackOwnedTransactionOnWarning() {
		warning = new SQLWarning("out of range", "22003", 1264);
		assertThrows(SQLException.class, () -> execute(BulkOption.builder().useTransaction(true).build()));
		assertTrue(calls.contains("rollback"));
		assertFalse(calls.contains("commit"));
	}

	@Test
	void neverCompletesCallerTransaction() {
		autoCommit = false;
		warning = new SQLWarning("out of range", "22003", 1264);
		assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
		assertFalse(calls.contains("rollback"));
		assertFalse(calls.contains("commit"));
		assertFalse(calls.stream().anyMatch(c -> c.startsWith("auto=")));
	}

	@Test
	void doesNotRestoreAutocommitAfterFailedRollback() {
		rollbackFails = true;
		warning = new SQLWarning("out of range", "22003", 1264);
		var failure = assertThrows(SQLException.class,
				() -> execute(BulkOption.builder().useTransaction(true).build()));
		assertTrue(failure.getSuppressed().length > 0);
		assertFalse(calls.contains("auto=true"));
	}

	@Test
	void preservesLoadFailureWhenDetachAlsoFails() {
		detachFails = true;
		warning = new SQLWarning("out of range", "22003", 1264);
		var failure = assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
		assertTrue(failure.getMessage().contains("1264"), failure.getMessage());
		assertTrue(failure.getSuppressed().length > 0);
	}

	@Test
	void refusesUnverifiableTransactionGuaranteeBeforeLoading() {
		engine = "MyISAM";
		assertThrows(SQLException.class, () -> execute(BulkOption.builder().useTransaction(true).build()));
		assertFalse(calls.contains("load"));
	}

	@Test
	void rejectsWarningsWithoutStoredDetails() {
		warningCount = 1;
		assertThrows(SQLException.class, () -> execute(BulkOption.defaults()));
	}

	@Test
	void reportsUnknownCommitOutcome() {
		commitFails = true;
		var failure = assertThrows(SQLException.class,
				() -> execute(BulkOption.builder().useTransaction(true).build()));
		assertTrue(failure.getMessage().contains("COMMIT_UNKNOWN"));
	}

	@Test
	void reportsCommittedRowsWhenResetFails() {
		resetFails = true;
		var failure = assertThrows(SQLException.class,
				() -> execute(BulkOption.builder().useTransaction(true).build()));
		assertTrue(failure.getMessage().contains("COMMITTED"));
		assertFalse(calls.contains("rollback"));
	}

	@Test
	void stagingWarningStopsUpsertBeforeTargetDml() {
		warning = new SQLWarning("value truncated", "01000", 1265);
		var table = table();
		table.setPrimaryKey(table.getColumns().get("id"));
		assertThrows(SQLException.class,
				() -> new MySqlBulkUpsertExecutor(DialectHolder.mysql840Dialect).execute(connection(), table,
						com.sqlapp.jdbc.bulk.BulkUpsertOption.builder().useTransaction(false).build()));
		assertFalse(calls.stream().anyMatch(sql -> sql.startsWith("INSERT INTO ") || sql.startsWith("UPDATE ")));
	}

}
