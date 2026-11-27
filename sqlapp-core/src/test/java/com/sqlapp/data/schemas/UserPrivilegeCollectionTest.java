/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserPrivilegeCollectionTest {
	private UserPrivilege privilege(String grantor, String kind) {
		var privilege = new UserPrivilege().setGranteeName("PRINCIPAL").setGrantorName(grantor).setPrivilege("AUTHORITY");
		privilege.getSpecifics().put("principalKind", kind);
		return privilege;
	}

	@Test
	void bulkAdditionPreservesEachGrantsMetadataAndParent() {
		Catalog catalog = new Catalog();
		var grants = List.of(privilege("A", "USER"), privilege("B", "USER"), privilege("A", "ROLE"));
		assertTrue(catalog.getUserPrivileges().addAll(grants));
		assertEquals(3, catalog.getUserPrivileges().size());
		for (var privilege : catalog.getUserPrivileges()) assertSame(catalog.getUserPrivileges(), privilege.getParent());
		assertFalse(catalog.getUserPrivileges().addAll(List.of()));
	}

	@Test
	void catalogAndCollectionCloningKeepAllGrantorsAndSupplementalIdentities() {
		Catalog catalog = new Catalog();
		catalog.getUserPrivileges().addAll(List.of(privilege("A", "USER"), privilege("B", "USER"), privilege("A", "ROLE")));
		var clone = catalog.clone();
		assertEquals(3, clone.getUserPrivileges().size());
		assertEquals(catalog, clone);
		assertEquals(catalog.getUserPrivileges(), catalog.getUserPrivileges().clone());
		clone.getUserPrivileges().getFirst().getSpecifics().put("principalKind", "CHANGED");
		assertEquals("USER", catalog.getUserPrivileges().getFirst().getSpecifics().get("principalKind"));
	}

	@Test
	void xmlRoundTripRetainsEachGrantRecord() throws Exception {
		Catalog catalog = new Catalog();
		catalog.getUserPrivileges().addAll(List.of(privilege("A", "USER"), privilege("B", "USER")));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(2, restored.getUserPrivileges().size());
	}

	@Test
	void indexedBulkAdditionPreservesDistinctRowsInOrder() {
		var privileges = new Catalog().getUserPrivileges();
		privileges.add(privilege("A", "USER"));
		assertTrue(privileges.addAll(0, List.of(privilege("B", "USER"), privilege("C", "ROLE"))));
		assertEquals(List.of("B", "C", "A"), privileges.stream().map(UserPrivilege::getGrantorName).toList());
		assertThrows(IndexOutOfBoundsException.class, () -> privileges.addAll(5, List.of()));
	}
}
