/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParseException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DateUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2008MergeRowFactoryTest extends AbstractSqlServer11SqlFactoryTest {
	SqlFactory<Row> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(
				new Row(), SqlType.MERGE_ROW);
		sqlFactory.getOptions().getTableOptions().setDmlBatchSize(10);
	}

	@Test
	public void testMergeRow1() throws ParseException {
		final Table table1 = getTable1("tableA");
		final List<SqlOperation> operations=sqlFactory.createSql(table1.getRows());
		final SqlOperation operation=CommonUtils.first(operations);
		final String expected = getResource("merge_row1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testMergeRow2() throws ParseException {
		final Table table = getTable1("tableA");
		final Row row=table.newRow();
		row.put("cola", 2);
		row.put("colb", "value2");
		row.put("colc", DateUtils.parse("2017-01-15 14:32:30", "yyyy-MM-dd HH:mm:ss"));
		table.getRows().add(row);
		final List<SqlOperation> operations=sqlFactory.createSql(table.getRows());
		final SqlOperation operation=CommonUtils.first(operations);
		final String expected = getResource("merge_row2.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	@Test
	public void testMergeRow3() throws ParseException {
		final Table table = getTable1("tableA");
		final Row row=table.newRow();
		row.put("cola", 2);
		row.put("colb", "value2");
		row.put("colc", DateUtils.parse("2017-01-15 14:32:30", "yyyy-MM-dd HH:mm:ss"));
		table.getRows().add(row);
		sqlFactory.getOptions().getTableOptions().setWithCoalesceAtUpdate(c->{
			if ("colb".equals(c.getName())){
				return true;
			}
			return false;
		});
		final List<SqlOperation> operations=sqlFactory.createSql(table.getRows());
		final SqlOperation operation=CommonUtils.first(operations);
		final String expected = getResource("merge_row3.sql");
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
		table.setPrimaryKey(table.getColumns().get("cola"));
		//
		final Row row=table.newRow();
		row.put("cola", 1);
		row.put("colb", "bvalue");
		row.put("colc", DateUtils.parse("2016-01-12 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		table.getRows().add(row);
		return table;
	}

	private Table getTable(final String tableName) {
		final Table table = new Table(tableName);
		return table;
	}
}
