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

package com.sqlapp.data.db.command.generator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.generator.config.FileGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.strategy.ValueSelectStrategy;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

class FileGeneratorConfigTest {

	@Test
	void test() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();
		Table table = createTable();
		TableGeneratorConfig config = factory.createDefault(table, dialect);
		FileGeneratorConfig fileConfig = CommonUtils.first(config.getFiles()).getValue();
		fileConfig.setSelectionStrategy(ValueSelectStrategy.NEXT_VALUE);
		fileConfig.loadData();
		Map<String, Object> map = fileConfig.getValueMap(0);
		System.out.println(map);
		assertEquals("GBR", map.get("col_a"));
		assertEquals("GBR", map.get("col_A"));
		assertEquals("United Kingdom", map.get("col_b"));
		assertEquals("GBR_United Kingdom", map.get("col_c"));
		assertEquals("GBR_United Kingdom", map.get("Col_C"));
		map = fileConfig.getValueMap(1);
		System.out.println(map);
		assertEquals("FRA", map.get("col_a"));
		assertEquals("France", map.get("col_b"));
		assertEquals("FRA_France", map.get("col_c"));
	}

	@Test
	void test2() {
		Dialect dialect = DialectResolver.getInstance().getDialect("HSQL", 0, 0, null);
		TableGeneratorConfigFactory factory = new TableGeneratorConfigFactory();
		Table table = createTable();
		TableGeneratorConfig config = factory.createDefault(table, dialect);
		FileGeneratorConfig fileConfig = CommonUtils.first(config.getFiles()).getValue();
		fileConfig.loadData();
		int usa = 0;
		int others = 0;
		for (int i = 0; i < 300; i++) {
			Map<String, Object> map = fileConfig.getValueMap(1);
			if ("USA".equals(map.get("col_a"))) {
				usa++;
			} else {
				others++;
			}
		}
		System.out.println("USA COUNT=" + usa);
		System.out.println("OTHERS COUNT=" + others);
		assertTrue(usa > others);
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
