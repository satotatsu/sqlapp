/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver;


import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2000;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2005;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2008;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2012;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2014;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2016;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2017;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2019;
import com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver;


public class SqlServerVersionTest {

	private Dialect dialect2000=SqlServerDialectResolver.getInstance().getDialect(8, 0);
	private Dialect dialect2005=SqlServerDialectResolver.getInstance().getDialect(9, 0);
	private Dialect dialect2008=SqlServerDialectResolver.getInstance().getDialect(10, 0);
	private Dialect dialect2012=SqlServerDialectResolver.getInstance().getDialect(11, 0);
	private Dialect dialect2014=SqlServerDialectResolver.getInstance().getDialect(12, 0);
	private Dialect dialect2016=SqlServerDialectResolver.getInstance().getDialect(13, 0);
	private Dialect dialect2017=SqlServerDialectResolver.getInstance().getDialect(14, 0);
	private Dialect dialect2019=SqlServerDialectResolver.getInstance().getDialect(15, 0);

	@Test
	public void testInstance() {
		assertTrue(dialect2000 instanceof SqlServer2000);
		assertTrue(dialect2005 instanceof SqlServer2005);
		assertTrue(dialect2008 instanceof SqlServer2008);
		assertTrue(dialect2012 instanceof SqlServer2012);
		assertTrue(dialect2014 instanceof SqlServer2014);
		assertTrue(dialect2016 instanceof SqlServer2016);
		assertTrue(dialect2017 instanceof SqlServer2017);
		assertTrue(dialect2019 instanceof SqlServer2019);
	}

	@Test
	public void testCompareTo() {
		assertTrue(dialect2000.compareTo(dialect2005)<0);
		assertTrue(dialect2005.compareTo(dialect2008)<0);
		assertTrue(dialect2008.compareTo(dialect2012)<0);
		assertTrue(dialect2012.compareTo(dialect2014)<0);
		assertTrue(dialect2014.compareTo(dialect2016)<0);
		assertTrue(dialect2017.compareTo(dialect2019)<0);
		//
		assertTrue(dialect2019.compareTo(dialect2017)>0);
		assertTrue(dialect2017.compareTo(dialect2016)>0);
		assertTrue(dialect2016.compareTo(dialect2014)>0);
		assertTrue(dialect2014.compareTo(dialect2012)>0);
		assertTrue(dialect2012.compareTo(dialect2008)>0);
		assertTrue(dialect2008.compareTo(dialect2005)>0);
	}
}
