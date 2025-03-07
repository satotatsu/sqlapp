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

public class DimensionLevelTest extends AbstractDbObjectTest<DimensionLevel> {

	@Override
	protected DimensionLevel getObject() {
		DimensionLevel obj = getDimensionLevel("dimensionA");
		return obj;
	}

	public static DimensionLevel getDimensionLevel(String name) {
		DimensionLevel obj = new DimensionLevel(name);
		DimensionLevelColumn column=new DimensionLevelColumn("colB");
		column.setTableName("table1");
		obj.getColumns().add(column);
		column=new DimensionLevelColumn("colA");
		obj.getColumns().add(column);
		return obj;
	}

	@Override
	protected void testDiffString(DimensionLevel obj1, DimensionLevel obj2) {
		obj2.setSkipWhenNull(true);
		obj2.getColumns().add("colC");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
