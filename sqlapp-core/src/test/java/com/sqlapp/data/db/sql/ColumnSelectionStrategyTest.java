/**
 * Copyright (C) 2026-2027 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core. If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ColumnSelectionStrategyTest {

	private static final Dialect dialect = DialectResolver.getInstance().getDefaultDialect();

	private Table getTable() {
		Table table = new Table("tabA");
		table.getColumns().add(c -> {
			c.setName("colA");
			c.setDataType(DataType.INT);
		});
		table.getColumns().add(c -> {
			c.setName("colB");
			c.setDataType(DataType.VARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colC");
			c.setDataType(DataType.BIGINT);
		});
		table.getColumns().add(c -> {
			c.setName("colD");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI2");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI3");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colI4");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colP1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colP2");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colU1");
			c.setDataType(DataType.NVARCHAR);
		});
		table.getColumns().add(c -> {
			c.setName("colU2");
			c.setDataType(DataType.NVARCHAR);
		});
		return table;
	}

	private Table getPkUkIndexTable() {
		Table table = getTable();
		addPK(table);
		addUks(table);
		addIndexes(table);
		return table;
	}

	private Table getPkTable() {
		Table table = getTable();
		addPK(table);
		return table;
	}

	private void addPK(Table table) {
		table.setPrimaryKey(table.getColumns().get("colP1"), table.getColumns().get("colP2"));
	}

	private Table getUkTable() {
		Table table = getTable();
		addUks(table);
		return table;
	}

	private void addUks(Table table) {
		table.getConstraints().addUniqueConstraint(uc -> {
			uc.setName("UK_" + table.getName() + "1");
			uc.getColumns().add(table.getColumns().get("colU1"));
		});
		table.getConstraints().addUniqueConstraint(uc -> {
			uc.setName("UK_" + table.getName() + "2");
			uc.getColumns().add(table.getColumns().get("colU2"));
		});
	}

	private Table getIndexTable() {
		Table table = getTable();
		addIndexes(table);
		return table;
	}

	private void addIndexes(Table table) {
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "1");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI1");
			column.setNotNull(true);
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "2");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI2");
			column.setNotNull(true);
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "3");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI3");
			idx.getColumns().add(column);
		});
		table.getIndexes().add(idx -> {
			idx.setName("IDX_" + table.getName() + "4");
			idx.setUnique(true);
			Column column = table.getColumns().get("colI4");
			column.setNotNull(false);
			idx.getColumns().add(column);
		});
	}

}
