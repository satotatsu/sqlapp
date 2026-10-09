/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.SchemaUtils;

class PostgresRoutineBodyReplacementTest {
	private String encode(String value) { return java.util.Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

	private Function original() {
		Function function = new Function("f").setSchemaName("s").setLanguage("sql").setStatement(" SELECT 1 ");
		function.getReturning().setDataType(DataType.INT);
		function.getSpecifics().put("POSTGRES_ROUTINE_DDL_BASE64", encode("CREATE OR REPLACE FUNCTION s.f()\n RETURNS integer\n LANGUAGE sql\n COST 123\n SET search_path TO 'pg_catalog'\nAS $function$ SELECT 1 $function$\n"));
		return function;
	}

	@Test
	void replacesBodyAfterXmlWithoutLosingUnmodeledClauses() throws Exception {
		Function original = SchemaUtils.readXml(new java.io.StringReader(original().asXml()));
		Function target = original.clone().setStatement(" SELECT 2 /* $sqlapp_body$ */ ");
		String ddl = PostgresRoutineBodyReplacement.replace(original, target, "s.f", false);
		assertTrue(ddl.contains("COST 123\n SET search_path TO 'pg_catalog'"));
		assertTrue(ddl.contains("AS $sqlapp_body_0$ SELECT 2 /* $sqlapp_body$ */ $sqlapp_body_0$"));
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		var operations = registry.createSql(original.diff(target));
		assertEquals(2, operations.size());
		assertEquals(ddl, operations.get(0).getSqlText());
		assertTrue(operations.get(1).getSqlText().contains("VOLATILE CALLED ON NULL INPUT SECURITY INVOKER"));
	}

	@Test
	void ignoresOnlySupplementalTemplateDifferencesAndRejectsOtherVendorChanges() {
		Function original = original(); Function target = original.clone();
		target.getSpecifics().put("POSTGRES_ROUTINE_DDL_BASE64", encode("refreshed"));
		var registry = DialectResolver.getInstance().getDialect("postgres", 15, 0, null).createSqlFactoryRegistry();
		assertTrue(registry.createSql(original.diff(target)).isEmpty());
		target.getSpecifics().put("ROUTINE_KIND", "PROCEDURE");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target)));
	}

	@Test
	void rejectsStaleBodyWrongIdentityExternalLanguageAndExtraStatements() {
		Function original = original(); Function target = original.clone().setStatement(" SELECT 2 ");
		assertThrows(UnsupportedOperationException.class, () -> PostgresRoutineBodyReplacement.replace(original.clone().setStatement("different"), target, "s.f", false));
		assertThrows(UnsupportedOperationException.class, () -> PostgresRoutineBodyReplacement.replace(original, target, "s.other", false));
		assertThrows(UnsupportedOperationException.class, () -> PostgresRoutineBodyReplacement.replace(original.clone().setLanguage("c"), target, "s.f", false));
		Function extra = original.clone(); extra.getSpecifics().put("POSTGRES_ROUTINE_DDL_BASE64", encode(new String(java.util.Base64.getDecoder().decode(original.getSpecifics().get("POSTGRES_ROUTINE_DDL_BASE64")), java.nio.charset.StandardCharsets.UTF_8) + "DROP TABLE victim;"));
		assertThrows(UnsupportedOperationException.class, () -> PostgresRoutineBodyReplacement.replace(extra, target, "s.f", false));
	}
}
