/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

public class ObjectPrivilegeTest extends AbstractDbObjectTest<ObjectPrivilege> {
	@org.junit.jupiter.api.Test
	void hierarchyFlagSurvivesXmlForBothValues() throws Exception {
		for (boolean hierarchy : new boolean[] { false, true }) {
			var original = getPrivilege().setHierachy(hierarchy);
			ObjectPrivilege restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
			org.junit.jupiter.api.Assertions.assertEquals(hierarchy, restored.isHierachy());
			org.junit.jupiter.api.Assertions.assertEquals(original, restored);
		}
	}

	@org.junit.jupiter.api.Test
	void absentHierarchyKeepsLegacyDefault() throws Exception {
		String xml = getPrivilege().asXml().replaceAll("\\s+hierachy=\"(?:true|false)\"", "");
		ObjectPrivilege restored = SchemaUtils.readXml(new java.io.StringReader(xml));
		org.junit.jupiter.api.Assertions.assertTrue(restored.isHierachy());
	}

	public static ObjectPrivilege getPrivilege() {
		ObjectPrivilege privilege = new ObjectPrivilege();
		privilege.setGrantorName("dbo").setGranteeName("userA").setObjectName("TABLEA").setPrivilege("SELECT");
		return privilege;
	}

	@Override
	protected ObjectPrivilege getObject() {
		return getPrivilege();
	}

	@Override
	protected AbstractBaseDbObjectXmlReaderHandler<ObjectPrivilege> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ObjectPrivilege obj1, ObjectPrivilege obj2) {
		obj2.setGrantorName("dbo1").setGranteeName("userB").setObjectName("TABLEB").setPrivilege("DELETE");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
