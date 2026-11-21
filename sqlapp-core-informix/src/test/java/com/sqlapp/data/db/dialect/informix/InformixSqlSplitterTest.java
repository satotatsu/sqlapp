/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InformixSqlSplitterTest {
	private java.util.List<String> sql(String text) {
		return DialectHolder.defaultDialect.createSqlSplitter().parse(text).stream()
				.filter(s -> !s.getTextType().isComment()).map(s -> s.getText()).toList();
	}

	@Test
	void preservesProceduresFunctionsNestedBlocksAndFollowingStatements() {
		final String procedure = "CREATE PROCEDURE p() DEFINE x INTEGER; BEGIN LET x = 1; END; END PROCEDURE";
		final String function = "CREATE FUNCTION f() RETURNING INT; RETURN 1; END FUNCTION DOCUMENT 'usage; end function'";
		assertEquals(java.util.List.of(procedure, function, "SELECT 1 FROM systables"),
				sql(procedure + ";" + function + ";SELECT 1 FROM systables;"));
	}

	@Test
	void ignoresTerminatorsInsideLiteralsIdentifiersAndAllCommentForms() {
		final String routine = "CREATE OR REPLACE PROCEDURE \"p;\"() "
				+ "DEFINE x VARCHAR(40); LET x = 'it''s; END PROCEDURE'; "
				+ "{ END PROCEDURE; ' } /* END PROCEDURE; */ -- END PROCEDURE;\nEND PROCEDURE";
		assertEquals(java.util.List.of(routine, "SELECT 'x;y' FROM systables"),
				sql(routine + ";SELECT 'x;y' FROM systables;"));
	}

	@Test
	void supportsExternalRoutinesAndCommentOnlyInput() {
		final String external = "CREATE FUNCTION f(INT) RETURNS INT EXTERNAL NAME 'lib;f' LANGUAGE C";
		assertEquals(java.util.List.of(external, "SELECT 1"), sql(external + "; SELECT 1;"));
		assertTrue(sql("-- comment\n{ another; } /* comment */").isEmpty());
		// Leave validation of standalone quoted SQL to the execution layer.
		assertEquals(java.util.List.of("'literal'"), sql("'literal';"));
	}

	@Test
	void failsClearlyOnIncompleteRoutineOrLexicalInputAndCanBeReused() {
		final var splitter = DialectHolder.defaultDialect.createSqlSplitter();
		for (String invalid : java.util.List.of("CREATE PROCEDURE p() LET x=1;", "SELECT 'x", "/* x", "{ x"))
			assertThrows(IllegalArgumentException.class, () -> splitter.parse(invalid));
		assertEquals(1, splitter.parse("SELECT 1;").size());
	}
}
