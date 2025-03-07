/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-mariadb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mariadb.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractCreateSchemaTest;

public class MariadbCreateSchemaTest extends AbstractCreateSchemaTest {

	@BeforeEach
	public void before() {
		super.before();
	}

	@Test
	public void testCreateTest1() throws Exception {
		// assertEquals(this.getResource("create_schema1.sql"),
		// this.getSqlText("schemas.xml"));
	}

	@Override
	protected String getProductName() {
		return "Mysql";
	}

	@Override
	protected int getMajorVersion() {
		return 5;
	}

	@Override
	protected int getMinorVersion() {
		return 1;
	}

}
