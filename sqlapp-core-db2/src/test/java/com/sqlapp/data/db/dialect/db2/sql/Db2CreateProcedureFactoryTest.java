/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.sql;

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
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;

/**
 * DB2用のCreate Procedure
 * 
 * @author tatsuo satoh
 * 
 */
public class Db2CreateProcedureFactoryTest extends AbstractDb2SqlFactoryTest {
	SqlFactory<Procedure> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Procedure(), SqlType.CREATE);
	}

	@Test
	public void testCreateTest2() {
		Procedure obj = getProcedure("test_goto");
		obj.setSchemaName("Purchasing");
		obj.setExecuteAs("CALLER");
		NamedArgument arg=new NamedArgument("p1");
		arg.setDataType(DataType.INT);
		obj.getArguments().add(arg);
		arg=new NamedArgument("out1");
		arg.setDataType(DataType.VARCHAR);
		arg.setLength(10);
		arg.setDirection(ParameterDirection.Output);
		obj.getArguments().add(arg);
		String statement = getResource("create_procedure_statement1.sql");
		obj.setStatement(statement);
		List<SqlOperation> list = operation.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_procedure1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Procedure getProcedure(String name) {
		Procedure obj = new Procedure(name);
		obj.setDialect(dialect);
		return obj;
	}
}
