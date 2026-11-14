/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;

/**
 * 列ストアインデックスのメタデータ読み込み設定のテスト
 */
class SqlServerColumnStoreMetadataTest {

	private static String resource(final String name) throws IOException {
		try (InputStream in = SqlServer2005IndexReader.class.getResourceAsStream(name)) {
			assertNotNull(in, name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void columnStoreIndexesAreReadFrom2012() throws Exception {
		assertTrue(resource("indexes2008.sql").contains("WHERE i.type IN (1, 2, 3, 4)"));
		for (String version : new String[] { "2012", "2016", "2019", "2022" }) {
			final String sql = resource("indexes" + version + ".sql");
			assertTrue(sql.matches("(?s).*WHERE i\\.type IN \\(1, 2, 3, 4, 5, 6[,)].*"), version);
			assertTrue(sql.contains("pt.data_compression_desc AS data_compression"), version);
			assertTrue(sql.contains("pt.partition_number = 1"), version);
		}
		assertFalse(resource("indexes2012.sql").contains("compression_delay"));
		for (String version : new String[] { "2016", "2019", "2022" }) {
			assertTrue(resource("indexes" + version + ".sql").contains("i.compression_delay"), version);
		}
		assertFalse(resource("indexes2019.sql").contains("column_store_order_ordinal"));
		final String sql2022 = resource("indexes2022.sql");
		assertTrue(sql2022.contains("ic.column_store_order_ordinal"));
		assertTrue(
				sql2022.contains("ORDER BY SCHEMA_NAME(o.schema_id), o.name, i.name, ic.column_store_order_ordinal"));
	}

	@Test
	void indexReaderPerVersion() {
		final var resolver = SqlServerDialectResolver.getInstance();
		final Class<?>[] expected = { SqlServer2012IndexReader.class, SqlServer2014IndexReader.class,
				SqlServer2016IndexReader.class, SqlServer2016IndexReader.class, SqlServer2019IndexReader.class,
				SqlServer2022IndexReader.class };
		final int[] versions = { 11, 12, 13, 14, 15, 16 };
		for (int i = 0; i < versions.length; i++) {
			final var tableReader = (SqlServer2000TableReader) resolver.getDialect(versions[i], 0).getCatalogReader()
					.getSchemaReader().getTableReader();
			assertEquals(expected[i], tableReader.newIndexReader().getClass(), "version=" + versions[i]);
		}
	}
}
