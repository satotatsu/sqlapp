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

package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.TypeInformation;

public class DecimalTypeTest {
	@Test
	public void testParse() {
		DecimalType type = new DecimalType();
		Optional<TypeInformation> optional = type.matchDataTypeName("DECIMAL (9,5)");
		TypeInformation column = optional.get();
		assertEquals(9, column.getLength().get().intValue());
		assertEquals(5, column.getScale().get().intValue());
		//
		optional = type.matchDataTypeName("DECIMAL (10,6)");
		column = optional.get();
		assertEquals(10, column.getLength().get().intValue());
		assertEquals(6, column.getScale().get().intValue());
		//
		optional = type.matchDataTypeName("DECIMAL (10)");
		column = optional.get();
		assertEquals(10, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		//
		optional = type.matchDataTypeName("DECIMAL");
		column = optional.get();
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getScale().isEmpty());
		//
		type.setDefaultPrecision(38);
		type.setDefaultScale(8);
		optional = type.matchDataTypeName("DECIMAL");
		column = optional.get();
		assertEquals(38, column.getLength().get().intValue());
		assertEquals(8, column.getScale().get().intValue());
	}
}
