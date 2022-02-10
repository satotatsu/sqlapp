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

public class DimensionHierarchyJoinKeyTest extends
		AbstractDbObjectTest<DimensionHierarchyJoinKey> {

	@Override
	protected DimensionHierarchyJoinKey getObject() {
		DimensionHierarchyJoinKey obj = getDimensionHierarchyJoinKey("customer", "country_id", "customers");
		return obj;
	}

	public static DimensionHierarchyJoinKey getDimensionHierarchyJoinKey(String name, String columnName, String tableName) {
		DimensionHierarchyJoinKey obj = new DimensionHierarchyJoinKey();
		obj.setLevelName(name);
		DimensionHierarchyJoinKeyColumn column=new DimensionHierarchyJoinKeyColumn();
		column.setName(columnName);
		column.setTableName(tableName);
		obj.getColumns().add(column);
		return obj;
	}

	@Override
	protected void testDiffString(DimensionHierarchyJoinKey obj1, DimensionHierarchyJoinKey obj2) {
		DimensionHierarchyJoinKeyColumn column=new DimensionHierarchyJoinKeyColumn();
		column.setName("country_region");
		column.setTableName("customers");
		obj2.getColumns().add(column);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
