/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

class InformixBulkExecutionTest {
	public interface PutConnection extends Connection {
		boolean getIFX_USEPUT() throws SQLException;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<? extends T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static class Driver {
		String url = "jdbc:informix-sqli://host/db:IFX_USEPUT=1;";
		String clientInfo;
		boolean clientInfoUnsupported;
		boolean transactions = true, serializable = true;
		int logging = 1, pending, batches, prepared, closed, queries;
		boolean failBatch, aggregatePutCounts;
		boolean exposePut, driverPut;
		final List<String> sql = new ArrayList<>();
		final List<Object[]> eligible = new ArrayList<>();
		final List<Object> bound = new ArrayList<>();

		Connection connection() {
			return proxy(exposePut ? PutConnection.class : Connection.class,
					(p, method, args) -> switch (method.getName()) {
					case "getIFX_USEPUT" -> driverPut;
					case "isWrapperFor" -> false;
					case "getClientInfo" -> {
						if (clientInfoUnsupported)
							throw new SQLFeatureNotSupportedException();
						yield clientInfo;
					}
					case "getMetaData" -> proxy(DatabaseMetaData.class, (mp, mm, ma) -> switch (mm.getName()) {
					case "getURL" -> url;
					case "supportsTransactions" -> transactions;
					case "supportsTransactionIsolationLevel" -> serializable;
					default -> throw new AssertionError(mm.getName());
					});
					case "prepareStatement" -> statement((String) args[0]);
					default -> throw new AssertionError("Unexpected connection operation: " + method.getName());
					});
		}

		PreparedStatement statement(String text) {
			prepared++;
			sql.add(text);
			return proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
			case "getConnection" -> connection();
			case "setInt", "setString", "setNull", "setObject", "setQueryTimeout" -> {
				bound.add(args[1]);
				yield null;
			}
			case "addBatch" -> {
				pending++;
				yield null;
			}
			case "executeBatch" -> {
				batches++;
				if (failBatch)
					throw new BatchUpdateException("failed", new int[] { Statement.EXECUTE_FAILED });
				final int[] counts = new int[pending];
				java.util.Arrays.fill(counts, aggregatePutCounts ? Statement.SUCCESS_NO_INFO : 1);
				if (aggregatePutCounts)
					counts[0] = pending;
				pending = 0;
				yield counts;
			}
			case "executeQuery" -> {
				queries++;
				if (text.contains("is_logging"))
					yield rows(java.util.Collections.singletonList(new Object[] { logging }));
				if (text.startsWith("SELECT DBINFO"))
					yield rows(java.util.Collections.singletonList(new Object[] { "db" }));
				if (text.startsWith("SELECT TRIM"))
					yield rows(eligible);
				throw new AssertionError(text);
			}
			case "close" -> {
				closed++;
				yield null;
			}
			default -> throw new AssertionError(method.getName());
			});
		}

