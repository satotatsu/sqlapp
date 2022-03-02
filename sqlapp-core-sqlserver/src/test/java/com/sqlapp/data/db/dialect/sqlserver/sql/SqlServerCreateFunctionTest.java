/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreateFunctionTest extends AbstractSqlServerSqlFactoryTest {
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
		Function obj = getFunction("ISOweek");
		NamedArgument arg = obj.getArguments().newArgument("@DATE");
		arg.setDataTypeName("datetime");
		obj.getArguments().add(arg);
		obj.getReturning().setDataTypeName("int");
		obj.setSqlSecurity(SqlSecurity.Invoker);
		String statement = getResource("create_function_statement1.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest2() {
		Function obj = getFunction("ufn_SalesByStore");
		obj.setSchemaName("Sales");
		NamedArgument arg = obj.getArguments().newArgument("@storeid");
		arg.setDataTypeName("int");
		obj.getArguments().add(arg);
		obj.setFunctionType(FunctionType.Table);
		String statement = getResource("create_function_statement2.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest3() {
		Function obj = getFunction("ufn_FindReports");
		obj.setSchemaName("dbo");
		NamedArgument arg = obj.getArguments().newArgument("@InEmpID");
		arg.setDataTypeName("INTEGER");
		obj.getArguments().add(arg);
		obj.getReturning().setName("@retFindReports");
		obj.setFunctionType(FunctionType.Table);
		String tableDef = getResource("create_function_table3.sql");
		obj.getReturning().setDefinition(tableDef);
		String statement = getResource("create_function_statement3.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function3.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest4() {
		Function obj = getFunction("len_s");
		obj.setSchemaName("dbo");
		obj.setClassNamePrefix("SurrogateStringFunction");
		obj.setClassName("Microsoft.Samples.SqlServer.SurrogateStringFunction");
		obj.setMethodName("LenS");
		NamedArgument arg = obj.getArguments().newArgument("@str");
		arg.setDataTypeName("nvarchar(4000)");
		obj.getArguments().add(arg);
		obj.getReturning().setDataTypeName("bigint");
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function4.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest5() {
		Function obj = getFunction("ReadEventLog");
		obj.setClassNamePrefix("tvfEventLog");
		obj.setClassName("TabularEventLog");
		obj.setMethodName("InitMethod");
		NamedArgument arg = obj.getArguments().newArgument("@logname");
		arg.setDataTypeName("nvarchar");
		arg.setLength(100);
		obj.getArguments().add(arg);
		obj.setFunctionType(FunctionType.Table);
		String tableDef = getResource("create_function_table5.sql");
		obj.getReturning().setDefinition(tableDef);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function5.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest6() {
		Function obj = getFunction("Concatenate");
		obj.setFunctionType(FunctionType.Aggregate);
		obj.setClassNamePrefix("StringUtilities");
		obj.setClassName("Microsoft.Samples.SqlServer");
		obj.setMethodName("Concatenate");
		NamedArgument arg = obj.getArguments().newArgument("@input");
		arg.setDataTypeName("nvarchar");
		arg.setLength(4000);
		obj.getArguments().add(arg);
		obj.getReturning().setDataTypeName("nvarchar");
		obj.getReturning().setLength(4000);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function6.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
