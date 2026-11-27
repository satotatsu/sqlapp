/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class DomainChecksTest {
	@Test
	void xmlCloneOwnershipAndDifferencesRetainNamedChecks() throws Exception {
		Domain domain = new Domain("RANGE").setDataType(DataType.INT);
		domain.getChecks().add(new DomainCheckConstraint("LOWER", "VALUE > 0")
				.setDeferrability(Deferrability.InitiallyDeferred).setEnable(false));
		domain.getChecks().add(new DomainCheckConstraint("UPPER", "VALUE < 100"));
		Domain restored = SchemaUtils.readXml(new StringReader(domain.asXml()));
		assertEquals(domain, restored);
		assertEquals(2, restored.getChecks().size());
		assertSame(restored, restored.getChecks().getParent());
		assertSame(restored.getChecks(), restored.getChecks().get(0).getParent());
		assertFalse(restored.getChecks().get(0).isEnable());
		assertEquals(Deferrability.InitiallyDeferred, restored.getChecks().get(0).getDeferrability());
		Domain clone = domain.clone();
		assertEquals(domain, clone);
		assertNotSame(domain.getChecks().get(0), clone.getChecks().get(0));
		clone.getChecks().get(0).setExpression("VALUE > 1");
		assertNotEquals(domain, clone);
		assertNotEquals(State.Unchanged, domain.diff(clone).getState());
		assertEquals("VALUE > 0", domain.getChecks().get(0).getExpression());
		DomainCheckConstraint standalone = SchemaUtils.readXml(new StringReader(domain.getChecks().get(0).asXml()));
		assertEquals("VALUE > 0", standalone.getExpression());
	}

	@Test
	void legacySingleCheckXmlRemainsConcise() throws Exception {
		Domain domain = new Domain("POSITIVE").setDataType(DataType.INT).setCheck("VALUE > 0");
		assertFalse(domain.asXml().contains("<checks"));
		Domain restored = SchemaUtils.readXml(new StringReader(domain.asXml()));
		assertEquals("VALUE > 0", restored.getCheck());
		assertTrue(restored.getChecks().isEmpty());
	}

	@Test
	void namesMustBePresentAndUniqueWhileQuotedCaseRemainsDistinct() {
		Domain domain = new Domain("D");
		assertThrows(IllegalArgumentException.class, () -> domain.getChecks().add(new DomainCheckConstraint(null, "VALUE > 0")));
		domain.getChecks().add(new DomainCheckConstraint("CK", "VALUE > 0"));
		assertThrows(IllegalArgumentException.class, () -> domain.getChecks().add(new DomainCheckConstraint("CK", "VALUE > 1")));
		domain.getChecks().add(new DomainCheckConstraint("ck", "VALUE < 100"));
		assertEquals(2, domain.getChecks().size());
	}
}
