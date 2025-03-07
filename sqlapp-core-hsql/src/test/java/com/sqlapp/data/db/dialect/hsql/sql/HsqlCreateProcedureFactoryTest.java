/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.SavepointLevel;
import com.sqlapp.data.schemas.SqlDataAccess;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class HsqlCreateProcedureFactoryTest extends AbstractHsqlSqlFactoryTest {
	SqlFactory<Procedure> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(new Procedure(),
				SqlType.CREATE);
	}

	@Test
	public void testCreateTest1() {
		Procedure obj = getProcedure("new_customer");
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure1.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	private Procedure getProcedure(String name) {
		Procedure obj = new Procedure(name);
		obj.setSpecificName("NEW_CUSTOMER_10030");
		obj.setDialect(dialect);
		obj.setLanguage("SQL");
		NamedArgument arg = obj.getArguments().newArgument("firstname");
		arg.setDataTypeName("VARCHAR(50)");
		obj.getArguments().add(arg);
		//
		arg = obj.getArguments().newArgument("lastname");
		arg.setDataTypeName("VARCHAR(50)");
		obj.getArguments().add(arg);
		//
		obj.setDeterministic(false);
		obj.setSqlDataAccess(SqlDataAccess.ModifiesSqlData);
		obj.setSavepointLevel(SavepointLevel.NewSavePointLevel);
		obj.setStatement("INSERT INTO CUSTOMERS VALUES(DEFAULT,FIRSTNAME,LASTNAME,CURRENT_TIMESTAMP)");
		return obj;
	}

	@Test
	public void testCreateTest2() {
		Procedure obj = getProcedure2("NEW_CUSTOMER2");
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure2.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	private Procedure getProcedure2(String name) {
		Procedure obj = new Procedure(name);
		obj.setSpecificName("NEW_CUSTOMER_10070");
		obj.setDialect(dialect);
		obj.setLanguage("SQL");
		NamedArgument arg = obj.getArguments().newArgument("FIRSTNAME");
		arg.setDataTypeName("VARCHAR(50)");
		obj.getArguments().add(arg);
		//
		arg = obj.getArguments().newArgument("LASTNAME");
		arg.setDataTypeName("VARCHAR(50)");
		obj.getArguments().add(arg);
		//
		arg = obj.getArguments().newArgument("ADDRESS");
		arg.setDataType(DataType.VARCHAR);
		arg.setLength(100);
		obj.getArguments().add(arg);
		//
		obj.setLanguage("JAVA");
		obj.setClassNamePrefix("CLASSPATH");
		obj.setClassName("com.sqlapp.ant.plugins.dialect.hsql.ProcedureTest");
		obj.setMethodName("newCustomerProcedure");
		obj.setDeterministic(false);
		obj.setSqlDataAccess(SqlDataAccess.ModifiesSqlData);
		obj.setSavepointLevel(SavepointLevel.NewSavePointLevel);
		obj.setMaxDynamicResultSets(1);
		return obj;
	}
}
