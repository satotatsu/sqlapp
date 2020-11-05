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

import com.sqlapp.data.db.datatype.DataType;

public class MviewTest extends AbstractDbObjectTest<Mview> {

	public static Mview getMview(String tableName) {
		Mview table = new Mview(tableName);
		// テーブルスペース
		return table;
	}

	public static Column getColumn(String name, DataType types) {
		Column column = new Column(name);
		column.setDataType(types);
		return column;
	}

	@Override
	protected Mview getObject() {
		Mview table = getMview("MviewA");
		return table;
	}

	@Override
	protected TableXmlReaderHandler getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(Mview obj1, Mview obj2) {
		obj2.setName("MviewB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
