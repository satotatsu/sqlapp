/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * 計算列(col AS (expr))のバージョン別テスト
 */
class SqlServerComputedColumnTest {

	private Table table(final boolean persisted) {
		final Table table = new Table("Orders");
		table.getColumns().add(new Column("Price").setDataType(DataType.INT));
		table.getColumns().add(new Column("Qty").setDataType(DataType.INT));
		table.getColumns().add(new Column("Total").setFormula("Price*Qty").setFormulaPersisted(persisted));
		return table;
	}

	private String text(final List<SqlOperation> operations) {
		return operations.stream().map(SqlOperation::getSqlText).collect(Collectors.joining(";"));
	}

	private Dialect dialect(final int majorVersion) {
		return SqlServerDialectResolver.getInstance().getDialect(majorVersion, 0);
	}

	@Test
	void allVersionsSupportComputedColumns() {
		for (int version : new int[] { 8, 9, 10, 11, 12, 13, 14, 15, 16 }) {
			assertTrue(dialect(version).supportsColumnFormula(), "version=" + version);
		}
	}

	@Test
	void sqlServer2000CreatesComputedColumn() {
		final String sql = text(dialect(8).createSqlFactoryRegistry().createSql(table(false), SqlType.CREATE));
		assertTrue(sql.contains("Total AS Price*Qty"), sql);
		assertFalse(sql.contains("PERSISTED"), sql);
	}

	@Test
	void sqlServer2000RejectsPersistedComputedColumn() {
		final var registry = dialect(8).createSqlFactoryRegistry();
		final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(table(true), SqlType.CREATE));
		assertTrue(e.getMessage().contains("SQL Server 2005"), e.getMessage());
	}

	@Test
	void persistedComputedColumnFrom2005() {
		for (int version : new int[] { 9, 10, 16 }) {
			final String sql = text(dialect(version).createSqlFactoryRegistry().createSql(table(true), SqlType.CREATE));
			assertTrue(sql.contains("Total AS Price*Qty PERSISTED"), "version=" + version + " " + sql);
		}
	}

	@Test
	void insertSkipsComputedColumn() {
		for (int version : new int[] { 8, 10, 16 }) {
			final String sql = text(
					dialect(version).createSqlFactoryRegistry().createSql(table(false), SqlType.INSERT));
			assertTrue(sql.contains("Price"), "version=" + version + " " + sql);
			assertFalse(sql.contains("Total"), "version=" + version + " " + sql);
		}
	}
}
