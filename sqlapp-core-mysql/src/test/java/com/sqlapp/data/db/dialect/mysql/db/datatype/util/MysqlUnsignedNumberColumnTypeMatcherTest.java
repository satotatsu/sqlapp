/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class MysqlUnsignedNumberColumnTypeMatcherTest {

	@Test
	void test() {
		ColumnTypeMatcher columnTypeNameMatcher = createMatcher("INT", "INTEGER");
		TypeInformation column = createCoumn("int unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
		//
		column = createCoumn("integer unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
		//
		column = createCoumn("integer unsigned", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isEmpty());
		Optional<TypeInformation> optional = createCoumnOptional("INTEGER zerofill", columnTypeNameMatcher);
		assertTrue(optional.isEmpty());
		//
		columnTypeNameMatcher = createMatcher("BIGINT");
		column = createCoumn("bigint unsigned zerofill", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getSpecifics().isPresent());
		assertEquals("true", column.getSpecifics().get().get("zerofill"));
	}

	private ColumnTypeMatcher createMatcher(String... dataTypeName) {
		ColumnTypeMatcher internalMatcher = new MysqlUnsignedNumberColumnTypeMatcher(dataTypeName);
		return internalMatcher;
	}

	private Optional<TypeInformation> createCoumnOptional(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp;
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp.get();
	}

}
