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

public class DimensionAttributeTest extends
		AbstractDbObjectTest<DimensionAttribute> {

	@Override
	protected DimensionAttribute getObject() {
		DimensionAttribute obj = getDimensionAttribute("attribute1");
		return obj;
	}

	public static DimensionAttribute getDimensionAttribute(String name) {
		DimensionAttribute obj = new DimensionAttribute(name);
		obj.getColumns().add("colB");
		obj.getColumns().add("colA");
		return obj;
	}

	@Override
	protected void testDiffString(DimensionAttribute obj1, DimensionAttribute obj2) {
		obj2.getColumns().add("colC");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
