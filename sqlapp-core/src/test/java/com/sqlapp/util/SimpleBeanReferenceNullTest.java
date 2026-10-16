/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.TableSpace;

class SimpleBeanReferenceNullTest {
	@Test void referenceFieldsCanBeClearedWithoutAcceptingWrongTypes() {
		Index index = new Index("IX").setTableSpaceName("OLD");
		assertFalse(SimpleBeanUtils.setField(index, "tableSpace", new Object()));
		assertEquals("OLD", index.getTableSpaceName());
		assertFalse(SimpleBeanUtils.setField(index, "tableSpace", null));
		assertEquals("OLD", index.getTableSpaceName());
		assertTrue(SimpleBeanUtils.setNullableField(index, "tableSpace", null));
		assertNull(index.getTableSpace());
		assertTrue(SimpleBeanUtils.setField(index, "tableSpace", new TableSpace("NEW")));
		assertEquals("NEW", index.getTableSpaceName());
	}
	@Test void tableSpaceSettersAndClonesRetainExplicitRemoval() {
		Index index = new Index("IX").setTableSpaceName("OLD"); Index copy = index.clone(); copy.setTableSpaceName(null);
		assertNull(copy.getTableSpaceName()); assertEquals("OLD", index.getTableSpaceName());
		Partition partition = new Partition("P").setTableSpaceName("OLD"); Partition restored = partition.clone();
		restored.setTableSpaceName(null); assertNull(restored.getTableSpaceName()); assertEquals("OLD", partition.getTableSpaceName());
		assertFalse(SimpleBeanUtils.setNullableField(index, "compression", null));
	}
}