		ResultSet rows(List<Object[]> data) {
			final int[] position = { -1 };
			return proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
			case "next" -> ++position[0] < data.size();
			case "getInt", "getString" -> data.get(position[0])[(int) args[0] - 1];
			case "wasNull" -> false;
			case "close" -> null;
			default -> throw new AssertionError(method.getName());
			});
		}
	}

	private Table table() {
		final var table = new Table("items").setSchemaName("owner").setCatalogName("db");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		for (int i = 1; i <= 2; i++) {
			final var row = table.newRow();
			row.put("id", i);
			table.getRows().add(row);
		}
		return table;
	}

	@Test
	void nativePutUsesExistingTypedBatchAndPropagatesFailureWithoutOwningTransactions() throws Exception {
		final var driver = new Driver();
		final var executor = new InformixBulkInsertExecutor(DialectHolder.defaultDialect);
		final var option = BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).batchSize(1).build();
		assertEquals(2, executor.execute(driver.connection(), table(), option));
		assertEquals(2, driver.batches);
		assertEquals(1, driver.closed);
		assertTrue(driver.sql.get(0).startsWith("INSERT INTO"));
		assertTrue(driver.bound.containsAll(List.of(1, 2)));
		driver.failBatch = true;
		assertThrows(BatchUpdateException.class, () -> executor.execute(driver.connection(), table(), option));
		assertEquals(2, driver.closed);
	}

	@Test
	void countsThePutBatchTotalWithoutCountingSuccessMarkersAgain() throws Exception {
		final var driver = new Driver();
		driver.aggregatePutCounts = true;
		final var executor = new InformixBulkInsertExecutor(DialectHolder.defaultDialect);
		final var option = BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).build();
		assertEquals(2, executor.execute(driver.connection(), table(), option));
		assertEquals(1, driver.batches);
	}

	@Test
	void nativeRequiresVerifiedPutAndAutoPreservesOrdinaryBatchBehavior() throws Exception {
		final var driver = new Driver();
		driver.url = "jdbc:informix-sqli://host/db:IFX_USEPUT=0;";
		final var executor = new InformixBulkInsertExecutor(DialectHolder.defaultDialect);
		assertThrows(IllegalArgumentException.class, () -> executor.execute(driver.connection(), table(),
				BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).build()));
		assertEquals(0, driver.prepared);
		assertEquals(2, executor.execute(driver.connection(), table(), BulkOption.defaults()));
		driver.clientInfo = "1";
		assertEquals(2, executor.execute(driver.connection(), table(),
				BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).build()));
		driver.clientInfo = null;
		driver.clientInfoUnsupported = true;
		driver.url = "jdbc:informix-sqli://host/db:IFX_USEPUT=1;";
		assertEquals(2, executor.execute(driver.connection(), table(),
				BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).build()));
	}

	@Test
	void resolvesAtomicProviderAndLocksResolvedTablesInStableOrder() {
		final var dialect = DialectHolder.defaultDialect;
		final var provider = AtomicTableReplacementProvider.resolve(dialect);
		assertInstanceOf(InformixAtomicTableReplacementProvider.class, provider);
		assertEquals(
				List.of("LOCK TABLE \"owner\".\"A\" IN EXCLUSIVE MODE", "LOCK TABLE \"owner\".\"B\" IN EXCLUSIVE MODE"),
				provider.lockSql(dialect,
						List.of(new Table("B").setSchemaName("owner"), new Table("A").setSchemaName("owner"))));
		assertEquals(List.of("LOCK TABLE \"odd\"\"owner\".\"a b\" IN EXCLUSIVE MODE"),
				provider.lockSql(dialect, List.of(new Table("a b").setSchemaName("odd\"owner"))));
		assertThrows(IllegalArgumentException.class, () -> provider.lockSql(dialect, List.of(new Table("unresolved"))));
		assertThrows(IllegalArgumentException.class,
				() -> provider.lockSql(dialect, java.util.Collections.nCopies(33, table())));
	}

	@Test
	void checksActualDriverStateBeforeTheOriginalConnectionUrl() throws Exception {
		final var driver = new Driver();
		driver.exposePut = true;
		driver.driverPut = false;
		final var executor = new InformixBulkInsertExecutor(DialectHolder.defaultDialect);
		final var option = BulkOption.builder().insertMethod(BulkInsertMethod.NATIVE).build();
		assertThrows(IllegalArgumentException.class, () -> executor.execute(driver.connection(), table(), option));
		assertEquals(0, driver.prepared);
		driver.driverPut = true;
		driver.url = "jdbc:informix-sqli://host/db:IFX_USEPUT=0;";
		assertEquals(2, executor.execute(driver.connection(), table(), option));
	}

	@Test
	void atomicReplacementRequiresLoggedSerializableDatabase() throws Exception {
		final var driver = new Driver();
		final var provider = new InformixAtomicTableReplacementProvider();
		provider.validate(driver.connection());
		assertEquals(1, driver.queries);
		driver.logging = 0;
		assertThrows(SQLException.class, () -> provider.validate(driver.connection()));
		driver.logging = 1;
		driver.serializable = false;
		assertThrows(SQLException.class, () -> provider.validate(driver.connection()));
	}

	@Test
	void atomicTableValidationUsesOneSetQueryAndRejectsMissingUnsafeAndCrossDatabaseTables() throws Exception {
		final var driver = new Driver();
		final var provider = new InformixAtomicTableReplacementProvider();
		final List<Table> tables = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			tables.add(new Table("table" + i).setSchemaName("owner").setCatalogName("db"));
			driver.eligible.add(new Object[] { "owner", "table" + i });
		}
		provider.validateTables(driver.connection(), tables);
		assertEquals(2, driver.queries);
		final String query = driver.sql.get(1);
		assertTrue(query.contains("t.flags IN (0, 1)"));
		assertTrue(query.contains("systriggers"));
		assertTrue(query.contains("sysinherits"));
		assertEquals(40, driver.bound.size());
		driver.eligible.removeFirst();
		assertThrows(IllegalArgumentException.class, () -> provider.validateTables(driver.connection(), tables));
		tables.get(0).setCatalogName("another_database");
		assertThrows(IllegalArgumentException.class, () -> provider.validateTables(driver.connection(), tables));
	}
}
