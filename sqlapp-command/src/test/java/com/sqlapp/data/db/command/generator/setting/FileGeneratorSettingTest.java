/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.command.generator.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

class FileGeneratorSettingTest {

	@Test
	void test() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableGeneratorSettingFactory factory = new TableGeneratorSettingFactory();
		Table table = createTable();
		TableGeneratorSetting setting = factory.createDefault(table, dialect);
		FileGeneratorSetting fileSetting = CommonUtils.first(setting.getFiles()).getValue();
		fileSetting.loadData();
		Map<String, Object> map = fileSetting.getValueMap(0);
		System.out.println(map);
		assertEquals("1", map.get("col_a"));
		assertEquals("1", map.get("col_A"));
		assertEquals(2, map.get("col_b"));
		assertEquals("cccc", map.get("col_c"));
		assertEquals("cccc", map.get("Col_C"));
		map = fileSetting.getValueMap(1);
		System.out.println(map);
		assertEquals("3", map.get("col_a"));
		assertEquals(4, map.get("col_b"));
		assertEquals("cccc", map.get("col_c"));
	}

	private Table createTable() {
		Table table = new Table("tableA");
		table.getColumns().add(c -> {
			c.setName("colA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colB");
			c.setDataType(DataType.VARCHAR);
			c.setLength(10);
		});
		table.getConstraints().addPrimaryKeyConstraint("PK_" + table.getName(), table.getColumns().get("colA"));
		return table;
	}

}
