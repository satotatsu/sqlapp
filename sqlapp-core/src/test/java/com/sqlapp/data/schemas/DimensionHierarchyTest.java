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

public class DimensionHierarchyTest extends AbstractDbObjectTest<DimensionHierarchy> {

	@Override
	protected DimensionHierarchy getObject() {
		DimensionHierarchy obj = getDimensionHierarchy("dimensionHierarchyA");
		return obj;
	}

	public static DimensionHierarchy getDimensionHierarchy(String name) {
		DimensionHierarchy obj = new DimensionHierarchy(name);
		obj.getLevels().add(DimensionHierarchyLevelTest.getDimensionHierarchyLevel("customer"));
		obj.getLevels().add(DimensionHierarchyLevelTest.getDimensionHierarchyLevel("city"));
		//
		obj.getJoinKeys().add(DimensionHierarchyJoinKeyTest.getDimensionHierarchyJoinKey("customer", "country_id", "customers"));
		obj.getJoinKeys().add(DimensionHierarchyJoinKeyTest.getDimensionHierarchyJoinKey("city", "country_id2", "customers2"));
		return obj;
	}

	@Override
	protected void testDiffString(DimensionHierarchy obj1, DimensionHierarchy obj2) {
		obj2.getLevels().add(DimensionHierarchyLevelTest.getDimensionHierarchyLevel("state"));
		obj2.getJoinKeys().add(DimensionHierarchyJoinKeyTest.getDimensionHierarchyJoinKey("state", "country_id3", "customers3"));
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
