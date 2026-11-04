/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class RoutinePrivilegeTest {
	@Test void xmlRetainsBothHierarchyValuesAndSpecificNames() throws Exception {
		for (boolean hierarchy : new boolean[] { false, true }) {
			for (String specific : new String[] { "P", "P_1" }) {
				var original = privilege().setSpecificName(specific).setHierachy(hierarchy);
				RoutinePrivilege restored = SchemaUtils.readXml(new StringReader(original.asXml()));
				assertEquals(hierarchy, restored.isHierachy());
				assertEquals(specific, restored.getSpecificName());
				assertEquals(original, restored);
			}
		}
	}
	@Test void missingHierarchyAttributeKeepsTheLegacyDefault() throws Exception {
		String xml = privilege().asXml().replaceAll("\\s+hierachy=\"(?:true|false)\"", "");
		RoutinePrivilege restored = SchemaUtils.readXml(new StringReader(xml));
		assertTrue(restored.isHierachy());
	}
	private static RoutinePrivilege privilege() {
		return new RoutinePrivilege().setSchemaName("APP").setObjectName("P")
				.setGranteeName("READER").setGrantorName("OWNER").setPrivilege("EXECUTE");
	}
}
