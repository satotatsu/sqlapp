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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
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
public class PostgresTriggerFactoryTest extends AbstractPostgresSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(new Table(), State.Modified);
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
	void restoresDisabledAndSpecialFiringModesWithoutChangingOrdinaryCreate() {
		Trigger trigger = getTrigger("triggerA").setSchemaName("tenant");
		sqlFactoryRegistry.getOptions().setDecorateSchemaName(true);
		assertEquals(1, sqlFactoryRegistry.createSql(trigger, SqlType.CREATE).size());
		trigger.setEnable(false);
		var disabled = sqlFactoryRegistry.createSql(trigger, SqlType.CREATE);
		assertEquals(2, disabled.size());
		assertTrue(disabled.get(1).getSqlText().contains("DISABLE TRIGGER"));
		assertTrue(disabled.get(1).getSqlText().contains("tenant."));
		assertTrue(disabled.get(1).getSqlText().contains("tableA"));
		for (String mode : List.of("ALWAYS", "REPLICA")) {
			trigger.setEnable(true);
			trigger.getSpecifics().put(Postgres90CreateTriggerFactory.FIRING_MODE, mode);
			var enabled = sqlFactoryRegistry.createSql(trigger, SqlType.CREATE);
			assertEquals(2, enabled.size());
			assertTrue(enabled.get(1).getSqlText().contains("ENABLE " + mode + " TRIGGER"));
		}
		trigger.getSpecifics().put(Postgres90CreateTriggerFactory.FIRING_MODE, "invalid");
		assertThrows(IllegalArgumentException.class, () -> sqlFactoryRegistry.createSql(trigger, SqlType.CREATE));
		trigger.getSpecifics().clear();
		Trigger missingTable = new Trigger("missing").setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> sqlFactoryRegistry.createSql(missingTable, SqlType.CREATE));
	}

	@Test
	void recreatesTriggerCommentWithQuotedIdentifiersAndEscapedText() {
		Trigger trigger = getTrigger("Trigger Name").setSchemaName("tenant");
		trigger.setRemarks("日本語 'quoted'").setEnable(false);
		sqlFactoryRegistry.getOptions().setDecorateSchemaName(true);
		var operations = sqlFactoryRegistry.createSql(trigger, SqlType.CREATE);
		assertEquals(3, operations.size());
		String comment = operations.get(2).getSqlText();
		assertTrue(comment.contains("COMMENT ON TRIGGER \"Trigger Name\" ON tenant.\"tableA\""), comment);
		assertTrue(comment.contains("'日本語 ''quoted'''"), comment);
	}

//	@Test
//	public void testAlter1() {
//		Trigger obj1 = getTrigger("triggerA");
//		Trigger obj2 = getTrigger("triggerB");
//		obj2.setEnable(false);
//		DbObjectDifference diff=obj1.diff(obj2);
//		List<SqlOperation> list = sqlFactoryRegistry.createSql(diff);
//		SqlOperation operation = CommonUtils.first(list);
//		System.out.println(list);
//		String expected = getResource("alter_trigger1.sql");
//		assertEquals(expected, operation.getSqlText());
//	}

	private Trigger getTrigger(String name) {
		Trigger obj = new Trigger(name);
		obj.setTableName("tableA");
		obj.setEnable(true);
		obj.setStatement(getResource("trigger_statement1.sql"));
		return obj;
	}

}
