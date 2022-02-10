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

public class DimensionTest extends AbstractDbObjectTest<Dimension> {

	@Override
	protected Dimension getObject() {
		Dimension obj = getDimension("dimensionA");
		return obj;
	}

	public static Dimension getDimension(String name) {
		Dimension obj = new Dimension(name);
		obj.setRemarks("Comment A");
		DimensionLevel dl3 = DimensionLevelTest.getDimensionLevel("level3");
		DimensionLevel dl2 = DimensionLevelTest.getDimensionLevel("level2");
		DimensionLevel dl1 = DimensionLevelTest.getDimensionLevel("level1");
		obj.getLevels().add(dl3);
		obj.getLevels().add(dl2);
		obj.getLevels().add(dl1);
		//
		DimensionHierarchy h1 = DimensionHierarchyTest.getDimensionHierarchy("level1");
		obj.getHierarchies().add(h1);
		//
		DimensionAttribute a1 = DimensionAttributeTest.getDimensionAttribute("attribute1");
		obj.getAttributes().add(a1);
		obj.validate();
		return obj;
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<Dimension> getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Dimension obj1, Dimension obj2) {
		obj2.getLevels().get("level2").setSkipWhenNull(true);
		obj2.getLevels().remove("level3");
		// obj2.setName("b");
		obj2.setRemarks("Comment B");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}
}
