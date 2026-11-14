/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;

/**
 * 列ストアインデックスのDDL生成テスト
 */
class SqlServerColumnStoreIndexTest {

	private static Table table() {
		final Table table = new Table("Sales");
		table.getColumns().add(new Column("Id").setDataType(DataType.INT));
		table.getColumns().add(new Column("Amount").setDataType(DataType.INT));
		table.getColumns().add(new Column("Region").setDataType(DataType.INT));
		return table;
	}

	private static Index nonclustered(final Table table, final String... columns) {
		final Index index = table.getIndexes().add("NCCI_Sales");
		index.setIndexType(IndexType.NonClusteredColumnStore);
		for (final String column : columns) {
			index.getColumns().add(new Column(column), Order.Asc);
		}
		return index;
	}

	private static Index clustered(final Table table, final String... orderColumns) {
		final Index index = table.getIndexes().add("CCI_Sales");
		index.setIndexType(IndexType.ClusteredColumnStore);
		for (final String column : orderColumns) {
			index.getColumns().add(new Column(column), Order.Asc);
		}
		return index;
	}

	private static String sql(final int version, final Index index) {
		final List<SqlOperation> operations = SqlServerDialectResolver.getInstance().getDialect(version, 0)
				.createSqlFactoryRegistry().createSql(index, SqlType.CREATE);
		return operations.stream().map(SqlOperation::getSqlText).collect(Collectors.joining(";"));
	}

	private static IllegalArgumentException fails(final int version, final Index index) {
		return assertThrows(IllegalArgumentException.class, () -> sql(version, index));
	}

	@Test
	void indexTypeTextAndParse() {
		assertEquals(IndexType.ClusteredColumnStore, IndexType.parse("CLUSTERED COLUMNSTORE"));
		assertEquals(IndexType.NonClusteredColumnStore, IndexType.parse("NONCLUSTERED COLUMNSTORE"));
		assertEquals(IndexType.Clustered, IndexType.parse("CLUSTERED"));
		assertEquals(IndexType.NonClusteredColumnStore, IndexType.parse("NonClusteredColumnStore"));
	}

	@Test
	void nonclusteredColumnStoreFrom2012() {
		for (int version : new int[] { 11, 12, 13, 16 }) {
			final String sql = sql(version, nonclustered(table(), "Amount", "Region"));
			assertTrue(sql.startsWith("CREATE NONCLUSTERED COLUMNSTORE INDEX NCCI_Sales ON Sales"), sql);
			assertTrue(sql.contains("Amount, Region"), sql);
			assertFalse(sql.contains("UNIQUE"), sql);
		}
		assertTrue(fails(10, nonclustered(table(), "Amount")).getMessage().contains("SQL Server 2012"));
	}

	@Test
	void clusteredColumnStoreHasNoColumnList() {
		for (int version : new int[] { 12, 13, 16 }) {
			final String sql = sql(version, clustered(table()));
			assertEquals("CREATE CLUSTERED COLUMNSTORE INDEX CCI_Sales ON Sales", sql.trim(), "version=" + version);
		}
		assertTrue(fails(11, clustered(table())).getMessage().contains("SQL Server 2014"));
	}

	@Test
	void orderedClusteredColumnStoreFrom2022() {
		final String sql = sql(16, clustered(table(), "Region", "Id"));
		assertTrue(sql.startsWith("CREATE CLUSTERED COLUMNSTORE INDEX CCI_Sales ON Sales"), sql);
		assertTrue(sql.contains("ORDER ( Region, Id )"), sql);
		assertTrue(fails(15, clustered(table(), "Region")).getMessage().contains("SQL Server 2022"));
	}

