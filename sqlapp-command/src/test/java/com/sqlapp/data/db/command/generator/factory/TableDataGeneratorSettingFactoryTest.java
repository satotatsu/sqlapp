package com.sqlapp.data.db.command.generator.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.setting.TableDataGeneratorSetting;
import com.sqlapp.data.db.command.test.AbstractTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;

class TableDataGeneratorSettingFactoryTest extends AbstractTest {

	@Test
	void testSetupFinalizeSql1() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Microsoft SQLServer", 0, 0, null);
		TableDataGeneratorSettingFactory factory = new TableDataGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setIdentity(true);
			c.setDataType(DataType.INT);
		});
		TableDataGeneratorSetting setting = factory.createDefault(table, dialect);
		assertEquals(this.getResource("setup1.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize1.sql"), setting.getFinalizeSql());
	}

	@Test
	void testSetupFinalizeSql2() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableDataGeneratorSettingFactory factory = new TableDataGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setIdentity(true);
			c.setDataType(DataType.INT);
		});
		TableDataGeneratorSetting setting = factory.createDefault(table, dialect);
		assertEquals(this.getResource("setup2.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize2.sql"), setting.getFinalizeSql());
	}

	@Test
	void testSetupFinalizeSql3() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Microsoft SQLServer", 0, 0, null);
		TableDataGeneratorSettingFactory factory = new TableDataGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		TableDataGeneratorSetting setting = factory.createDefault(table, dialect);
		assertEquals(this.getResource("setup3.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize3.sql"), setting.getFinalizeSql());
	}

	@Test
	void testSetupFinalizeSql4() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableDataGeneratorSettingFactory factory = new TableDataGeneratorSettingFactory();
		Table table = new Table("TAB1");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		TableDataGeneratorSetting setting = factory.createDefault(table, dialect);
		assertEquals(this.getResource("setup3.sql"), setting.getSetupSql());
		assertEquals(this.getResource("finalize3.sql"), setting.getFinalizeSql());
	}
}
