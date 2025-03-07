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

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.Table;

/**
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005AlterPartitionFunctionFactoryTest extends AbstractSqlServer11SqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(
				new PartitionFunction(), SqlType.ALTER);
	}

	@Test
	public void test1() throws ParseException {
		PartitionFunction obj1 = getPartitionFunction1("funcA");
		PartitionFunction obj2 = getPartitionFunction2("funcA");
		List<SqlOperation> operations=sqlFactory.createDiffSql(obj1.diff(obj2));
		for(int i=0;i<operations.size();i++){
			SqlOperation operation=operations.get(i);
			String expected = getResource("alter_partition_function"+(i+1)+".sql");
			assertEquals(expected, operation.getSqlText());
		}
	}
	
	private PartitionFunction getPartitionFunction1(String name) throws ParseException {
		PartitionFunction obj = getPartitionFunction(name);
		obj.getValues().add("1");
		obj.getValues().add("10");
		obj.getValues().add("100");
		return obj;
	}

	private PartitionFunction getPartitionFunction2(String name) throws ParseException {
		PartitionFunction obj = getPartitionFunction(name);
		obj.getValues().add("1");
		obj.getValues().add("5");
		obj.getValues().add("10");
		return obj;
	}

	private PartitionFunction getPartitionFunction(String name) {
		PartitionFunction obj = new PartitionFunction(name);
		return obj;
	}
}
