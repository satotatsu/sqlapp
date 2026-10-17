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
		Table table = new Table("T"); table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		OracleTableReader reader = new OracleTableReader(DialectHolder.oracle10gDialect);
		reader.setPartition(null, new ExResultSet(null) {
			@Override public boolean contains(String key) { return java.util.Set.of("PARTITIONING_TYPE", "SUBPARTITIONING_TYPE", "PARTITION_NAME", "HIGH_VALUE", "COMPRESSION").contains(key); }
			@Override public String getString(String key) {
				return switch (key) {
					case "PARTITIONING_TYPE" -> "RANGE";
					case "SUBPARTITIONING_TYPE" -> "NONE";
					case "PARTITION_NAME" -> "P1";
					case "HIGH_VALUE" -> "100";
					case "COMPRESSION" -> compression;
					default -> null;
				};
			}
			@Override public Object getObject(String key) { return getString(key); }
		}, table);
		table.getPartitioning().getPartitioningColumns().add("ID");
		return table;
	}
	@Test void nullCompressionDoesNotInventACompressedPartition() throws SQLException {
		Table table = read(null); assertFalse(table.getPartitioning().getPartitions().get(0).isCompression());
		String sql = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("PARTITION BY RANGE"), sql); assertFalse(sql.contains("COMPRESS"), sql);
	}
	@Test void disabledAndEnabledCatalogValuesRetainTheirMeaning() throws SQLException {
		assertFalse(read("DISABLED").getPartitioning().getPartitions().get(0).isCompression());
		assertFalse(read("disabled").getPartitioning().getPartitions().get(0).isCompression());
		assertTrue(read("ENABLED").getPartitioning().getPartitions().get(0).isCompression());
	}
}
