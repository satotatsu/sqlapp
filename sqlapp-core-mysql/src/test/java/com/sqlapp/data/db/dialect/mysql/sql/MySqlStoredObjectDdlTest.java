/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.data.schemas.Trigger;

class MySqlStoredObjectDdlTest {

	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void functionCharacteristicsPrecedeSingleBody(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		for (Boolean deterministic : new Boolean[] { true, false, null }) {
			Function function = new Function("constant_value");
			function.setDialect(dialect);
			function.getReturning().setDataType(DataType.INT);
			function.setDeterministic(deterministic);
			function.setSqlDataAccess("NO SQL");
			function.setSqlSecurity(SqlSecurity.Invoker);
			function.setRemarks("owner's constant");
			function.setStatement("RETURN 1");
			SqlFactory<Function> factory = dialect.createSqlFactoryRegistry().getSqlFactory(function, SqlType.CREATE);
			String sql = factory.createSql(function).get(0).getSqlText();
			assertTrue(sql.contains("RETURNS INT"), sql);
			assertTrue(sql.contains("SQL SECURITY INVOKER"), sql);
			assertTrue(sql.contains("COMMENT 'owner''s constant'"), sql);
			assertEquals(sql.indexOf("RETURN 1"), sql.lastIndexOf("RETURN 1"), sql);
			assertTrue(sql.indexOf("COMMENT") < sql.indexOf("RETURN 1"), sql);
			if (deterministic == null) {
				assertFalse(sql.contains("DETERMINISTIC"), sql);
			} else {
				assertTrue(sql.contains(deterministic ? "DETERMINISTIC" : "NOT DETERMINISTIC"), sql);
				assertEquals(!deterministic, sql.contains("NOT DETERMINISTIC"), sql);
				assertFalse(sql.contains("NOT\nNOT"), sql);
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void procedureSecurityAndCompleteDefinitions(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Procedure procedure = new Procedure("do_work");
		procedure.setDialect(dialect);
		procedure.setSqlSecurity(SqlSecurity.Definer);
		procedure.setStatement("BEGIN SELECT 1; END");
		SqlFactory<Procedure> procedureFactory = dialect.createSqlFactoryRegistry().getSqlFactory(procedure,
				SqlType.CREATE);
		String sql = procedureFactory.createSql(procedure).get(0).getSqlText();
		assertTrue(sql.contains("SQL SECURITY DEFINER"), sql);
		procedure.setDefinition("CREATE PROCEDURE `do_work`() SELECT 2");
		assertEquals("CREATE PROCEDURE `do_work`() SELECT 2",
				procedureFactory.createSql(procedure).get(0).getSqlText());

		Function function = new Function("constant_value");
		function.setDialect(dialect);
		function.setDefinition("CREATE FUNCTION `constant_value`() RETURNS INT RETURN 2");
		SqlFactory<Function> functionFactory = dialect.createSqlFactoryRegistry().getSqlFactory(function,
				SqlType.CREATE);
		assertEquals("CREATE FUNCTION `constant_value`() RETURNS INT RETURN 2",
				functionFactory.createSql(function).get(0).getSqlText());
	}

	@ParameterizedTest
	@CsvSource({ "5,5", "5,7", "8,0", "8,4", "9,0" })
	void triggerHasSeparatedTimingAndRejectsUnsupportedEvents(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Trigger trigger = new Trigger("before insert");
		trigger.setDialect(dialect);
		trigger.setTableName("order details");
		trigger.setActionTiming("BEFORE");
		trigger.addEventManipulation("INSERT");
		trigger.setStatement("SET NEW.id = 1");
		SqlFactory<Trigger> factory = dialect.createSqlFactoryRegistry().getSqlFactory(trigger, SqlType.CREATE);
		String sql = factory.createSql(trigger).get(0).getSqlText().replaceAll("\\s+", " ");
		assertEquals("CREATE TRIGGER `before insert` BEFORE INSERT ON `order details` FOR EACH ROW SET NEW.id = 1",
				sql);
		trigger.addEventManipulation("UPDATE");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
		trigger.getEventManipulation().clear();
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
		trigger.addEventManipulation("TRUNCATE");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
		trigger.getEventManipulation().clear();
		trigger.addEventManipulation("DELETE");
		trigger.setActionTiming("INSTEAD OF");
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(trigger));
		trigger.setDefinition("CREATE TRIGGER `before_insert` BEFORE INSERT ON `orders` FOR EACH ROW SET NEW.id = 2");
		assertEquals(trigger.getDefinition().get(0), factory.createSql(trigger).get(0).getSqlText());
	}

	private Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
	}
}
