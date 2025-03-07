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

package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreatePartitionSchemeTest extends AbstractSqlServerSqlFactoryTest {
	SqlFactory<PartitionScheme> createOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new PartitionScheme("func"), SqlType.CREATE);
	}

	private PartitionScheme getObj(String name) {
		PartitionScheme obj = new PartitionScheme(name);
		obj.setDialect(dialect);
		return obj;
	}

	@Test
	public void testCreateTest1() {
		PartitionScheme obj = getObj("myRangePS1");
		obj.setPartitionFunctionName("myRangePF1");
		obj.getTableSpaces().add("test1fg");
		obj.getTableSpaces().add("test2fg");
		obj.getTableSpaces().add("test3fg");
		obj.getTableSpaces().add("test4fg");
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_partition_scheme1.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