	@Test
	void onlyColumnStoreOptionsAreEmitted() {
		final Index index = nonclustered(table(), "Amount");
		index.getSpecifics().put("PAD_INDEX", "OFF");
		index.getSpecifics().put("FILLFACTOR", "80");
		index.getSpecifics().put("ALLOW_ROW_LOCKS", "ON");
		index.getSpecifics().put("ALLOW_PAGE_LOCKS", "ON");
		index.getSpecifics().put("IGNORE_DUP_KEY", "OFF");
		index.getSpecifics().put("STATISTICS_NORECOMPUTE", "OFF");
		index.getSpecifics().put("COMPRESSION_DELAY", "10");
		index.getSpecifics().put("DATA_COMPRESSION", "columnstore_archive");
		index.getSpecifics().put("MAXDOP", "4");
		final String sql = sql(13, index);
		assertTrue(sql.contains("MAXDOP = 4"), sql);
		assertTrue(sql.contains("COMPRESSION_DELAY = 10"), sql);
		assertTrue(sql.contains("DATA_COMPRESSION = COLUMNSTORE_ARCHIVE"), sql);
		for (String rowStoreOnly : new String[] { "PAD_INDEX", "FILLFACTOR", "ALLOW_ROW_LOCKS", "ALLOW_PAGE_LOCKS",
				"IGNORE_DUP_KEY", "STATISTICS_NORECOMPUTE" }) {
			assertFalse(sql.contains(rowStoreOnly), rowStoreOnly + ": " + sql);
		}
	}

	@Test
	void optionVersionBoundaries() {
		Index index = nonclustered(table(), "Amount");
		index.getSpecifics().put("COMPRESSION_DELAY", "10");
		assertTrue(fails(12, index).getMessage().contains("COMPRESSION_DELAY"));
		index = nonclustered(table(), "Amount");
		index.getSpecifics().put("DATA_COMPRESSION", "COLUMNSTORE_ARCHIVE");
		assertTrue(fails(11, index).getMessage().contains("SQL Server 2014"));
		assertTrue(sql(12, index).contains("DATA_COMPRESSION = COLUMNSTORE_ARCHIVE"));
		index = nonclustered(table(), "Amount");
		index.getSpecifics().put("DATA_COMPRESSION", "PAGE");
		assertTrue(fails(16, index).getMessage().contains("COLUMNSTORE_ARCHIVE"));
	}

	@Test
	void filteredNonclusteredColumnStoreFrom2016() {
		final Index index = nonclustered(table(), "Amount");
		index.setWhere("Region = 1");
		assertTrue(sql(13, index).contains("WHERE Region = 1"));
		assertTrue(fails(12, index).getMessage().contains("SQL Server 2016"));
		final Index cci = clustered(table());
		cci.setWhere("Region = 1");
		assertTrue(fails(16, cci).getMessage().contains("WHERE"));
	}

	@Test
	void unsupportedDefinitionsAreRejected() {
		Index index = nonclustered(table(), "Amount");
		index.getIncludes().add(new Column("Region"));
		assertTrue(fails(16, index).getMessage().contains("INCLUDE"));
		index = nonclustered(table());
		index.getColumns().add(new Column("Amount"), Order.Desc);
		assertTrue(fails(16, index).getMessage().contains("DESC"));
		index = nonclustered(table(), "Amount");
		index.setUnique(true);
		assertTrue(fails(16, index).getMessage().contains("UNIQUE"));
		assertTrue(fails(16, nonclustered(table())).getMessage().contains("at least one column"));
	}

	@Test
	void columnStoreCompressionIsNotATableOption() {
		final var registry = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry();
		Table table = table();
		table.setCompression(true);
		table.setCompressionType("COLUMNSTORE");
		final Table t1 = table;
		assertTrue(assertThrows(IllegalArgumentException.class, () -> registry.createSql(t1, SqlType.CREATE))
				.getMessage().contains("columnstore index"));
		table = table();
		table.getSpecifics().put("COMPRESSION_DELAY", "10");
		final Table t2 = table;
		assertTrue(assertThrows(IllegalArgumentException.class, () -> registry.createSql(t2, SqlType.CREATE))
				.getMessage().contains("COMPRESSION_DELAY"));
		table = table();
		table.setCompression(true);
		table.setCompressionType("PAGE");
		final String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("DATA_COMPRESSION = PAGE"), sql);
	}

	@Test
	void createTableWithClusteredColumnStore() {
		final Table table = table();
		clustered(table);
		final String sql = SqlServerDialectResolver.getInstance().getDialect(16, 0).createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE).stream().map(SqlOperation::getSqlText)
				.collect(Collectors.joining(";"));
		assertTrue(sql.contains("CREATE CLUSTERED COLUMNSTORE INDEX CCI_Sales ON Sales"), sql);
		assertFalse(sql.contains("CREATE CLUSTERED INDEX"), sql);
	}
}
