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

package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;
import com.sqlapp.util.FileUtils;

public class InsertRowFactoryTest extends AbstractStandardFactoryTest {
	SqlFactory<Row> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = sqlFactoryRegistry.getSqlFactory(
				new Row(), SqlType.INSERT_ROW);
		final Options option=new Options();
		option.getTableOptions().setWithCoalesceAtUpdate(true);
		sqlFactory.setOptions(option);
		option.getTableOptions().setTableComment(t->t.getDisplayName());
	}

	@Test
	public void testMergeRow() throws ParseException {
		final Table table1 = getTable1("tableA");
		final List<SqlOperation> operations=sqlFactory.createSql(table1.getRows());
		final SqlOperation operation=CommonUtils.first(operations);
		final String expected = FileUtils.getResource(this, "insert_row1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	
	private Table getTable1(final String tableName) throws ParseException {
		final Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50);
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		column = new Column("cold").setDataType(DataType.INT).setNotNull(true).setDefaultValue("1");
		table.getColumns().add(column);
		table.setPrimaryKey(table.getColumns().get("cola"));
		//
		final Row row=table.newRow();
		row.put("cola", 1);
		row.put("colb", "bvalue");
		row.put("colc", DateUtils.parse("2016-01-12 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		row.put("cold", 1);
		table.getRows().add(row);
		return table;
	}

	private Table getTable(final String tableName) {
		final Table table = new Table(tableName);
		table.setDisplayName("テーブル_"+tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}
}
