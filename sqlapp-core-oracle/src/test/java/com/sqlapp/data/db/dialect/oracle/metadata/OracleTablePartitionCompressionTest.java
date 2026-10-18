/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

class OracleTablePartitionCompressionTest {
	private Table read(String compression) throws SQLException {
		return read(compression, false, null, null);
	}
	private Table read(String compression, boolean iot, String tableSpace, String iotSpace) throws SQLException {
		return read(compression, iot, tableSpace, iotSpace, null);
	}
	private Table read(String compression, boolean iot, String tableSpace, String iotSpace, String logging) throws SQLException {
		Table table = new Table("T");
		if (iot) table.getSpecifics().put("oracle.table.organization", "INDEX");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		OracleTableReader reader = new OracleTableReader(DialectHolder.oracle10gDialect);
		reader.setPartition(null, new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return java.util.Set
						.of("PARTITIONING_TYPE", "SUBPARTITIONING_TYPE", "PARTITION_NAME", "HIGH_VALUE", "COMPRESSION", "TABLESPACE_NAME", "IOT_TABLESPACE_NAME", "LOGGING")
						.contains(key);
			}

			@Override
			public String getString(String key) {
				return switch (key) {
				case "PARTITIONING_TYPE" -> "RANGE";
				case "SUBPARTITIONING_TYPE" -> "NONE";
				case "PARTITION_NAME" -> "P1";
				case "HIGH_VALUE" -> "100";
				case "COMPRESSION" -> compression;
				case "LOGGING" -> logging;
				case "TABLESPACE_NAME" -> tableSpace;
				case "IOT_TABLESPACE_NAME" -> iotSpace;
				default -> null;
				};
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}
		}, table);
		table.getPartitioning().getPartitioningColumns().add("ID");
		return table;
	}

	@Test
	void iotPartitionUsesItsPrimaryIndexTablespaceOnlyAsFallback() throws SQLException {
		assertEquals("IOT_SPACE", read(null, true, null, "IOT_SPACE").getPartitioning().getPartitions().get(0).getTableSpaceName());
		assertNull(read(null, false, null, "IOT_SPACE").getPartitioning().getPartitions().get(0).getTableSpaceName());
		assertEquals("TABLE_SPACE", read(null, true, "TABLE_SPACE", "IOT_SPACE").getPartitioning().getPartitions().get(0).getTableSpaceName());
	}
	@Test
	void nullCompressionDoesNotInventACompressedPartition() throws SQLException {
		Table table = read(null);
		assertFalse(table.getPartitioning().getPartitions().get(0).isCompression());
		String sql = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0)
				.getSqlText();
		assertTrue(sql.contains("PARTITION BY RANGE"), sql);
		assertFalse(sql.contains("COMPRESS"), sql);
	}

	@Test
	void unspecifiedCompositeCompressionIsNotEnabled() throws SQLException {
		assertFalse(read("NONE").getPartitioning().getPartitions().get(0).isCompression());
		assertFalse(read("none").getPartitioning().getPartitions().get(0).isCompression());
		assertTrue(read("enabled").getPartitioning().getPartitions().get(0).isCompression());
	}
	@Test
	void unspecifiedCompositeLoggingIsOmittedButExplicitDefaultsAreRetained() throws SQLException {
		for (String value : new String[] { null, "NONE", "none" }) {
			assertNull(read("NONE", false, null, null, value).getPartitioning().getPartitions().get(0).getSpecifics().get("LOGGING"));
		}
		for (String value : new String[] { "YES", "NO" }) {
			assertEquals(value, read("NONE", false, null, null, value).getPartitioning().getPartitions().get(0).getSpecifics().get("LOGGING"));
		}
	}
	@Test
	void disabledAndEnabledCatalogValuesRetainTheirMeaning() throws SQLException {
		assertFalse(read("DISABLED").getPartitioning().getPartitions().get(0).isCompression());
		assertFalse(read("disabled").getPartitioning().getPartitions().get(0).isCompression());
		assertTrue(read("ENABLED").getPartitioning().getPartitions().get(0).isCompression());
	}
}
