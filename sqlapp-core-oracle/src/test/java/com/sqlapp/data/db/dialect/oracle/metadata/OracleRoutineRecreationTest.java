/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.SchemaUtils;

class OracleRoutineRecreationTest {
	@Test
	void originalSignatureAndBodySurviveAcrossVersions() throws Exception {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.oracle11gR2Dialect,
				DialectHolder.oracle19cDialect, DialectHolder.oracle21cDialect, DialectHolder.oracle23aiDialect,
				DialectHolder.oracle26aiDialect)) {
			for (Routine<?> routine : List.of(new Function("F"), new Procedure("P"))) {
				String source = routine instanceof Function
						? "FUNCTION F(p VARCHAR2 DEFAULT 'a IS b') RETURN VARCHAR2 AUTHID CURRENT_USER\nIS BEGIN RETURN p; END F;"
						: "PROCEDURE P(p NUMBER DEFAULT 42) AUTHID CURRENT_USER\nIS BEGIN NULL; END P;";
				OracleMetadataUtils.setRoutineDefinition(routine, List.of(source));
				String expected = "CREATE OR REPLACE " + source;
				assertEquals(expected,
						dialect.createSqlFactoryRegistry().createSql(routine, SqlType.CREATE).get(0).getSqlText());
				Routine<?> restored = SchemaUtils.readXml(new java.io.StringReader(routine.asXml()));
				assertEquals(expected,
						dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText());
				// Existing user-authored definitions without CREATE remain supported.
				routine.setDefinition(source);
				assertEquals(expected,
						dialect.createSqlFactoryRegistry().createSql(routine, SqlType.CREATE).get(0).getSqlText());
			}
		}
	}

	@Test
	void unavailableSourceFailsWithIdentity() {
		Procedure procedure = new Procedure("P").setSchemaName("APP");
		for (List<String> source : List.<List<String>>of(List.of(), List.of(" "))) {
			var error = assertThrows(IllegalStateException.class,
					() -> OracleMetadataUtils.setRoutineDefinition(procedure, source));
			assertTrue(error.getMessage().contains("APP.P"));
		}
		assertThrows(IllegalStateException.class, () -> OracleMetadataUtils.setRoutineDefinition(procedure, null));
	}
}
