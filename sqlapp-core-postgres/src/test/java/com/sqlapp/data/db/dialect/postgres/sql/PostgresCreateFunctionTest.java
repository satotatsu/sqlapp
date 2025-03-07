/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.SqlSecurity;

public class PostgresCreateFunctionTest extends AbstractPostgresSqlFactoryTest {
	SqlFactory<Function> createOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Function("func"), SqlType.CREATE);
	}

	private Function getFunction(String name) {
		Function obj = new Function(name);
		obj.setDialect(dialect);
		return obj;
	}

	@Test
	public void testCreateTest1() {
		Function obj = getFunction("add");
		obj.setRemarks("comment1");
		NamedArgument arg = obj.getArguments().newArgument();
		arg.setDataTypeName("integer");
		obj.getArguments().add(arg);
		arg = obj.getArguments().newArgument();
		arg.setDataTypeName("integer");
		obj.getArguments().add(arg);
		obj.getReturning().setDataTypeName("integer");
		obj.setSqlSecurity(SqlSecurity.Invoker);
		obj.setOnNullCall(OnNullCall.ReturnsNullOnNullInput);
		obj.setDeterministic(true);
		String statement = getResource("create_function_statement1.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		int i=0;
		SqlOperation operation = list.get(i++);
		String expected = getResource("create_function1.sql");
		assertEquals(expected, operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("create_function_comment1.sql");
		assertEquals(expected, operation.getSqlText());
	}


}
