/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.util.CommonUtils;

/**
 * DB2用のCreate Procedure
 * 
 * @author tatsuo satoh
 * 
 */
public class Db2_1010CreateMaskFactoryTest extends AbstractDb2SqlFactoryTest {
	SqlFactory<Mask> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Mask(), SqlType.CREATE);
	}

	@Test
	public void testCreateTest2() {
		Mask obj = getMask("SSN_MASK");
		obj.setSchemaName("test");
		obj.setTableName("EMPLOYEE");
		obj.setColumnName("SSN");
		String statement = getResource("create_mask_statement1.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_mask1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Mask getMask(String name) {
		Mask obj = new Mask(name);
		obj.setDialect(dialect);
		return obj;
	}
}
