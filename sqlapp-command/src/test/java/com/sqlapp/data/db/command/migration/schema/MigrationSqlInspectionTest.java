/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MigrationSqlInspectionTest {
	private final MigrationSqlInspection service = new MigrationSqlInspection();

	@Test
	void preservesQuotedSemicolonsAndSeparatesCommentsWithoutChangingSource() {
		final String sql = "-- comment\nSELECT ';';\nSELECT 2;";
		final var result = service.inspect(sql, "HSQL Database Engine", "2.7");
		assertEquals(2, result.sqlParts());
		assertEquals(1, result.commentParts());
		assertTrue(result.parts().stream().anyMatch(part -> part.text().contains("';'")));
		assertEquals("-- comment\nSELECT ';';\nSELECT 2;", sql);
		assertThrows(UnsupportedOperationException.class, () -> result.parts().clear());
		assertTrue(result.resolvedDialect().startsWith("Hsql"));
	}

	@Test
	void postgresFunctionDollarQuotedBodyStaysInOnePart() {
		final var result = service.inspect(
				"CREATE FUNCTION p() RETURNS void AS $$ BEGIN PERFORM 1; PERFORM 2; END $$ LANGUAGE plpgsql;\nSELECT 3;",
				"PostgreSQL", "15.0");
		assertEquals(2, result.sqlParts());
		assertTrue(result.parts().getFirst().text().contains("PERFORM 2;"));
	}

	@Test
	void mysqlDelimiterAndSqlServerRepeatedBatchesUseSharedSemantics() {
		final var mysql = service.inspect(
				"delimiter //\nCREATE PROCEDURE p()\nBEGIN\nSELECT 1;\nSELECT 2;\nEND//\ndelimiter ;\nSELECT 3;",
				"MySQL", "8.0");
		assertEquals(2, mysql.sqlParts());
		assertTrue(mysql.parts().stream().anyMatch(part -> part.text().contains("SELECT 2;")));
		final var sqlserver = service.inspect("SELECT 1;\nGO\nSELECT 2;\nGO 2\n", "Microsoft SQL Server", "15.0");
		assertEquals(3, sqlserver.sqlParts());
		assertEquals(sqlserver.parts().get(1).text(), sqlserver.parts().get(2).text());
	}

	@Test
	void keepsAnonymousPostgresDoBlocksTogether() {
		final var result = service.inspect("DO $$ BEGIN PERFORM 1; PERFORM 2; END $$;\nSELECT 3;", "PostgreSQL",
				"15.0");
		assertEquals(2, result.sqlParts());
	}

	@Test
	void requiresExplicitSupportedDialectAndValidVersionAndLimitsText() {
		assertThrows(IllegalArgumentException.class, () -> service.inspect("SELECT 1;", null, "15"));
		assertThrows(IllegalArgumentException.class, () -> service.inspect("SELECT 1;", "unknown", "15"));
		for (final String version : new String[] { "", "-1", "15.x", "999999999999" })
			assertThrows(IllegalArgumentException.class, () -> service.inspect("SELECT 1;", "PostgreSQL", version));
		assertThrows(IllegalArgumentException.class, () -> service.inspect("a".repeat(1_048_577), "PostgreSQL", "15"));
		assertThrows(IllegalArgumentException.class, () -> service.inspect("顧".repeat(400_000), "PostgreSQL", "15"));
		assertEquals(0, service.inspect("", "PostgreSQL", "15").sqlParts());
	}

	@Test
	void dialectQuotesAndGoInsideStringsRetainInspectionBoundaries() {
		final var mysql = service.inspect("SELECT `a;b` FROM t;SELECT 2;", "MySQL", "8.0");
		assertEquals(2, mysql.sqlParts());
		assertEquals("SELECT `a;b` FROM t", mysql.parts().getFirst().text());
		final var sqlserver = service.inspect("SELECT 'a\nGO\nb';\nGO\nSELECT 2;\nGO\n", "Microsoft SQL Server",
				"15.0");
		assertEquals(2, sqlserver.sqlParts());
		assertEquals("SELECT 'a\nGO\nb'", sqlserver.parts().getFirst().text());
		final var oracle = service.inspect("SELECT q'[it's;a]' FROM dual;SELECT 2 FROM dual;", "Oracle", "19.0");
		assertEquals(2, oracle.sqlParts());
	}

	@Test
	void sessionQuotingChangesFailWithConfigurationGuidance() {
		var mysql = assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> service.inspect("SET sql_mode='NO_BACKSLASH_ESCAPES';SELECT 2;", "MySQL", "8.0"));
		assertTrue(mysql.getMessage().contains("setNoBackslashEscapes"));
		var postgres = assertThrows(com.sqlapp.exceptions.InvalidTextException.class,
				() -> service.inspect("SET standard_conforming_strings=off;SELECT 2;", "PostgreSQL", "15.0"));
		assertTrue(postgres.getMessage().contains("setStandardConformingStrings"));
	}

}
