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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class DataTypeTest {
	@Test
	public void testToType() {
		assertEquals(DataType.toType("TIMESTAMP_WITH_TIMEZONE")
				, DataType.TIMESTAMP_WITH_TIMEZONE, "OK");
		assertEquals(DataType.toType("TIMESTAMP WITH TIMEZONE")
				, DataType.TIMESTAMP_WITH_TIMEZONE, "OK");
		assertEquals(DataType.toType("  TIMESTAMP WITH   TIMEZONE ")
				, DataType.TIMESTAMP_WITH_TIMEZONE, "OK");
		assertEquals(DataType.toType("  TIMESTAMP  ")
				, DataType.TIMESTAMP, "OK");
	}

	@Test
	public void testAll() {
		for(DataType dataType:DataType.values()){
			StringBuilder builder=new StringBuilder();
			builder.append(dataType);
			if (dataType.isFixedSize()){
				builder.append(" fixedSize="+dataType.isFixedSize());
			}
			if (dataType.isFixedScale()){
				builder.append(" fixedScale="+dataType.isFixedScale());
			}
			System.out.println(builder.toString());
			assertNotEquals(dataType.getSurrogate(), dataType);
			assertNotEquals(dataType.getUpperSurrogate(), dataType);
		}
	}


	@Test
	public void testGetTypeName() {
		assertEquals("INT", DataType.INT.getTypeName());
	}

	
}
