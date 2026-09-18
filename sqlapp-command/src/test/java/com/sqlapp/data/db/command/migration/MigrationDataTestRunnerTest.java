/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.MigrationDataTest;

class MigrationDataTestRunnerTest {

	@Test
	void runsGeneratedAndCommentTemplateTests() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration_data_test", "SA", "")) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE CUSTOMER(ID BIGINT, STATUS VARCHAR(20))");
				statement.execute("INSERT INTO CUSTOMER VALUES (1, 'ACTIVE'), (2, NULL), (3, 'INVALID')");
			}
			final var notNull = new MigrationDataTest("customer-status-not-null", MigrationDataTest.Type.NOT_NULL,
					MigrationDataTest.Severity.ERROR, null, null, "CUSTOMER", List.of("STATUS"), null, Map.of(), 0, 0,
					10);
			final var accepted = new MigrationDataTest("customer-status-accepted", MigrationDataTest.Type.CUSTOM_SQL,
					MigrationDataTest.Severity.ERROR, null, null, "CUSTOMER", List.of("STATUS"),
					"SELECT ID, STATUS FROM CUSTOMER WHERE STATUS NOT IN /*values*/('ACTIVE')",
					Map.of("values", List.of("ACTIVE", "INACTIVE")), 0, 0, 10);

			final var results = MigrationDataTestRunner.run(connection, List.of(notNull, accepted));

			assertEquals(1, results.get(0).failures());
			assertEquals(MigrationDataTestResult.Status.ERROR, results.get(0).status());
			assertEquals(1, results.get(1).failures());
			assertEquals(List.of(3L, "INVALID"), results.get(1).samples().getFirst());
		}
	}
}
