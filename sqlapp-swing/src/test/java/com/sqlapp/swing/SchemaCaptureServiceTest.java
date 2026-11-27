/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class SchemaCaptureServiceTest {
	@TempDir
	Path directory;

	@Test
	void capturesCompleteMetadataWithoutRowsAndFeedsExistingComparison() throws Exception {
		final String url = "jdbc:hsqldb:mem:capture_" + UUID.randomUUID();
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
			statement.execute("INSERT INTO ITEMS VALUES(1,'row-value-must-not-be-exported')");
			statement.execute("CREATE VIEW ITEM_NAMES AS SELECT ID, NAME FROM ITEMS");
			statement.execute("CREATE SEQUENCE ITEM_SEQUENCE START WITH 10");
			final Path existing = Files.writeString(directory.resolve("schema.xml"), "keep this");
			final var service = new SchemaCaptureService();
			final var spec = new PlanningJdbc.Spec(url, "SA", "");
			final Path before = service.capture(spec, new char[0], "", directory);
			try (var input = Files.newInputStream(before)) {
				final var schema = (Schema) SchemaUtils.readXml(input);
				assertEquals("PUBLIC", schema.getName());
				assertNotNull(schema.getProductName());
				assertNotNull(schema.getTables().get("ITEMS"));
				assertNotNull(schema.getViews().get("ITEM_NAMES"));
				assertNotNull(schema.getSequences().get("ITEM_SEQUENCE"));
				assertTrue(schema.getTables().get("ITEMS").getRows().isEmpty());
			}
			assertFalse(Files.readString(before).contains("row-value-must-not-be-exported"));
			assertEquals(0, new SchemaComparisonService().inspect(before, before).statements());
			statement.execute("ALTER TABLE ITEMS ADD TAG VARCHAR(20)");
			final Path after = service.capture(spec, new char[0], "PUBLIC", directory);
			assertNotEquals(before, after);
			assertEquals("keep this", Files.readString(existing));
			final var compared = new SchemaComparisonService().inspect(before, after);
			assertTrue(compared.statements() > 0);
			assertTrue(compared.sqlDraft().contains("TAG"));
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				rows.next();
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	@Test
	void rejectsUnresolvedSchemaAndInvalidWorkspaceAndRedactsPasswords() throws Exception {
		final String url = "jdbc:hsqldb:mem:capture_errors_" + UUID.randomUUID();
		final var service = new SchemaCaptureService();
		try (var connection = DriverManager.getConnection(url, "SA", "")) {
			assertThrows(IllegalArgumentException.class,
					() -> service.capture(new PlanningJdbc.Spec(url, "SA", ""), new char[0], "MISSING", directory));
		}
		final char[] secret = "never-connect".toCharArray();
		assertThrows(IllegalArgumentException.class, () -> service
				.capture(new PlanningJdbc.Spec("jdbc:invalid", "", ""), secret, "", directory.resolve("absent")));
		assertArrayEquals(new char[secret.length], secret);
		final char[] driverSecret = "test-only-secret".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> service.capture(new PlanningJdbc.Spec("jdbc:unused", "", "missing.test-only-secret"),
						driverSecret, "", directory));
		assertFalse(failure.getMessage().contains("test-only-secret"));
		assertNull(failure.getCause());
		assertArrayEquals(new char[driverSecret.length], driverSecret);
		try (var files = Files.list(directory)) {
			assertEquals(0, files.count());
		}
	}
}
