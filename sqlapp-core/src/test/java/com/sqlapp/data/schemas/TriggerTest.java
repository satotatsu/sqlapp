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

public class TriggerTest extends AbstractDbObjectTest<Trigger> {

	@org.junit.jupiter.api.Test
	void tableIdentitySurvivesSetterOrderAndXml() throws Exception {
		Trigger trigger = new Trigger("tr");
		trigger.setTableSchemaName("other");
		trigger.setTableName("source");
		org.junit.jupiter.api.Assertions.assertEquals("other", trigger.getTableSchemaName());
		trigger.setTableSchemaName("main");
		org.junit.jupiter.api.Assertions.assertEquals("source", trigger.getTableName());
		Trigger restored = SchemaUtils.readXml(new java.io.StringReader(trigger.asXml()));
		org.junit.jupiter.api.Assertions.assertEquals("main", restored.getTableSchemaName());
		org.junit.jupiter.api.Assertions.assertEquals("source", restored.getTableName());
		Table original = new Table("bound").setSchemaName("original");
		trigger.setTable(original);
		trigger.setTableName("renamed");
		trigger.setTableSchemaName("changed");
		org.junit.jupiter.api.Assertions.assertEquals("original", original.getSchemaName());
		org.junit.jupiter.api.Assertions.assertEquals("bound", original.getName());
	}

	@org.junit.jupiter.api.Test
	void partitionParentPreservesBothTableIdentifiers() {
		PartitionParent parent = new PartitionParent();
		parent.setTableName("parent");
		parent.setTableSchemaName("other");
		org.junit.jupiter.api.Assertions.assertEquals("parent", parent.getTableName());
		parent.setTableName("renamed");
		org.junit.jupiter.api.Assertions.assertEquals("other", parent.getTableSchemaName());
	}

	public static Trigger getTrigger(String triggerName) {
		Trigger obj = new Trigger(triggerName);
		obj.setEventManipulation("INSERT", "UPDATE");
		obj.setActionOrientation("STATEMENT");
		obj.setActionTiming("BEFORE");
		obj.setActionReferenceOldRow(":OLD");
		obj.setTableName("tableA");
		obj.addDefinition("DDL1行目");
		obj.addDefinition("DDL2行目");
		return obj;
	}

	@Override
	protected Trigger getObject() {
		Trigger obj = getTrigger("Trigger");
		obj.setRemarks("コメント");
		return obj;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Trigger> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Trigger obj1, Trigger obj2) {
		obj2.setActionTiming("AFTER").setEventManipulation("DELETE", "UPDATE");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
