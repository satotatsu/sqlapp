/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.sybase.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;

class SybaseSourceTextTest {
	@Test
	void rejectsViewHeadersWithoutExecutableBody() {
		assertThrows(IllegalStateException.class, () -> SybaseSourceText.viewStatement("CREATE VIEW v AS "));
		assertThrows(IllegalStateException.class,
				() -> SybaseSourceText.viewStatement("CREATE VIEW v AS /* missing */"));
	}

	@Test
	void rejectsNumberedGroupsAndPreservesFirstGroupSource() throws Exception {
		assertThrows(UnsupportedOperationException.class,
				() -> SybaseSourceText.required(
						SybaseReaderTestSupport
								.row(java.util.Map.of("source_group", 2, "definition", "CREATE PROC p;2 AS SELECT 2")),
						"definition", "p"));
		assertEquals(" source ",
				SybaseSourceText.required(
						SybaseReaderTestSupport.row(java.util.Map.of("source_group", 1, "definition", " source ")),
						"definition", "p"));
	}

	@ParameterizedTest
	@CsvSource(value = { "DEFAULT (42)|(42)", "CREATE DEFAULT [AS] AS 'hello world'|'hello world'",
			"CREATE OR REPLACE DEFAULT dbo.x AS (1 + 2)|(1 + 2)",
			"'plain'|'plain'" }, delimiter = '|', quoteCharacter = '"')
	void extractsDefaultExpression(String source, String expected) {
		assertEquals(expected, SybaseSourceText.defaultExpression(source));
	}

	@Test
	void masksNestedCommentsStringsAndEscapedIdentifiersWithoutChangingOffsets() {
		final String source = "/* AS /* nested */ */ CREATE VIEW [AS]]name] AS SELECT 'AS''FOR UPDATE',\"AS\"";
		final String masked = SybaseSourceText.mask(source);
		assertEquals(source.length(), masked.length());
		assertFalse(masked.contains("nested"));
		assertFalse(masked.contains("FOR UPDATE"));
		assertEquals("SELECT 'AS''FOR UPDATE',\"AS\"", SybaseSourceText.viewStatement(source));
		assertThrows(IllegalStateException.class, () -> SybaseSourceText.defaultExpression("CREATE DEFAULT x"));
	}

	@Test
	void createsCompleteRoutineDefinitionsAndRejectsMissingOrWrongKinds() throws Exception {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var procedure = new Procedure("p");
		procedure.setDefinition("CREATE PROCEDURE dbo.p @n INT AS SELECT @n");
		final Procedure xml = SchemaUtils.readXml(new java.io.StringReader(procedure.asXml()));
		assertEquals("CREATE PROCEDURE dbo.p @n INT AS SELECT @n",
				registry.createSql(xml, SqlType.CREATE).get(0).getSqlText());
		var function = new Function("f");
		function.setDefinition("CREATE FUNCTION dbo.f() RETURNS INT AS BEGIN RETURN 1 END");
		assertTrue(registry.createSql(function, SqlType.CREATE).get(0).getSqlText().startsWith("CREATE FUNCTION"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Trigger("missing"), SqlType.CREATE));
		function.setDefinition("CREATE PROCEDURE dbo.f AS SELECT 1");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(function, SqlType.CREATE));
	}
}
