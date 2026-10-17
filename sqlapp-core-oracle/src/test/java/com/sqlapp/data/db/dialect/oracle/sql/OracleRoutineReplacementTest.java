/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;

class OracleRoutineReplacementTest {
	@Test void functionReplacementPreservesTheBodyAndQualifiesTheOwner() {
		Function before = new Function("F").setSchemaName("APP"); before.setDefinition("CREATE OR REPLACE FUNCTION F RETURN NUMBER IS BEGIN RETURN 1; END F;");
		Function after = before.clone(); after.setDefinition("CREATE OR REPLACE FUNCTION F(p NUMBER DEFAULT 42) RETURN NUMBER AUTHID CURRENT_USER IS BEGIN RETURN p; END F;");
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.oracle10gDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			var operations = registry.getSqlFactory(before.diff(after)).createDiffSql(before.diff(after));
			assertEquals(1, operations.size()); assertEquals(SqlType.ALTER, operations.get(0).getSqlType());
			assertEquals("CREATE OR REPLACE FUNCTION APP.F(p NUMBER DEFAULT 42) RETURN NUMBER AUTHID CURRENT_USER IS BEGIN RETURN p; END F;", operations.get(0).getSqlText());
		}
	}
	@Test void procedureReplacementSupportsLegacyHeadersAndQuotedNames() {
		Procedure before = new Procedure("my.proc").setSchemaName("my owner"); before.setDefinition("PROCEDURE \"my.proc\" IS BEGIN NULL; END;");
		Procedure after = before.clone(); after.setDefinition("PROCEDURE \"my.proc\" IS BEGIN DBMS_OUTPUT.PUT_LINE('value'); END;");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		String ddl = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).get(0).getSqlText();
		assertTrue(ddl.startsWith("CREATE OR REPLACE PROCEDURE \"my owner\".\"my.proc\""), ddl);
	}
	@Test void staleDefinitionAndMismatchedHeadersAreRejected() {
		Function before = new Function("F").setSchemaName("APP"); before.setDefinition("CREATE FUNCTION F RETURN NUMBER IS BEGIN RETURN 1; END;");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		Function stale = before.clone(); stale.setStatement("BEGIN RETURN 2; END;");
		assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(stale, SqlType.ALTER).createDiffSql(before.diff(stale)));
		for (String header : java.util.List.of("FUNCTION OTHER", "FUNCTION OTHER_SCHEMA.F", "PROCEDURE F")) {
			Function target = before.clone(); target.setDefinition("CREATE " + header + " RETURN NUMBER IS BEGIN RETURN 2; END;");
			assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(target, SqlType.ALTER).createDiffSql(before.diff(target)));
		}
	}
	@Test void unchangedAndRenamedRoutinesHaveSafeOutcomes() {
		Procedure before = new Procedure("P"); before.setDefinition("PROCEDURE P IS BEGIN NULL; END;");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		assertTrue(registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(before.clone())).isEmpty());
		Procedure renamed = before.clone(); renamed.setName("OTHER"); renamed.setDefinition("PROCEDURE OTHER IS BEGIN NULL; END;");
		assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(renamed, SqlType.ALTER).createDiffSql(before.diff(renamed)));
	}
}
