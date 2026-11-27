/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcConnectionTestServiceTest {
	@Test
	void testsEmbeddedConnectionWithoutChangingRowsAndClearsPasswordCopy() throws Exception {
		final String url = "jdbc:hsqldb:mem:connection_test_" + UUID.randomUUID();
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ITEMS(ID INTEGER)");
			statement.execute("INSERT INTO ITEMS VALUES(1)");
			final char[] password = new char[0];
			final String product = new JdbcConnectionTestService().test(new PlanningJdbc.Spec(url, "SA", ""), password);
			assertTrue(product.contains("HSQL"));
			assertTrue(product.contains("2.7"));
			assertFalse(product.contains(url));
			assertArrayEquals(new char[0], password);
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				rows.next();
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	@Test
	void driverFailureRedactsSecretAndDiscardsRawCause() {
		final String secret = "test-only-secret";
		final char[] password = secret.toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class, () -> new JdbcConnectionTestService()
				.test(new PlanningJdbc.Spec("jdbc:unused", "", "missing.driver." + secret), password));
		assertFalse(failure.getMessage().contains(secret));
		assertTrue(failure.getMessage().contains("[redacted]"));
		assertNull(failure.getCause());
		assertArrayEquals(new char[password.length], password);
	}
}
