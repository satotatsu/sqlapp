/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DomainChecksCopyTest {
	@Test
	void mergingExistingDomainReplacesChecksWithoutDuplicatesOrStaleEntries() {
		var schema = new Schema("APP");
		var original = new Domain("D"); original.getChecks().add(new DomainCheckConstraint("C", "VALUE>0"));
		schema.getDomains().add(original);
		var incoming = original.clone(); incoming.getChecks().get("C").setExpression("VALUE>1");
		incoming.getChecks().add(new DomainCheckConstraint("SECOND", "VALUE<10"));
		schema.getDomains().addAll(java.util.List.of(incoming));
		assertEquals(2, schema.getDomains().get("D").getChecks().size());
		assertEquals("VALUE>1", schema.getDomains().get("D").getChecks().get("C").getExpression());
		incoming.getChecks().clear();
		assertEquals(2, schema.getDomains().get("D").getChecks().size());
		schema.getDomains().addAll(java.util.List.of(incoming));
		assertTrue(schema.getDomains().get("D").getChecks().isEmpty());
		assertThrows(IllegalArgumentException.class, () -> {
			original.getChecks().add(new DomainCheckConstraint("SAME", "VALUE>0"));
			original.getChecks().add(new DomainCheckConstraint("SAME", "VALUE>0"));
		});
	}
}
