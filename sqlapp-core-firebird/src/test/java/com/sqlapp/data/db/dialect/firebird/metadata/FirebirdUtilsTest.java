/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;

public class FirebirdUtilsTest {

	@Test
	public void testConvertCheckConstraint() {
		assertEquals("a>b", FirebirdUtils.convertCheckConstraint("CHECK (a>b)"));
	}

	@Test
	void preservesMultilineDefaultExpressionsAndTrailingLetterS() {
		Column column = new Column("value");
		FirebirdUtils.setDefaultConstraint(column, "DEFAULT CASE\nWHEN 1=1 THEN 'status' ELSE 's' END");
		assertEquals("CASE\nWHEN 1=1 THEN 'status' ELSE 's' END", column.getDefaultValue());
	}

	@Test
	void restoresFixedPointSubtypesForEveryIntegerStorageWidth() {
		for (int type : new int[] { 7, 8, 16, 26 }) {
			for (int subtype : new int[] { 1, 2 }) {
				Column column = new Column("amount");
				FirebirdUtils.setDbType(column, type, subtype, 8, 9, -2, 0);
				assertEquals(subtype == 1 ? DataType.NUMERIC : DataType.DECIMAL, column.getDataType());
				assertEquals(9L, column.getLength());
				assertEquals(2, column.getScale());
			}
		}
	}

	@Test
	void restoresNativeBoolean() {
		Column column = new Column("enabled");
		FirebirdUtils.setDbType(column, 23, 0, 1, 0, 0, 0);
		assertEquals(DataType.BOOLEAN, column.getDataType());
		assertEquals("BOOLEAN", column.getDataTypeName());
	}

}
