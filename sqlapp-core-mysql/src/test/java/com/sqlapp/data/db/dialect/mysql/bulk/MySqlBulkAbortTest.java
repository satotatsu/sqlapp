/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.mysql.cj.jdbc.JdbcStatement;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;

/**
 * 値を符号化できなかったときの LOAD DATA の中断(DBには接続しない)。
 * <p>
 * 入力ストリームから例外を投げると JDBC ドライバーが接続を切断する(Claude 側の検証環境の MySQL 8.4 / MariaDB 11.4
 * で確認) ため、各レコードの末尾に中断フラグを付け、符号化に失敗したら中断レコードを送ってサーバー側で文を失敗させる。
 * </p>
 */
class MySqlBulkAbortTest {

	private String loadSql;
	private String streamed;

	/** 中断レコード(フラグ = 1)を受け取ったらサーバーと同じく文をエラーにする */
	private Connection connection() {
		final InputStream[] input = new InputStream[1];
		final JdbcStatement statement = (JdbcStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { JdbcStatement.class }, (p, m, a) -> switch (m.getName()) {
				case "unwrap" -> p;
				case "setLocalInfileInputStream" -> {
					input[0] = (InputStream) a[0];
					yield null;
				}
				case "executeLargeUpdate" -> {
					loadSql = (String) a[0];
					streamed = new String(input[0].readAllBytes(), StandardCharsets.UTF_8);
					if (streamed.endsWith("\u001f1\u001e")) {
						throw new SQLException("Invalid JSON text", "22032", 3141);
					}
					yield (long) streamed.split("\u001e").length;
				}
				case "getWarnings" -> null;
				case "executeQuery" -> Proxy.newProxyInstance(getClass().getClassLoader(),
						new Class<?>[] { ResultSet.class }, (r, rm, ra) -> switch (rm.getName()) {
						case "next" -> true;
						case "getLong" -> 0L;
						case "close" -> null;
						default -> throw new AssertionError(rm.getName());
						});
				case "close" -> null;
				default -> throw new AssertionError(m.getName());
				});
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(p, m, a) -> switch (m.getName()) {
				case "createStatement" -> statement;
				case "getAutoCommit" -> true;
				default -> throw new AssertionError(m.getName());
				});
	}

	private static Table table(final String... bits) {
		final Table table = new Table("target");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getColumns().add("bits", c -> c.setDataType(DataType.BIT).setLength(3));
		for (int i = 0; i < bits.length; i++) {
			final int id = i + 1;
			final String value = bits[i];
			table.getRows().add(row -> {
				row.put("id", id);
				row.put("bits", value);
			});
		}
		return table;
	}

	private long execute(final Dialect dialect, final Table table) throws SQLException {
		return new MySqlBulkInsertExecutor(dialect).execute(connection(), table, BulkOption.defaults());
	}

	@Test
	void addsAbortFlagAndGuardOnMySql57OrLater() throws Exception {
		assertEquals(2, execute(DialectHolder.mysql840Dialect, table("101", "1")));
		assertTrue(loadSql.contains("(id, @sqlapp_bulk_1, @sqlapp_abort)"), loadSql);
		assertTrue(loadSql.contains(" SET id=IF(@sqlapp_abort IS NULL, id, "
				+ "IF(CAST(CONCAT('{', @sqlapp_abort) AS JSON) IS NULL, NULL, NULL)), "
				+ "bits=CAST(CONV(@sqlapp_bulk_1,2,10) AS UNSIGNED)"), loadSql);
		assertEquals("1\u001f101\u001f\\N\u001e2\u001f1\u001f\\N\u001e", streamed);
	}

	@Test
	void sendsAbortRecordAndReportsTheValueFailure() {
		final SQLException e = assertThrows(SQLException.class,
				() -> execute(DialectHolder.mysql840Dialect, table("101", "1111", "1")));
		// 失敗した値の例外を原因とし、サーバーの中断エラーは抑制例外として付ける
		assertInstanceOf(IllegalArgumentException.class, e.getCause());
		assertEquals(1, e.getSuppressed().length);
		assertTrue(e.getMessage().contains("sent=1"), e.getMessage());
		// 失敗した行より後は送らず、中断レコードで終える
		assertEquals("1\u001f101\u001f\\N\u001e\\N\u001f\\N\u001f1\u001e", streamed);
	}

	@Test
	void keepsPreviousBehaviorWithoutAbortExpression() throws Exception {
		assertEquals(1, execute(DialectHolder.mysql564Dialect, table("101")));
		assertFalse(loadSql.contains("@sqlapp_abort"), loadSql);
		assertEquals("1\u001f101\u001e", streamed);
	}
}
