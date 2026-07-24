/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.util.CommonUtils;

class TableRelationTreeHolderTest {

	@Test
	void test() {
		TableRelationTreeHolder holder = new TableRelationTreeHolder(getTables());
		TableRelation tableRelation = holder.findFirst(trel -> "tabA".equals(trel.getTable().getName())).get();
		assertNull(tableRelation.getParent());
		assertEquals(2, tableRelation.getChildren().size());
		int i = 0;
		assertEquals("tabB", tableRelation.getChildren().get(i++).getTable().getName());
		assertEquals("tabD", tableRelation.getChildren().get(i++).getTable().getName());

		for (TableRelation tableRel : holder.getRelationTree().values()) {
			System.out.println("tableRel=" + tableRel);
		}
	}

	private List<Table> getTables() {
		List<Table> list = CommonUtils.list();
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		table.setPrimaryKey(table.getColumns().get("ID"));
		list.add(table);
		//
		Table tableb = new Table("tabB");
		tableb.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tableb.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tableb.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tableb, table, "PARENT_ID");
		list.add(tableb);
		//
		Table tablec = new Table("tabC");
		tablec.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tablec.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tablec.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tablec.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tablec, tableb, "PARENT_ID");
		list.add(tablec);
		//
		Table tabled = new Table("tabD");
		tabled.getColumns().add(c -> {
			c.setName("ID");
			c.setDataType(DataType.INT);
		});
		tabled.getColumns().add(c -> {
			c.setName("PARENT_ID");
			c.setDataType(DataType.INT);
		});
		tabled.getColumns().add(c -> {
			c.setName("TXT");
			c.setDataType(DataType.VARCHAR);
			c.setLength(15);
		});
		tabled.setPrimaryKey(table.getColumns().get("ID"));
		addForeignKey(tabled, table, "PARENT_ID");
		list.add(tabled);
		return list;
	}

	private void addForeignKey(Table table, Table parent, String columnName) {
		String parentColumnName = CommonUtils.first(parent.getPrimaryKeyConstraint().getColumns()).getName();
		table.getConstraints().addForeignKeyConstraint("fk_" + table.getName(), table.getColumns().get(columnName),
				parent.getColumns().get(parentColumnName));

	}

}
