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

package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;


public class DecimalTypeTest {
	@Test
	public void testParse() {
		DecimalType type=new DecimalType();
		Column column=new Column();
		type.parseAndSet("DECIMAL (9,5)", column);
		assertEquals(column.getLength().longValue(), 9, "OK");
		assertEquals(column.getScale().intValue(), 5, "OK");
		//
		column=new Column();
		type.parseAndSet("DECIMAL", column);
		assertEquals(column.getLength(), null);
		assertEquals(column.getScale(), null, "OK");
		//
		column=new Column();
		type.parseAndSet("DECIMAL(10) ", column);
		assertEquals(column.getLength().longValue(), 10, "OK");
		assertEquals(column.getScale(), null, "OK");
		//
		type.setDefaultPrecision(38);
		type.setDefaultScale(10);
		column=new Column();
		type.parseAndSet("DECIMAL", column);
		assertEquals(column.getLength().longValue(), 38, "OK");
		assertEquals(column.getScale().intValue(), 10, "OK");
	}
}
