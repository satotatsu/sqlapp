/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.test.AbstractTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;

class TableGeneratorSettingFactoryTest extends AbstractTest {

	@Test
	void testSetupFinalizeSql1() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Microsoft SQLServer", 0, 0, null);
		TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setIdentity(true);
			c.setDataType(DataType.INT);
		});
		TableGeneratorSetting setting = factory.createDefault(table, dialect);
		assertEquals("iterator(100)", setting.getDataSourceExpression());
		assertEquals("[\"_index\":value]", setting.getColumnMappingExpression());
		String startCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(startCountSql, setting.getStartCountSql());
		String startValueSql = """
				SELECT
					COALESCE( MAX( ID ), 0 ) AS ID
				FROM TAB1""";
		assertEquals(startValueSql, setting.getStartValueSql());
		String setupSql = "--SET IDENTITY_INSERT TAB1 ON";
		assertEquals(setupSql, setting.getSetupSql());
		String finalize = "--SET IDENTITY_INSERT TAB1 OFF";
		assertEquals(finalize, setting.getFinalizeSql());
		String finishCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(finishCountSql, setting.getFinishCountSql());
	}

	@Test
	void testSetupFinalizeSql2() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setIdentity(true);
			c.setDataType(DataType.INT);
		});
		TableGeneratorSetting setting = factory.createDefault(table, dialect);
		String startCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(startCountSql, setting.getStartCountSql());
		String startValueSql = """
				SELECT
					COALESCE( MAX( ID ), 0 ) AS ID
				FROM TAB1""";
		assertEquals(startValueSql, setting.getStartValueSql());
		assertNull(setting.getSetupSql());
		assertNull(setting.getFinalizeSql());
		String finishCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(finishCountSql, setting.getFinishCountSql());
	}

	@Test
	void testSetupFinalizeSql3() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Microsoft SQLServer", 0, 0, null);
		TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		TableGeneratorSetting setting = factory.createDefault(table, dialect);
		String startCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(startCountSql, setting.getStartCountSql());
		String startValueSql = """
				SELECT
					COALESCE( MAX( ID ), 0 ) AS ID
				FROM TAB1""";
		assertEquals(startValueSql, setting.getStartValueSql());
		assertNull(setting.getSetupSql());
		assertNull(setting.getFinalizeSql());
		String finishCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(finishCountSql, setting.getFinishCountSql());
	}

	@Test
	void testSetupFinalizeSql4() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		TableGeneratorSetting setting = factory.createDefault(table, dialect);
		String startCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(startCountSql, setting.getStartCountSql());
		String startValueSql = """
				SELECT
					COALESCE( MAX( ID ), 0 ) AS ID
				FROM TAB1""";
		assertEquals(startValueSql, setting.getStartValueSql());
		assertNull(setting.getSetupSql());
		assertNull(setting.getFinalizeSql());
		String finishCountSql = """
				SELECT
				COUNT(*)
				FROM TAB1""";
		assertEquals(finishCountSql, setting.getFinishCountSql());
	}
}
