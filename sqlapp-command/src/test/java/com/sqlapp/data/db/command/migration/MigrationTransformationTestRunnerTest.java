/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.migration.MigrationTransformationTest;

class MigrationTransformationTestRunnerTest {

	@Test
	void evaluatesInlineFixtureThroughCommentTemplate() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:transform_test", "SA", "")) {
			final String sql = """
					SELECT ID, UPPER(NAME) AS NAME
					FROM (VALUES (1, CAST('alice' AS VARCHAR(20))), (2, CAST('bob' AS VARCHAR(20)))) AS FIXTURE(ID, NAME)
					WHERE ID IN /*ids*/(1)
					""";
			final var passing = new MigrationTransformationTest("upper-name", sql, Map.of("ids", List.of(1, 2)),
					List.of(List.of("2", "BOB"), List.of("1", "ALICE")), false);
			final var failing = new MigrationTransformationTest("wrong-name", sql, Map.of("ids", List.of(1)),
					List.of(List.of("1", "BOB")), true);

			final var results = MigrationTransformationTestRunner.run(connection, List.of(passing, failing));

			assertTrue(results.get(0).match(), results.get(0).toString());
			assertFalse(results.get(1).match());
		}
	}
}
