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
		for (String version : new String[] { "2008", "2012", "2014", "2016", "2019", "2022" }) {
			String query = resource("tables" + version + ".sql");
			assertTrue(query
					.contains("CAST(CASE WHEN ctt.object_id IS NULL THEN 0 ELSE 1 END AS bit) AS has_change_tracking"));
		}
	}

	@Test
	void compressionDelayOnlyReferencesCatalogColumnFrom2016() throws Exception {
		for (String version : new String[] { "2008", "2012", "2014" })
			assertFalse(resource("tables" + version + ".sql").contains("idx.compression_delay"));
		for (String version : new String[] { "2016", "2019", "2022" })
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
		for (String version : new String[] { "2016", "2019", "2022" }) {
			String sql = resource("tables" + version + ".sql");
			assertTrue(sql.contains("sys.periods"));
			assertTrue(sql.contains("t.history_table_id"));
			assertTrue(sql.contains("period_start_column"));
			assertTrue(sql.contains("period_end_column"));
		}
		assertTrue(resource("columns2016.sql").contains("c.is_hidden"));
		assertFalse(resource("columns2008.sql").contains("c.is_hidden"));
	}
}
