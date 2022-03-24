/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlAlterTableFactoryTest2 extends AbstractMySqlSqlFactoryTest {
	SqlFactory<Table> operation;

	@BeforeEach
	public void before() {
		operation = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}

	@Test
	public void testGetDdlAlterColumnOrder1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable2("tableA");
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_column1.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	private Table getTable(String tableName) {
		Table table = new Table(tableName);
		table.getSpecifics().put("ENGINE", "innodb");
		return table;
	}

	private Table getTable1(String tableName) {
		Table table = getTable(tableName);
		Column column = new Column("cola").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(column);
		column = new Column("colb").setDataType(DataType.VARCHAR).setLength(50)
				.setCharacterSet("utf8").setCollation("utf8mb4_binary");
		table.getColumns().add(column);
		column = new Column("colc").setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		return table;
	}

	private Table getTable2(String tableName) {
		Table table = getTable1(tableName);
		Column column = new Column("cold").setDataType(DataType.BIGINT).setNotNull(true);
		table.getColumns().add(1, column);
		return table;
	}

	@Test
	public void testGetDdlAlterColumnOrder2() {
		Table table1 = getTable1("tableA");
		Column column=table1.getColumns().get(0);
		table1.getColumns().remove(0);
		table1.getColumns().add(table1.getColumns().size()-1, column);
		Table table2 = getTable2("tableA");
		List<SqlOperation> list = operation.createDiffSql(table1.diff(table2));
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_column2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	
	@Test
	public void testGetDdlAlterColumnOrder3() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable2("tableA");
		Column column=table2.getColumns().get(0);
		table2.getColumns().remove(0);
		table2.getColumns().add(column);
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_column3.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	@Test
	public void testGetDdlDropIndex1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getIndexes().add("idx1", table1.getColumns().get(1));
		table1.getIndexes().add("idx2", table1.getColumns().get(2));
		table1.getConstraints().add(new UniqueConstraint("idx2", table1.getColumns().get(2)));
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_drop_index1.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testGetDdlDropIndex2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getIndexes().add("idx1", table1.getColumns().get(1));
		table1.getIndexes().add("idx2", table1.getColumns().get(2));
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_drop_index2.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testGetDdlDropIndex3() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getIndexes().add("idx1", table1.getColumns().get(1));
		table1.getConstraints().add(new UniqueConstraint("idx2", table1.getColumns().get(2)));
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_drop_index3.sql");
		assertEquals(expected, operation.getSqlText());
	}

	@Test
	public void testGetAddConstraint() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getConstraints().add(new UniqueConstraint("idx2", table1.getColumns().get(2)));
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_add_constraint1.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	@Test
	public void testGetAddConstraint2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getConstraints().add(new UniqueConstraint("idx2", true, table1.getColumns().get(2)));
		DbObjectDifference diff=table1.diff(table2);
		System.out.println(diff);
		List<SqlOperation> list = operation.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_add_constraint2.sql");
		assertEquals(expected, operation.getSqlText());
	}

}
