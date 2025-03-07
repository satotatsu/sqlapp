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
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreatePartitionFunctionTest extends AbstractSqlServerSqlFactoryTest {
	SqlFactory<PartitionFunction> createOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new PartitionFunction("func"), SqlType.CREATE);
	}

	private PartitionFunction getObj(String name) {
		PartitionFunction obj = new PartitionFunction(name);
		obj.setDialect(dialect);
		return obj;
	}

	@Test
	public void testCreateTest1() {
		PartitionFunction obj = getObj("myRangePF1");
		obj.setDataTypeName("int");
		obj.setBoundaryValueOnRight(false);
		obj.getValues().add("1");
		obj.getValues().add("100");
		obj.getValues().add("1000");
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_partition_function1.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
