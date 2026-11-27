/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FirebirdTriggerTypeTest {
	@Test
	void decodesSingleAndMultipleDmlEvents() {
		int[] types = { 1, 3, 5, 17, 25, 27, 113 };
		var events = java.util.List.of(Set.of("INSERT"), Set.of("UPDATE"), Set.of("DELETE"), Set.of("INSERT", "UPDATE"),
				Set.of("INSERT", "DELETE"), Set.of("UPDATE", "DELETE"), Set.of("INSERT", "UPDATE", "DELETE"));
		for (int i = 0; i < types.length; i++) {
			assertEquals(events.get(i), FirebirdTriggerReader.getEventManipulationString(types[i]));
			assertEquals(events.get(i), FirebirdTriggerReader.getEventManipulationString(types[i] + 1));
			assertEquals("BEFORE", FirebirdTriggerReader.getActionTimingString(types[i]));
			assertEquals("AFTER", FirebirdTriggerReader.getActionTimingString(types[i] + 1));
		}
	}

	@Test
	void decodesDatabaseAndDdlEventsWithoutTruncatingLongBits() {
		assertEquals(Set.of("ON CONNECT"), FirebirdTriggerReader.getEventManipulationString(8192));
		assertNull(FirebirdTriggerReader.getActionTimingString(8192));
		long type = 0x0000100100004001L;
		assertEquals("AFTER", FirebirdTriggerReader.getActionTimingString(type));
		assertEquals(Set.of("ALTER SEQUENCE", "DROP PACKAGE BODY"),
				FirebirdTriggerReader.getEventManipulationString(type));
		assertEquals(Set.of("ANY DDL STATEMENT"),
				FirebirdTriggerReader.getEventManipulationString(0x7FFFFFFFFFFFDFFEL));
		assertThrows(IllegalArgumentException.class, () -> FirebirdTriggerReader.getEventManipulationString(0));
	}
}
