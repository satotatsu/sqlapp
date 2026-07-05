/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

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
public class MySqlMergeByPkTableFactoryTest extends AbstractMySqlSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(new Table(), SqlType.MERGE_BY_PK);
	}

	@Test
	public void testMergeTable() throws ParseException {
		Table table1 = getTable1("tableA");
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(true);
		List<SqlOperation> operations = sqlFactory.createSql(table1);
		SqlOperation operation = CommonUtils.first(operations);
		String expected = """
				INSERT INTO `tableA` ( cola, colb, created_at, updated_at, lock_version )
				VALUES (/*cola*/0, /*colb*/'', current_timestamp, COALESCE(/*updated_at*/current_timestamp, current_timestamp), 0 )
				ON DUPLICATE KEY UPDATE colb = VALUES( colb ), updated_at = COALESCE(/*updated_at*/current_timestamp, current_timestamp), lock_version = COALESCE( VALUES ( lock_version ), 0) + 1
				""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	@Test
	public void testMergeTable2() throws ParseException {
		Table table1 = getTable1("tableA");
		sqlFactory.getTableOptions().setWithCoalesceAtUpdate(false);
		List<SqlOperation> operations = sqlFactory.createSql(table1);
		SqlOperation operation = CommonUtils.first(operations);
		String expected = """
				INSERT INTO `tableA` ( cola, colb, created_at, updated_at, lock_version )
				VALUES (/*cola*/0, /*colb*/'', current_timestamp, current_timestamp, 0 )
				ON DUPLICATE KEY UPDATE colb = VALUES( colb ), updated_at = current_timestamp, lock_version = COALESCE( lock_version , 0 ) + 1
				""";
		assertEquals(expected.trim(), operation.getSqlText().trim());
	}

	private Table getTable1(String tableName) throws ParseException {
		Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50);
		table.getColumns().add(column);
		column = new Column("created_at").setDataType(DataType.TIMESTAMP);
		table.getColumns().add(column);
		column = new Column("updated_at").setDataType(DataType.TIMESTAMP);
		table.getColumns().add(column);
		column = new Column("lock_version").setDataType(DataType.INT);
		table.getColumns().add(column);
		table.setPrimaryKey(table.getColumns().get("cola"));
		//
		Row row = table.newRow();
		row.put("cola", 1);
		row.put("colb", "bvalue");
		row.put("created_at", DateUtils.parse("2016-01-12 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		row.put("updated_at", DateUtils.parse("2016-12-31 12:32:30", "yyyy-MM-dd HH:mm:ss"));
		table.getRows().add(row);
		return table;
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}
}
