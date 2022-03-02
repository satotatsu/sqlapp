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
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.Assembly.PermissionSet;
import com.sqlapp.data.schemas.AssemblyFile;
import com.sqlapp.util.CommonUtils;

public class SqlServerCreateAssemblyTest extends AbstractSqlServerSqlFactoryTest {
	SqlFactory<Assembly> createOperationFactory;
	SqlFactory<Assembly> dropOperationFactory;

	@BeforeEach
	public void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Assembly("asm"), SqlType.CREATE);
		dropOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Assembly("asm"), SqlType.DROP);
	}

	@Test
	public void testCreateTest1() {
		Assembly obj = getAssembly("HelloWorld");
		List<SqlOperation> list = createOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_assembly1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Assembly getAssembly(String name) {
		Assembly obj = new Assembly(name);
		obj.setPermissionSet(PermissionSet.Safe);
		AssemblyFile asf = obj.newAssemblyFile();
		asf.setName("c:\\Program Files\\Microsoft SQL Server\\100\\Samples\\HelloWorld\\CS\\HelloWorld\\bin\\debug\\HelloWorld.dll");
		obj.getAssemblyFiles().add(asf);
		return obj;
	}

	@Test
	public void testDropTest1() {
		Assembly obj = getAssembly("HelloWorld");
		List<SqlOperation> list = dropOperationFactory.createSql(obj);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("drop_assembly1.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
