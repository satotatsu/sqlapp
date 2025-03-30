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
		assertEquals(this.getResource("startValue1.sql"), setting.getStartValueSql());
		assertEquals(this.getResource("setup1.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize1.sql"), setting.getFinalizeSql());
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
		assertEquals(this.getResource("startValue1.sql"), setting.getStartValueSql());
		assertEquals(this.getResource("setup2.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize2.sql"), setting.getFinalizeSql());
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
		assertEquals(this.getResource("startValue1.sql"), setting.getStartValueSql());
		assertEquals(this.getResource("setup3.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize3.sql"), setting.getFinalizeSql());
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
		assertEquals(this.getResource("startValue1.sql"), setting.getStartValueSql());
		assertEquals(this.getResource("setup3.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize3.sql"), setting.getFinalizeSql());
	}
}
