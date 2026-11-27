/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IdentityColumnOptionsTest {
	@Test
	void booleanOptionsDoNotDependOnIdentitySetterOrder() {
		Column column = new Column("ID").setIdentityOrder(true).setIdentityCycle(true).setIdentityCache(false);
		assertFalse(column.isIdentity());
		column.setIdentity(true);
		assertTrue(column.isIdentityOrder()); assertTrue(column.isIdentityCycle()); assertFalse(column.isIdentityCache());
	}
	@Test
	void identityOptionsSurviveXmlAndClone() throws Exception {
		Column column = new Column("ID").setIdentity(true).setIdentityOrder(true).setIdentityCycle(true).setIdentityCache(false);
		Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
		assertTrue(restored.isIdentity()); assertTrue(restored.isIdentityOrder()); assertTrue(restored.isIdentityCycle()); assertFalse(restored.isIdentityCache());
		Column clone = restored.clone(); clone.setIdentityOrder(false);
		assertTrue(restored.isIdentityOrder()); assertFalse(clone.isIdentityOrder());
	}
}
