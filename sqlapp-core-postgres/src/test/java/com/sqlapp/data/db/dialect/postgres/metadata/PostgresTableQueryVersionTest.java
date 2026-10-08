package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PostgresTableQueryVersionTest {
	@Test
	void insertVacuumSettingsStartAtPostgres13() throws Exception {
		String legacy = query("tables100.sql");
		String modern = query("tables130.sql");
		for (String column : java.util.List.of("autovacuum_vacuum_insert_threshold", "autovacuum_vacuum_insert_scale_factor")) {
			assertFalse(legacy.contains(column), column);
			assertTrue(modern.contains(column), column);
		}
	}

	private String query(String name) throws Exception {
		try (var input = PostgresTableReader.class.getResourceAsStream(name)) {
			return new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
