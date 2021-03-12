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
/**
 * 
 */
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
/**
 * @author satoh
 *
 */
public class SqlBuilderTest {

	@Test
	public void _parameterEq() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterEq("cola", "COLB");
		assertEquals("cola = /*COLB*/'1'", builder.toString());
	}

	@Test
	public void _parameterIn() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterIn("cola", "COLB");
		assertEquals("cola IN /*COLB*/(1)", builder.toString());
	}

	@Test
	public void _parameterGt() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterGt("cola", "COLB");
		assertEquals("cola > /*COLB*/'1'", builder.toString());
	}

	@Test
	public void _parameterGte() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterGte("cola", "COLB");
		assertEquals("cola >= /*COLB*/'1'", builder.toString());
	}

	@Test
	public void _parameterLt() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterLt("cola", "COLB");
		assertEquals("cola < /*COLB*/'1'", builder.toString());
	}

	@Test
	public void _parameterLte() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterLte("cola", "COLB");
		assertEquals("cola <= /*COLB*/'1'", builder.toString());
	}

	@Test
	public void _parameterLike() {
		final SqlBuilder builder=(SqlBuilder)DialectResolver.getInstance().getDefaultDialect().createSqlBuilder();
		builder._parameterLike("cola", "COLB");
		assertEquals("cola LIKE /*COLB*/'1'", builder.toString());
	}

}
