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

public class PartitionSchemeTest extends AbstractDbObjectTest<PartitionScheme> {

	public static PartitionScheme getPartitionScheme(String name) {
		PartitionScheme obj = new PartitionScheme(name);
		obj.setTableSpaces("ts1", "ts2");
		obj.setRemarks("コメント");
		obj.addDefinition("DDL1行目");
		obj.addDefinition("DDL2行目");
		return obj;
	}

	@Override
	protected PartitionScheme getObject() {
		return getPartitionScheme("PartitionSchemeA");
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<PartitionScheme> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(PartitionScheme obj1, PartitionScheme obj2) {
		obj2.setTableSpaces("ts1", "ts2", "ts3");
		obj2.addDefinition("DDL3行目");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
