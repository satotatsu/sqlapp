/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

public class MariadbTest {

	Dialect dialect = DialectResolver.getInstance().getDialect("mariadb", 10,
			0);
	@Test
	public void testToType() {
		Column column = new Column();
		dialect.setDbType("enum('a','b','c')", 1L, -1, column);
		assertEquals(DataType.ENUM, column.getDataType());
		Set<String> set = new LinkedHashSet<String>();
		set.add("'a'");
		set.add("'b'");
		set.add("'c'");
		assertEquals(set, column.getValues());
	}
}
