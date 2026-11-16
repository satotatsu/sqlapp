/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.CascadeRule;

class Db2ForeignKeyRuleTest {

	@Test
	void catalogRuleCodesAreNotLost() {
		assertEquals(CascadeRule.None, Db2ForeignKeyConstraintReader.rule("A"));
		assertEquals(CascadeRule.Cascade, Db2ForeignKeyConstraintReader.rule("C"));
		assertEquals(CascadeRule.SetNull, Db2ForeignKeyConstraintReader.rule("N"));
		assertEquals(CascadeRule.Restrict, Db2ForeignKeyConstraintReader.rule("R"));
		assertEquals(CascadeRule.SetNull, Db2ForeignKeyConstraintReader.rule("N "));
		assertNull(Db2ForeignKeyConstraintReader.rule(null));
	}
}
