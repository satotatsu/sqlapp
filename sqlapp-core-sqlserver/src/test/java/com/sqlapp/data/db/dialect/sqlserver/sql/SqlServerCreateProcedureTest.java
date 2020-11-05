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
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreateProcedureTest extends AbstractSqlServerSqlFactoryTest {
	SqlFactory<Procedure> createOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Procedure("func"), SqlType.CREATE);
	}

	private Procedure getProcedure(String name) {
		Procedure obj = new Procedure(name);
		obj.setDialect(dialect);
		return obj;
	}

	@Test
	public void testCreateTest1() {
		Procedure obj = getProcedure("uspGetEmployees2");
		obj.setSchemaName("HumanResources");
		NamedArgument arg = obj.getArguments().newArgument("@LastName");
		arg.setDataTypeName("nvarchar(50)");
		arg.setDefaultValue("N'D%'");
		obj.getArguments().add(arg);
		arg = obj.getArguments().newArgument("@FirstName");
		arg.setDataTypeName("nvarchar(50)");
		arg.setDefaultValue("N'%'");
		obj.getArguments().add(arg);
		String statement = getResource("create_procedure_statement1.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest2() {
		Procedure obj = getProcedure("uspVendorAllInfo");
		obj.setSchemaName("Purchasing");
		obj.setExecuteAs("CALLER");
		String statement = getResource("create_procedure_statement2.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testCreateTest3() {
		Procedure obj = getProcedure("uspProductByVendor");
		obj.setSchemaName("dbo");
		NamedArgument arg = obj.getArguments().newArgument("@Name");
		arg.setDataTypeName("varchar(30)");
		arg.setDefaultValue("'%'");
		arg.setReadonly(true);
		obj.getArguments().add(arg);
		String statement = getResource("create_procedure_statement3.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure3.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
