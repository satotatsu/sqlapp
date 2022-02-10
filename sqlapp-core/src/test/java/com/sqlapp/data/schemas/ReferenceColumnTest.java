/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

public class ReferenceColumnTest extends AbstractDbObjectTest<ReferenceColumn> {

	@Override
	protected ReferenceColumn getObject() {
		ReferenceColumn column = new ReferenceColumn("A");
		column.setWith("=");
		column.setRemarks("comment");
		column.addDefinition("DDL1行目");
		column.addDefinition("DDL2行目");
		return column;
	}

	@Override
	protected ReferenceColumnXmlReaderHandler getHandler() {
		return new ReferenceColumnXmlReaderHandler();
	}

	@Override
	protected void testDiffString(ReferenceColumn obj1, ReferenceColumn obj2) {
		obj2.setName("b");
		obj2.setWith("&&");
		obj2.setRemarks("コメントB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
