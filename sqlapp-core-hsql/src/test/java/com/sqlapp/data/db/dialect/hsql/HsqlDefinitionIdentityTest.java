/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HsqlDefinitionIdentityTest {
	@Test
	void toleratesNumericDefaultScaleAndFormattingWithoutChangingExpressionSemantics() {
		assertTrue(HsqlDefinitionIdentity.equivalent("DEFAULT - 8.000 CHECK ( VALUE < 100 )", "default -8 CHECK(VALUE<100)"));
		assertTrue(HsqlDefinitionIdentity.equivalent("DEFAULT 8 /* explanation */ CONSTRAINT C CHECK(VALUE>0)", "DEFAULT 8.000 CONSTRAINT C CHECK (VALUE > 0)"));
		assertFalse(HsqlDefinitionIdentity.equivalent("CHECK (VALUE=1/2)", "CHECK (VALUE=1.0/2)"));
		assertFalse(HsqlDefinitionIdentity.equivalent("DEFAULT 'DEFAULT 8 CHECK'", "DEFAULT 'DEFAULT 8.000 CHECK'"));
		assertFalse(HsqlDefinitionIdentity.equivalent("DEFAULT 'a b'", "DEFAULT 'ab'"));
		assertFalse(HsqlDefinitionIdentity.equivalent("TYPE \"Case Name\"", "TYPE \"CASE NAME\""));
	}
}
