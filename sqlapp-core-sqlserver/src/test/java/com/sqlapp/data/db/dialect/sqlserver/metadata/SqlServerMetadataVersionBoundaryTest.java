/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SqlServerMetadataVersionBoundaryTest {
	private String resource(String name) throws Exception {
		try (var input = SqlServer2005TableReader.class.getResourceAsStream(name)) {
			assertNotNull(input);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void changeTrackingIsProjectedAsBooleanInsteadOfObjectId() throws Exception {
		for (String version : new String[] { "2008", "2012", "2014", "2016", "2017", "2019", "2022" }) {
			String query = resource("tables" + version + ".sql");
			assertTrue(query
					.contains("CAST(CASE WHEN ctt.object_id IS NULL THEN 0 ELSE 1 END AS bit) AS has_change_tracking"));
		}
	}

	@Test
	void compressionDelayOnlyReferencesCatalogColumnFrom2016() throws Exception {
		for (String version : new String[] { "2008", "2012", "2014" })
			assertFalse(resource("tables" + version + ".sql").contains("idx.compression_delay"));
		for (String version : new String[] { "2016", "2017", "2019", "2022" })
			assertTrue(resource("tables" + version + ".sql").contains("idx.compression_delay"));
	}

	@Test
	void partitionStatisticsDoNotIncludeSecondaryIndexesOrLoseLobStorage() throws Exception {
		String query = resource("partitions2005.sql");
		assertTrue(query.contains("WHERE i.[type] <= 1"));
		assertTrue(query.contains("p.hobt_id ELSE p.partition_id"));
		assertTrue(query.contains("LEFT JOIN sys.allocation_units"));
		assertFalse(query.contains("column_name"));
	}

	@Test
	void temporalCatalogReferencesAreIsolatedTo2016AndLater() throws Exception {
		for (String version : new String[] { "2008", "2012", "2014" })
			assertFalse(resource("tables" + version + ".sql").contains("sys.periods"));
		for (String version : new String[] { "2016", "2017", "2019", "2022" }) {
			String sql = resource("tables" + version + ".sql");
			assertTrue(sql.contains("sys.periods"));
			assertTrue(sql.contains("t.history_table_id"));
			assertTrue(sql.contains("period_start_column"));
			assertTrue(sql.contains("period_end_column"));
		}
		assertTrue(resource("columns2016.sql").contains("c.is_hidden"));
		assertFalse(resource("columns2008.sql").contains("c.is_hidden"));
	}

	@Test
	void temporalSelfJoinsQualifyTableStorageColumns() throws Exception {
		for (String version : new String[] { "2016", "2017", "2019", "2022" }) {
			String sql = resource("tables" + version + ".sql");
			assertTrue(sql.contains(", t.text_in_row_limit"));
			assertTrue(sql.contains(", t.large_value_types_out_of_row"));
		}
	}

	@Test
	void retentionColumnsAndReaderAreIntroducedOnlyIn2017() throws Exception {
		assertFalse(resource("tables2016.sql").contains("history_retention_period"));
		for (String version : new String[] { "2017", "2019", "2022" }) {
			assertTrue(resource("tables" + version + ".sql").contains("t.history_retention_period"));
			assertTrue(resource("tables" + version + ".sql").contains("t.history_retention_period_unit_desc"));
		}
		var resolver = com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver.getInstance();
		assertEquals(SqlServer2016TableReader.class,
				resolver.getDialect(13, 0).getCatalogReader().getSchemaReader().getTableReader().getClass());
		assertEquals(SqlServer2017TableReader.class,
				resolver.getDialect(14, 0).getCatalogReader().getSchemaReader().getTableReader().getClass());
		assertInstanceOf(SqlServer2017TableReader.class,
				resolver.getDialect(15, 0).getCatalogReader().getSchemaReader().getTableReader());
	}

	@Test
	void readsOnlyDescriptionFromTheMatchingExtendedPropertyClass() throws Exception {
		for (String resource : new String[] { "tables2005", "tables2008", "tables2012", "tables2014", "tables2016",
				"tables2017", "tables2019", "tables2022", "columns2005", "columns2008", "columns2016",
				"typeColumns2008" }) {
			String sql = resource(resource + ".sql");
			int join = sql.indexOf("LEFT OUTER JOIN sys.extended_properties ex");
			assertTrue(join >= 0, resource);
			String predicate = sql.substring(join, sql.indexOf(")", join));
			boolean typeColumn = resource.equals("typeColumns2008");
			assertTrue(predicate.contains("ex.class = " + (typeColumn ? 8 : 1)), resource);
			assertTrue(predicate.contains("ex.name = N'MS_Description'"), resource);
			if (typeColumn)
				assertTrue(predicate.contains("t.user_type_id = ex.major_id"));
		}
	}

	@Test
	void readsGraphFlagsFrom2017AndTextDurabilityFrom2014() throws Exception {
		assertFalse(resource("tables2016.sql").contains("t.is_node"));
		for (String version : new String[] { "2017", "2019", "2022" }) {
			String sql = resource("tables" + version + ".sql");
			assertTrue(sql.contains("t.is_node"));
			assertTrue(sql.contains("t.is_edge"));
			assertTrue(sql.contains("t.durability_desc AS durability"));
		}
	}
}
