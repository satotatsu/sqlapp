/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;

class DomainColumnsTest {
	@Test
	void columnsRetainTypesOrderOwnershipAndXml() throws Exception {
		Domain domain = new Domain("ADDRESS");
		domain.getColumns().add(new DomainColumn("CITY").setDataType(DataType.VARCHAR).setLength(80));
		domain.getColumns().add(new DomainColumn("ZIP").setDataType(DataType.INT).setNotNull(true));
		domain.getChecks().add(new DomainCheckConstraint("CK_ZIP", "ZIP > 0"));
		Domain restored = SchemaUtils.readXml(new java.io.StringReader(domain.asXml()));
		assertEquals(domain, restored);
		assertEquals("CITY", restored.getColumns().get(0).getName());
		assertSame(restored, restored.getColumns().getParent());
		assertSame(restored.getColumns(), restored.getColumns().get(1).getParent());
		Domain clone = domain.clone();
		assertEquals(domain, clone);
		clone.getColumns().get(0).setLength(40);
		assertNotEquals(domain, clone);
		assertNotEquals(State.Unchanged, domain.diff(clone).getState());
		assertEquals(80L, domain.getColumns().get(0).getLength());
	}

	@Test
	void invalidNamesFailAndScalarXmlRemainsConcise() throws Exception {
		Domain domain = new Domain("D");
		assertThrows(IllegalArgumentException.class, () -> domain.getColumns().add(new DomainColumn()));
		domain.getColumns().add(new DomainColumn("C").setDataType(DataType.INT));
		assertThrows(IllegalArgumentException.class, () -> domain.getColumns().add(new DomainColumn("C")));
		Domain scalar = new Domain("S").setDataType(DataType.INT);
		assertFalse(scalar.asXml().contains("<columns"));
	}
}
