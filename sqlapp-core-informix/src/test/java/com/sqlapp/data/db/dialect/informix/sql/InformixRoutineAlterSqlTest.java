/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InformixRoutineAlterSqlTest {
	@Test
	void distinguishesStatementsFromQuotedNamesPathsAndComments() {
		final String first = "ALTER SPECIFIC FUNCTION \"ALTER\" WITH (MODIFY EXTERNAL NAME 'path; ALTER FUNCTION ''quoted''(x)')";
		final String second = "ALTER PROCEDURE p(INTEGER) WITH (MODIFY VARIANT)";
		assertEquals(java.util.List.of(first + " /* ALTER ROUTINE fake() */ { ALTER ignored }", second),
				InformixRoutineAlterSql.split(first + " /* ALTER ROUTINE fake() */ { ALTER ignored } " + second));
		assertEquals(1, InformixRoutineAlterSql.split(first + " -- ALTER fake\n").size());
	}

	@Test
	void rejectsIncompleteAndUnexpectedHistory() {
		assertTrue(InformixRoutineAlterSql.split(null).isEmpty());
		for (String text : java.util.List.of(" ", "SELECT 1", "ALTER TABLE x ADD col INTEGER",
				"ALTER FUNCTION f() WITH (", "ALTER FUNCTION f())",
				"ALTER FUNCTION f() WITH (MODIFY VARIANT) -- lost newline   ALTER FUNCTION f() WITH (MODIFY NOT VARIANT)"))
			assertThrows(IllegalArgumentException.class, () -> InformixRoutineAlterSql.split(text), text);
	}
}
