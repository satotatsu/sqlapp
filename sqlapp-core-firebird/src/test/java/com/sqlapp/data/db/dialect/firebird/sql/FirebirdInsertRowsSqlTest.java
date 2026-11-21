/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Table;

/** INSERT ... SELECT parameters are cast to a type the version understands. */
class FirebirdInsertRowsSqlTest {

	private static Table table() {
		final Table table = new Table("Order Items");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getColumns().add("NAME", c -> c.setDataType(DataType.VARCHAR).setLength(10).setCharacterSet("UTF8"));
		table.getColumns().add("AMOUNT",
				c -> c.setDomain(new Domain("D_AMOUNT").setDataType(DataType.NUMERIC).setLength(18).setScale(2))
						.setDomainName("D_AMOUNT").setDataType(DataType.NUMERIC).setLength(18).setScale(2));
		return table;
	}

	private static String sql(final Dialect dialect, final Table table) {
		return dialect.createSqlFactoryRegistry().createSql(table, SqlType.INSERT_ROWS).get(0).getSqlText();
	}

	@Test
	void modernCastsToQuotedTypeOfColumn() {
		final Dialect dialect = DialectHolder.defaultDialect30;
		final String sql = sql(dialect, table());
		final String table = dialect.quote("Order Items");
		assertTrue(sql.contains("AS TYPE OF COLUMN " + table + ".ID)"), sql);
		assertTrue(sql.contains("AS TYPE OF COLUMN " + table + ".NAME)"), sql);
		assertFalse(sql.contains("TYPE OF COLUMN Order Items"), sql);
	}

	@Test
	void legacyCastsToDeclaredTypes() {
		for (final Dialect dialect : new Dialect[] { DialectHolder.defaultDialect20, DialectHolder.defaultDialect }) {
			final String sql = sql(dialect, table());
			assertFalse(sql.contains("TYPE OF COLUMN"), sql);
			assertTrue(sql.contains("AS INTEGER)"), sql);
			assertTrue(sql.contains("AS VARCHAR(10) CHARACTER SET UTF8)"), sql);
			assertTrue(sql.contains("AS NUMERIC(18, 2))") || sql.contains("AS NUMERIC(18,2))"), sql);
		}
	}

	@Test
	void legacyCastsNameOnlyDomainToColumnType() {
		final Table table = new Table("T");
		table.getColumns().add("AMOUNT",
				c -> c.setDomainName("D_AMOUNT").setDataType(DataType.NUMERIC).setLength(18).setScale(2));
		final String sql = sql(DialectHolder.defaultDialect20, table);
		assertTrue(sql.contains("AS NUMERIC(18, 2))") || sql.contains("AS NUMERIC(18,2))"), sql);
	}

	@Test
	void legacyRejectsArrayColumns() {
		final Table table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT).setArrayDimension(1).setArrayDimensionLowerBound(1)
				.setArrayDimensionUpperBound(3));
		assertThrows(IllegalArgumentException.class, () -> sql(DialectHolder.defaultDialect20, table));
	}
}
