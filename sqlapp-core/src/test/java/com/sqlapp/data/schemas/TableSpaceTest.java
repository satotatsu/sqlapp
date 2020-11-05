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


public class TableSpaceTest extends AbstractDbObjectTest<TableSpace> {

	public static TableSpace getTableSpace() {
		TableSpace tableSpace = new TableSpace("spaceA");
		TableSpaceFile tableSpaceFile = new TableSpaceFile("ddd",
				"/aaa/ccc/ddd.dat");
		tableSpace.getTableSpaceFiles().add(tableSpaceFile);
		tableSpaceFile = new TableSpaceFile("ddd", "/aaa/bbb/EEE.dat");
		tableSpace.getTableSpaceFiles().add(tableSpaceFile);
		return tableSpace;
	}

	@Override
	protected TableSpace getObject() {
		return getTableSpace();
	}

	@Override
	protected AbstractNamedObjectXmlReaderHandler<TableSpace> getHandler() {
		return this.getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(TableSpace obj1, TableSpace obj2) {
		obj2.getTableSpaceFiles().get(0).setAutoExtensible(false);
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
