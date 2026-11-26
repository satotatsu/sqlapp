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

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class DialectTest {

	Dialect dialect = DialectResolver.getInstance().getDialect("default", 1, 1);

	@Test
	public void testNvarchar() {
		Column column = createColumn();
		column.setDataTypeName("nvarchar(10)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}

	@Test
	public void testVarchar() {
		Column column = createColumn();
		column.setDataTypeName("varchar(10)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}

	@Test
	public void testChar() {
		Column column = createColumn();
		column.setDataTypeName("char(10)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}

	@Test
	public void testDecimal() {
		Column column = createColumn();
		column.setDataTypeName("decimal(10,1)");
		assertEquals(DataType.DECIMAL, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
		assertEquals(Integer.valueOf(1), column.getScale());
	}

	@Test
	public void testDecimal1() {
		Column column = createColumn();
		column.setDataTypeName("decimal");
		assertEquals(DataType.DECIMAL, column.getDataType());
		assertNull(column.getLength());
		assertNull(column.getScale());
	}

	@Test
	public void preservesQuotedQualifiedNamesInMatchingAndColumnSetters() {
		for (String name : new String[] { "\"Other.Schema\".\"Type \"\"Name\"\"\"", "public.\"Mixed Case\"",
				"\"Space  Schema\".\"Type  Name\"" }) {
			Column matched = createColumn();
			dialect.setDbType("  " + name + "  ", null, null, matched);
			assertEquals(name, matched.getDataTypeName());
			Column assigned = createColumn();
			assigned.setDataTypeName(name);
			assertEquals(name, assigned.getDataTypeName());
			assertEquals(DataType.OTHER, assigned.getDataType());
		}
		Column builtin = createColumn();
		builtin.setDataTypeName("\"CHAR\"");
		assertEquals(DataType.CHAR, builtin.getDataType());
	}

	protected Column createColumn() {
		Column column = new Column();
		column.setDialect(dialect);
		return column;
	}

	@Test
	public void quotedIdentifiersExecuteAndKeepTheirNames() throws Exception {
		try (var connection = java.sql.DriverManager.getConnection("jdbc:hsqldb:mem:quotedIdentifiers", "SA", "");
				var statement = connection.createStatement()) {
			for (String name : new String[] { "A\"B", "1ABC", "A B", "\"", "\"EDGE", "EDGE\"" }) {
				String quoted = dialect.quote(name);
				assertEquals(name, dialect.unQuote(quoted));
				statement.execute("CREATE TABLE " + quoted + " (" + quoted + " INTEGER)");
				try (var result = statement.executeQuery("SELECT " + quoted + " FROM " + quoted)) {
					assertEquals(name, result.getMetaData().getColumnName(1));
				}
				statement.execute("DROP TABLE " + quoted);
			}
		}
		assertEquals("ABC_123$", dialect.quote("ABC_123$"));
		assertEquals("\"1ABC\"", dialect.quote("1ABC"));
		assertEquals("\"A\"\"B\"", dialect.quote("A\"B"));
		assertNull(dialect.quote(null));
		assertNull(dialect.unQuote(null));
		assertEquals("", dialect.unQuote(""));
		assertEquals("A B", dialect.unQuote("\"A B\""));
		assertEquals("\"A\"", dialect.unQuote("\"\"\"A\"\"\""));
	}

	@Test
	public void bracketAndBacktickIdentifiersRoundTrip() {
		for (char open : new char[] { '[', '`' }) {
			Dialect custom = new Dialect(null) {
				@Override
				public char getOpenQuote() { return open; }
				@Override
				public char getCloseQuote() { return open == '[' ? ']' : open; }
			};
			for (String name : new String[] { "A B", "A]B", "A`B", "1ABC" }) {
				assertEquals(name, custom.unQuote(custom.quote(name)));
			}
			String quoted = open + "A B" + custom.getCloseQuote();
			assertEquals(quoted, custom.quote(quoted));
		}
	}

}
