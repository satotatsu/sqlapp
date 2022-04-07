/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractCreateSchemaTest;

public class MySqlCreateSchemaTest extends AbstractCreateSchemaTest {

	@BeforeEach
	public void before() {
		super.before();
	}

	@Test
	public void testCreateTest1() throws Exception {
		//assertEquals(this.getResource("create_schema1.sql"), this.getSqlText("schemas.xml"));
	}

	@Override
	protected String productName() {
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
