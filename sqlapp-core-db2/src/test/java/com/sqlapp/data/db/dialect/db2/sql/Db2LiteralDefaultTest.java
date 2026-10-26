/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class Db2LiteralDefaultTest {
	@Test
	void literalDefaultsHaveNoObjectDependencies() {
		for (String expression : List.of("NULL", "(null)", "TRUE", "FALSE", "42", "(-1.25 + 4) / 2",
				"'F() -- not a reference'", "'it''s literal'", "/* one /* nested */ end */ (1+2) -- F()"))
			assertTrue(Db2LiteralDefault.isIndependent(expression), expression);
	}

	@Test
	void namesFunctionsSubqueriesAndStatementsCannotBeProvenIndependent() {
		for (String expression : List.of("", "/* only comment */", "F()", "APP.F(4)", "\"F\"", "V", "CURRENT DATE",
				"(SELECT 1 FROM T)", "1; DROP TABLE T", "NULLABLE", "FALSE_FN()", "1 + F()", "'F()' || V",
				"TRUE.FALSE"))
			assertFalse(Db2LiteralDefault.isIndependent(expression), expression);
		assertFalse(Db2LiteralDefault.isIndependent(null));
	}
}
