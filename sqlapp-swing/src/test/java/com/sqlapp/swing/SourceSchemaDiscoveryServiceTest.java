/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSchemaDiscoveryServiceTest {
	@TempDir
	java.nio.file.Path directory;

	@Test
	void selectedNonCurrentSchemaIsUsedByExistingDraftDiscovery() throws Exception {
		final String url = "jdbc:hsqldb:mem:selected_schema_" + UUID.randomUUID();
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA REPORTS AUTHORIZATION DBA");
			statement.execute("CREATE TABLE REPORTS.ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
			final var spec = new PlanningJdbc.Spec(url, "SA", "");
			final var names = new SourceSchemaDiscoveryService().list(spec, new char[0]);
			assertTrue(names.contains("REPORTS"));
			try (var files = java.nio.file.Files.list(directory)) {
				assertEquals(0, files.count());
			}
			final var draft = new DataMigrationDraftService().discover(spec, new char[0], "REPORTS", directory);
			assertEquals(1, draft.schema().tables().size());
			assertTrue(draft.schema().tables().getFirst().table().endsWith("REPORTS.ITEMS"));
			assertTrue(draft.schema().tables().getFirst().selectable());
		}
	}

	@Test
	void listsCanonicalSchemaNamesFromEmbeddedDatabase() throws Exception {
		final String url = "jdbc:hsqldb:mem:schemas_" + UUID.randomUUID();
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA REPORTS AUTHORIZATION DBA");
			statement.execute("CREATE SCHEMA \"販売\" AUTHORIZATION DBA");
			final var names = new SourceSchemaDiscoveryService().list(new PlanningJdbc.Spec(url, "SA", ""),
					new char[0]);
			assertTrue(names.contains("PUBLIC"));
			assertTrue(names.contains("REPORTS"));
			assertTrue(names.contains("販売"));
			assertEquals(names.stream().sorted().toList(), names);
			assertThrows(UnsupportedOperationException.class, () -> names.add("other"));
		}
	}

	@Test
	void failedDiscoveryRedactsPasswordAndClearsCopy() {
		final char[] password = "test-only-secret".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class, () -> new SourceSchemaDiscoveryService()
				.list(new PlanningJdbc.Spec("jdbc:unused", "", "missing.test-only-secret"), password));
		assertFalse(failure.getMessage().contains("test-only-secret"));
		assertTrue(failure.getMessage().contains("[redacted]"));
		assertNull(failure.getCause());
		assertArrayEquals(new char[password.length], password);
	}
}
