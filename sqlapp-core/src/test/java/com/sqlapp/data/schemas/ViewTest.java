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

import com.sqlapp.data.db.datatype.DataType;

public class ViewTest extends AbstractDbObjectTest<View> {

	public static View getView(String tableName) {
		View table = new View(tableName);
		// テーブルスペース
		return table;
	}

	public static Column getColumn(String name, DataType types) {
		Column column = new Column(name);
		column.setDataType(types);
		return column;
	}

	@Override
	protected View getObject() {
		View table = getView("ViewA");
		return table;
	}

	@Override
	protected TableXmlReaderHandler getHandler() {
		return getObject().getDbObjectXmlReaderHandler();
	}

	@Override
	protected void testDiffString(View obj1, View obj2) {
		obj2.setName("ViewB");
		DbObjectDifference diff = obj1.diff(obj2);
		this.testDiffString(diff);
	}

}
