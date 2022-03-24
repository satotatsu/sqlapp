/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleTriggerFactoryTest extends AbstractOracleSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}


	@Test
	public void testCreate1() {
		Trigger obj1 = getTrigger("triggerA");
		List<SqlOperation> list = sqlFactoryRegistry.createSql(obj1, SqlType.CREATE);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("create_trigger1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testAlter1() {
		Trigger obj1 = getTrigger("triggerA");
		Trigger obj2 = getTrigger("triggerB");
		obj2.setEnable(false);
		DbObjectDifference diff=obj1.diff(obj2);
		List<SqlOperation> list = sqlFactoryRegistry.createSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_trigger1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Trigger getTrigger(String name) {
		Trigger obj = new Trigger(name);
		obj.setTableName("tableA");
		obj.setEnable(true);
		obj.setStatement(getResource("trigger_statement1.sql"));
		return obj;
	}

}
