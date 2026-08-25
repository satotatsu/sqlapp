/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

class BulkDataTest {
	@Test
	void streamsRowsAndOmitsServerGeneratedColumns() throws Exception {
		final Table table = createTable();
		try (BulkData data = new BulkData(table, BulkOption.defaults())) {
			assertEquals(Set.of(1, 2), data.getColumnOrdinals());
			assertEquals("name", data.getColumnName(1));
			assertEquals(Types.NVARCHAR, data.getColumnType(1));
			assertEquals(100, data.getPrecision(1));
			assertEquals("amount", data.getColumnName(2));
			assertEquals(2, data.getScale(2));
			assertTrue(data.next());
			assertArrayEquals(new Object[] { "alpha", new BigDecimal("12.34") },
					data.getRowData());
			assertFalse(data.next());
			assertEquals(1, data.getRowCount());
		}
	}

	@Test
	void includesIdentityWhenRequested() throws Exception {
		final Table table = createTable();
		try (BulkData data = new BulkData(table,
				BulkOption.builder().keepIdentity(true).build())) {
			assertEquals(Set.of(1, 2, 3), data.getColumnOrdinals());
			assertEquals("id", data.getColumnName(1));
			assertTrue(data.next());
			assertArrayEquals(new Object[] { 10, "alpha", new BigDecimal("12.34") },
					data.getRowData());
		}
	}

	@Test
	void mapsAllCommonOptions() throws Exception {
		final var source = BulkOption.builder().batchSize(500)
				.bulkCopyTimeout(42).checkConstraints(true).fireTriggers(true)
				.keepIdentity(true).keepNulls(true).tableLock(true)
				.useTransaction(true).allowEncryptedValueModifications(true)
				.build();
		final var target = BulkData.toSqlServerOptions(source);
		assertEquals(500, target.getBatchSize());
		assertEquals(42, target.getBulkCopyTimeout());
		assertTrue(target.isCheckConstraints());
		assertTrue(target.isFireTriggers());
		assertTrue(target.isKeepIdentity());
		assertTrue(target.isKeepNulls());
		assertTrue(target.isTableLock());
		assertTrue(target.isUseInternalTransaction());
		assertTrue(target.isAllowEncryptedValueModifications());
	}

	@Test
	void resolvesSqlServerProviderAndRejectsBadAccessOrder() throws Exception {
		assertTrue(BulkInsertResolver.resolve(DialectHolder.defaultDialect2022)
				instanceof SqlServerBulkInsertExecutor);
		try (BulkData data = new BulkData(createTable(), BulkOption.defaults())) {
			assertThrows(java.sql.SQLException.class, data::getRowData);
			assertThrows(IllegalArgumentException.class,
					() -> data.getColumnName(0));
		}
	}

	private Table createTable() {
		final Table table = new Table("target");
		table.setDialect(DialectHolder.defaultDialect2022);
		table.getColumns().add(new Column("id").setDataType(DataType.INT)
				.setIdentity(true));
		table.getColumns().add(new Column("name").setDataType(DataType.NVARCHAR)
				.setLength(100));
		table.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL)
				.setLength(10).setScale(2));
		table.getColumns().add(new Column("computed")
				.setDataType(DataType.INT).setFormula("[id] + 1"));
		table.getRows().add(row -> {
			row.put("id", 10);
			row.put("name", "alpha");
			row.put("amount", 12.34);
			row.put("computed", 11);
		});
		return table;
	}
}
