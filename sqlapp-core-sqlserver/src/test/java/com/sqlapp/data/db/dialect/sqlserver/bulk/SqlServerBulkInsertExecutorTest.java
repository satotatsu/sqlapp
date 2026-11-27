/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/**
 * クラスタ化列ストアへのバルク投入時のbatchSize警告のテスト
 */
class SqlServerBulkInsertExecutorTest {

	private final SqlServerBulkInsertExecutor executor = new SqlServerBulkInsertExecutor(
			SqlServerDialectResolver.getInstance().getDialect(16, 0));

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static Connection connection(final boolean found, final boolean fail, final List<String> params) {
		return proxy(Connection.class, (c, method, args) -> {
			if (!"prepareStatement".equals(method.getName())) {
				throw new UnsupportedOperationException(method.getName());
			}
			if (fail) {
				throw new SQLException("permission denied");
			}
			assertTrue(((String) args[0]).contains("type = 5"));
			return proxy(PreparedStatement.class, (s, m, a) -> switch (m.getName()) {
			case "setString" -> {
				params.add((String) a[1]);
				yield null;
			}
			case "executeQuery" -> proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
			case "next" -> found;
			case "close" -> null;
			default -> throw new UnsupportedOperationException(rm.getName());
			});
			case "close" -> null;
			default -> throw new UnsupportedOperationException(m.getName());
			});
		});
	}

	private static Table table() {
		final Table table = new Table("Sales").setSchemaName("dbo");
		table.getColumns().add(new Column("Id").setDataType(DataType.INT));
		return table;
	}

	@Test
	void warnsOnlyWhenSmallBatchSplitsALargeLoad() {
		final int min = SqlServerBulkInsertExecutor.COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS;
		assertTrue(SqlServerBulkInsertExecutor.mayMissDirectCompression(10_000, min));
		assertTrue(SqlServerBulkInsertExecutor.mayMissDirectCompression(min - 1, 5_000_000));
		// 未指定は全行1バッチ
		assertFalse(SqlServerBulkInsertExecutor.mayMissDirectCompression(null, 5_000_000));
		assertFalse(SqlServerBulkInsertExecutor.mayMissDirectCompression(min, 5_000_000));
		assertFalse(SqlServerBulkInsertExecutor.mayMissDirectCompression(1_048_576, 5_000_000));
		// 総行数が閾値未満ならbatchSizeに関係なくデルタストアに入る
		assertFalse(SqlServerBulkInsertExecutor.mayMissDirectCompression(1_000, min - 1));
	}

	@Test
	void warningMessageExplainsTheFix() {
		final String message = SqlServerBulkInsertExecutor.columnStoreBatchWarning(table(), 10_000, 2_000_000);
		assertTrue(message.contains("Sales"), message);
		assertTrue(message.contains("batchSize=10000"), message);
		assertTrue(message.contains("102400"), message);
		assertTrue(message.contains("delta store"), message);
	}

	@Test
	void clusteredColumnStoreFromSchemaModelNeedsNoQuery() {
		final Table table = table();
		table.getIndexes().add("CCI_Sales").setIndexType(IndexType.ClusteredColumnStore);
		assertTrue(executor.hasClusteredColumnStore(null, table));
	}

	@Test
	void clusteredColumnStoreFromDatabase() {
		final List<String> params = new ArrayList<>();
		assertTrue(executor.hasClusteredColumnStore(connection(true, false, params), table()));
		assertEquals(List.of("dbo.Sales"), params);
		params.clear();
		final Table spaced = new Table("Sales Data").setSchemaName("dbo");
		executor.hasClusteredColumnStore(connection(true, false, params), spaced);
		assertEquals(List.of("dbo.[Sales Data]"), params);
		assertFalse(executor.hasClusteredColumnStore(connection(false, false, params), table()));
		final Table rowStore = table();
		rowStore.getIndexes().add("NCCI_Sales").setIndexType(IndexType.NonClusteredColumnStore);
		assertFalse(executor.hasClusteredColumnStore(connection(false, false, params), rowStore));
	}

	@Test
	void checkFailureDoesNotStopBulkInsert() {
		assertFalse(executor.hasClusteredColumnStore(connection(false, true, new ArrayList<>()), table()));
	}
}
