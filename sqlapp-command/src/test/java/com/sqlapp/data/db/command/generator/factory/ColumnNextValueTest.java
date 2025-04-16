/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class ColumnNextValueTest {

	private ColumnNextValue func = new ColumnNextValue();

	@Test
	void testBIGINT() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BIGINT);
		assertEquals("_previous.col + 1", func.apply(column));
	}

	@Test
	void testBOOLEAN() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.BOOLEAN);
		assertEquals("!_previous.col", func.apply(column));
	}

	@Test
	void testVARCHAR() {
		Column column = new Column();
		column.setDataType(DataType.VARCHAR);
		column.setLength(10);
		assertEquals("nextAlphaNumeric( 10 )", func.apply(column));
	}

	@Test
	void testDATE() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATE);
		assertEquals("addDays(_previous.col,1)", func.apply(column));
	}

	@Test
	void testDATETIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.DATETIME);
		assertEquals("addMilliSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testTIMESTAMP() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIMESTAMP);
		assertEquals("addMilliSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testTIME() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.TIME);
		assertEquals("addSeconds(_previous.col,1)", func.apply(column));
	}

	@Test
	void testUUID() {
		Column column = new Column();
		column.setName("col");
		column.setDataType(DataType.UUID);
		assertEquals("java.util.UUID.randomUUID()", func.apply(column));
	}

	@Test
	void testJson() {
		Column column = new Column();
		column.setDataType(DataType.JSON);
		assertEquals("\"{}\"", func.apply(column));
	}

	@Test
	void testJsonb() {
		Column column = new Column();
		column.setDataType(DataType.JSONB);
		assertEquals("\"{}\"", func.apply(column));
	}
}
