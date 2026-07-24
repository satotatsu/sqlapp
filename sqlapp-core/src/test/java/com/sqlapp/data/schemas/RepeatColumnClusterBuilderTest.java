package com.sqlapp.data.schemas;

/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.RepeatColumnClusterBuilder.RepeatColumnCluster;

class RepeatColumnClusterBuilderTest {

	@Test
	void test() {
		Table table = createTable();
		List<RepeatColumnCluster> clusters = RepeatColumnClusterBuilder.of(table).build();
		assertEquals(1, clusters.size());
		for (RepeatColumnCluster cluster : clusters) {
			assertEquals(31, cluster.getRepeatCount());
			assertEquals(2, cluster.getColumns().size());
			int i = 0;
			RepeatColumn repeatColumn = cluster.getColumns().get(i++);
			assertEquals("DATE", repeatColumn.getBaseName());
			Column column = repeatColumn.firstColumn();
			assertEquals(DataType.DATE, column.getDataType());
			//
			repeatColumn = cluster.getColumns().get(i++);
			assertEquals("ITEM", repeatColumn.getBaseName());
			column = repeatColumn.firstColumn();
			assertEquals(DataType.VARCHAR, column.getDataType());
		}
	}

	@Test
	void testMinimumColumnCount() {
		Table table = new Table("tabA");
		table.getColumns().add(new Column("VALUE_1").setDataType(DataType.INT));
		table.getColumns().add(new Column("VALUE_2").setDataType(DataType.INT));

		assertEquals(0, RepeatColumnClusterBuilder.of(table).build().size());
		assertEquals(1, RepeatColumnClusterBuilder.of(table).minimumColumnCount(1).build().size());
		assertThrows(IllegalArgumentException.class,
				() -> RepeatColumnClusterBuilder.of(table).minimumColumnCount(0));
	}

	public Table createTable() {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		int[] cnt = new int[1];
		for (int i = 0; i < 31; i++) {
			cnt[0] = i + 1;
			table.getColumns().add(c -> {
				c.setName("DATE_" + cnt[0]);
				c.setDataType(DataType.DATE);
			});
		}
		for (int i = 0; i < 31; i++) {
			cnt[0] = i + 1;
			table.getColumns().add(c -> {
				c.setName("ITEM_" + cnt[0]);
				c.setDataType(DataType.VARCHAR);
			});
		}
		return table;
	}
}
