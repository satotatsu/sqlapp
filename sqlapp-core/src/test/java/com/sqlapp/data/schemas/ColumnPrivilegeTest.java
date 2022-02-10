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

public class ColumnPrivilegeTest extends AbstractDbObjectTest<ColumnPrivilege> {

	public static ColumnPrivilege getColumnPrivilege(String name) {
		ColumnPrivilege column = new ColumnPrivilege();
		column.setColumnName(name);
		column.setObjectName("tableA");
		column.setGrantorName("userA");
		column.setGranteeName("grantee");
		return column;
	}
	
	@Override
	protected ColumnPrivilege getObject() {
		return getColumnPrivilege("colA");
	}

	@Override
	protected void testDiffString(ColumnPrivilege obj1, ColumnPrivilege obj2) {
		obj2.setObjectName("tableB");
		obj2.setGrantorName("userB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
