/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.core.test.AbstractAlterSchemaTest;

public class OracleAlterSchemaTest extends AbstractAlterSchemaTest {

	@BeforeEach
	public void before() {
		super.before();
	}

	@Test
	public void testAlterTest1() throws Exception {
		// assertEquals(this.getResource("alter_schema1.sql"),
		// this.getAlterSqlText("sysb_schemas.xml", "sysc_schemas.xml"));
	}

	@Override
	protected String getProductName() {
		return "oracle";
	}

	@Override
	protected int getMajorVersion() {
		return 11;
	}

	@Override
	protected int getMinorVersion() {
		return 1;
	}

}
