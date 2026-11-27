/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;

class H2CatalogMetadataTest {
	@Test
	void readsCatalogsUsersAndRolesFromModernCatalog() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE USER READER PASSWORD 'x'");
			statement.execute("COMMENT ON USER READER IS 'read only'");
			statement.execute("CREATE ROLE AUDIT");
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();

			var catalogs = reader.getAll(connection);
			assertEquals(1, catalogs.size());
			assertEquals(connection.getCatalog(), catalogs.get(0).getName());

			var users = reader.getUserReader().getAll(connection);
			var admin = users.stream().filter(user -> "SA".equals(user.getName())).findFirst().orElseThrow();
			var ordinary = users.stream().filter(user -> "READER".equals(user.getName())).findFirst().orElseThrow();
			assertEquals(2, users.size());
			assertTrue(admin.isAdmin());
			assertFalse(ordinary.isAdmin());
			assertEquals("read only", ordinary.getRemarks());

			var roles = reader.getRoleReader().getAll(connection);
			assertTrue(roles.stream().anyMatch(role -> "AUDIT".equals(role.getName())));
		}
	}

	@Test
	void readsTheCompleteCatalogTree() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE ROLE AUDIT");
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY)");
			statement.execute("CREATE VIEW V AS SELECT ID FROM T");
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
			var catalogs = reader.getAllFull(connection);
			assertEquals(1, catalogs.size());
			var catalog = catalogs.get(0);
			var schema = catalog.getSchemas().get("PUBLIC");
			assertNotNull(schema.getTables().get("T"));
			assertFalse(schema.getViews().get("V").getStatement().isEmpty());
			assertNotNull(catalog.getSchemas().get("INFORMATION_SCHEMA").getViews().get("CHECK_CONSTRAINTS"));
			assertNotNull(catalog.getUsers().get("SA"));
			assertNotNull(catalog.getRoles().get("AUDIT"));
		}
	}
}
