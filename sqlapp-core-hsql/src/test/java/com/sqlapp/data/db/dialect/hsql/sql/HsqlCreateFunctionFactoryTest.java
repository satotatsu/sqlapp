/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
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
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.OnNullCall;
import com.sqlapp.data.schemas.SqlDataAccess;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のCreateコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class HsqlCreateFunctionFactoryTest extends AbstractHsqlSqlFactoryTest {
	SqlFactory<Function> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = sqlFactoryRegistry.getSqlFactory(
				new Function(), SqlType.CREATE);
	}

	@Test
	public void testCreateTest1() {
		Function obj = getFunction("AN_HOUR_BEFORE_MAX");
		List<SqlOperation> list = sqlFactory.createSql(obj);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function1.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	private Function getFunction(String name) {
		Function obj = new Function(name);
		obj.setSpecificName("AN_HOUR_BEFORE_MAX_WITH_INT");
		obj.setDialect(dialect);
		obj.getReturning().setDataType(DataType.TIMESTAMP);
		obj.setLanguage("SQL");
		NamedArgument arg = obj.getArguments().newArgument("E_TYPE");
		arg.setDataTypeName("INTEGER");
		obj.getArguments().add(arg);
		//
		obj.setDeterministic(false);
		obj.setSqlDataAccess(SqlDataAccess.ReadsSqlData);
		obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		obj.setStatement("RETURN(SELECT MAX(EVENT_TIME)FROM ATABLE WHERE EVENT_TYPE=E_TYPE)-1 HOUR");
		return obj;
	}

	@Test
	public void testCreateTest2() {
		Function obj = getFunction2("ABS_JAVA");
		List<SqlOperation> list = sqlFactory.createSql(obj);
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_function2.sql");
		assertEquals(expected, commandText.getSqlText());
	}

	private Function getFunction2(String name) {
		Function obj = new Function(name);
		obj.setSpecificName("ABS_JAVA_INT");
		obj.setDialect(dialect);
		obj.setLanguage("JAVA");
		obj.getReturning().setDataTypeName("INTEGER");
		NamedArgument arg = obj.getArguments().newArgument("A");
		arg.setDataTypeName("INTEGER");
		obj.getArguments().add(arg);
		//
		//
		obj.setClassNamePrefix("CLASSPATH");
		obj.setClassName("java.lang.Math");
		obj.setMethodName("abs");
		obj.setDeterministic(false);
		obj.setOnNullCall(OnNullCall.CalledOnNullInput);
		return obj;
	}
}
