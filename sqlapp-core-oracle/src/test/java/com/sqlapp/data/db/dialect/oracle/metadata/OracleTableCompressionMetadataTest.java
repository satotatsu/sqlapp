/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;

class OracleTableCompressionMetadataTest {
	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return values.containsKey(key);
			}

			@Override
			public String getString(String key) {
				return values.get(key);
			}

			@Override
			public Object getObject(String key) {
				return values.get(key);
			}
		};
	}

	@Test
	void partitionDefaultsOverrideTableFlagsAndRetainCompressionMode() throws Exception {
		var reader = new Oracle11gR2TableReader(DialectHolder.oracle11gR2Dialect);
		for (String mode : new String[] { "BASIC", "ADVANCED" }) {
			var table = reader.createTable(row(Map.of("TABLE_NAME", "T", "OWNER", "APP", "PARTITION_COMPRESSION",
					"ENABLED", "PARTITION_COMPRESS_FOR", mode)));
			assertTrue(table.isCompression());
			assertEquals(mode, table.getCompressionType());
			assertEquals(mode, table.getSpecifics().get("COMPRESS_FOR"));
			var restored = com.sqlapp.data.schemas.SchemaUtils.<com.sqlapp.data.schemas.Table>readXml(
					new java.io.StringReader(table.asXml()));
			String ddl = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
					.createSql(restored, com.sqlapp.data.db.sql.SqlType.CREATE).get(0).getSqlText();
			assertTrue(ddl.contains(mode.equals("BASIC") ? "COMPRESS" : "ROW STORE COMPRESS ADVANCED"));
		}
		var disabled = reader.createTable(row(Map.of("TABLE_NAME", "T", "COMPRESSION", "ENABLED",
				"PARTITION_COMPRESSION", "DISABLED", "TABLE_COMPRESS_FOR", "ADVANCED")));
		assertFalse(disabled.isCompression());
		assertNull(disabled.getCompressionType());
		var heap = reader.createTable(row(Map.of("TABLE_NAME", "T", "COMPRESSION", "ENABLED", "PARTITION_COMPRESSION",
				"NONE", "TABLE_COMPRESS_FOR", "ADVANCED")));
		assertTrue(heap.isCompression());
		assertEquals("ADVANCED", heap.getCompressionType());
	}

	@Test
	void partitionAndChildCompressionModesUseCommonFieldWithoutInventingOldCatalogValues() throws Exception {
		var reader = new OracleTableReader(DialectHolder.oracle26aiDialect);
		for (String mode : new String[] { "BASIC", "ADVANCED" }) {
			for (com.sqlapp.data.schemas.AbstractPartition<?> part : java.util.List.<com.sqlapp.data.schemas.AbstractPartition<?>>of(
					new com.sqlapp.data.schemas.Partition("P"), new com.sqlapp.data.schemas.SubPartition("S"))) {
				reader.readPartitionCompression(row(Map.of("COMPRESSION", "ENABLED", "COMPRESS_FOR", mode)), part);
				assertTrue(part.isCompression());
				assertEquals(mode, part.getCompressionType());
				reader.readPartitionCompression(row(Map.of("COMPRESSION", "NONE")), part);
				assertFalse(part.isCompression());
				assertNull(part.getCompressionType());
			}
		}
		var older = new OracleTableReader(DialectHolder.oracle10gDialect);
		var part = new com.sqlapp.data.schemas.Partition("P");
		older.readPartitionCompression(new ExResultSet(null) {
			@Override
			public boolean contains(String key) {
				return "COMPRESSION".equals(key);
			}

			@Override
			public String getString(String key) throws java.sql.SQLException {
				if (!contains(key))
					throw new java.sql.SQLException("Column absent in old catalog: " + key);
				return "ENABLED";
			}
		}, part);
		assertTrue(part.isCompression());
		assertNull(part.getCompressionType());
	}

	@Test
	void compressionCatalogJoinIsLimitedToNewerReader() {
		assertEquals(Oracle11gTableReader.class,
				DialectHolder.oracle11gDialect.getCatalogReader().getSchemaReader().getTableReader().getClass());
		assertEquals(Oracle11gR2TableReader.class,
				DialectHolder.oracle11gR2Dialect.getCatalogReader().getSchemaReader().getTableReader().getClass());
		var older = new Oracle11gTableReader(DialectHolder.oracle11gDialect);
		var newer = new Oracle11gR2TableReader(DialectHolder.oracle11gR2Dialect);
		String oldSql = older.getSqlSqlNode(new ProductVersionInfo()).toString();
		String newSql = newer.getSqlSqlNode(new ProductVersionInfo()).toString();
		assertFalse(oldSql.contains("DEF_COMPRESS_FOR"));
		assertTrue(newSql.contains("DEF_COMPRESS_FOR"));
		assertTrue(newSql.contains("LEFT OUTER JOIN ALL_PART_TABLES"));
	}
}
