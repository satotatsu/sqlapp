/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.ForeignKeyConstraint.MatchOption;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のAlterコマンドテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class PostgresAlterTableSqlFactoryTest extends AbstractPostgresSqlFactoryTest {
	SqlFactory<Table> sqlFactory;

	@BeforeEach
	public void before() {
		sqlFactory = this.sqlFactoryRegistry.getSqlFactory(
				new Table(), State.Modified);
	}

	@Test
	public void testForeinfKey() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		Table table3 = getTable1("tableB");
		table3.getColumns().get("cola").setName("cola1");
		table3.getColumns().get("colb").setName("colb1");
		table2.getConstraints().addForeignKeyConstraint(
				"tablea_tableb_fk",
				new Column[] { table2.getColumns().get("colb"),
						table2.getColumns().get("cola") },
				new Column[] { table3.getColumns().get("cola1"),
						table3.getColumns().get("colb1") }).setDeleteRule(CascadeRule.Cascade).setMatchOption(MatchOption.Simple);
		DbObjectDifference diff=table1.diff(table2);
		List<SqlOperation> list = sqlFactory.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("alter_table_add_foreignKey1.sql");
		assertEquals(expected, operation.getSqlText());
		//
		list = sqlFactory.createDiffSql(diff.reverse());
		operation = CommonUtils.first(list);
		System.out.println(list);
		expected = getResource("alter_table_add_foreignKey1_reverse.sql");
		assertEquals(expected, operation.getSqlText());
	}
	
	@Test
	public void testRenameTable() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableB");
		DbObjectDifference diff=table1.diff(table2);
		List<SqlOperation> list = sqlFactory.createDiffSql(diff);
		SqlOperation operation = CommonUtils.first(list);
		System.out.println(list);
		String expected = getResource("rename_Table1.sql");
		assertEquals(expected, operation.getSqlText());
		//
		list = sqlFactory.createDiffSql(diff.reverse());
		operation = CommonUtils.first(list);
		System.out.println(list);
		expected = getResource("rename_Table1_reverse.sql");
		assertEquals(expected, operation.getSqlText());
	}

	private Table getTable(String tableName) {
		Table table = new Table(tableName);
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
		column = new Column("cold").setDataType(DataType.INT);
		table.getColumns().add(column);
		table.getSpecifics().put("oids", "false");
		return table;
	}

	/**
	 * AUTO_INCREMENTテスト
	 */
	@Test
	public void testGetDdlTableTableAutoIncrement() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().get("cola").setIdentity(true)
				.setIdentityLastValue(10);
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		System.out.println(list);
		assertEquals(
				"ALTER TABLE \"tableA\" ALTER COLUMN cola serial",
				commandText.getSqlText());
	}

	/**
	 * Primary Key制約テスト1
	 */
	@Test
	public void testGetDdlTableTablePrimaryKey1() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table1.getConstraints().addPrimaryKeyConstraint("pk1",
				table1.getColumns().get("cola"));
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		SqlOperation commandText = CommonUtils.first(list);
		assertEquals("ALTER TABLE \"tableA\" DROP CONSTRAINT pk1",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Primary Key制約テスト2
	 */
	@Test
	public void testGetDdlTableTablePrimaryKey2() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getConstraints().addPrimaryKeyConstraint("pk1",
				table2.getColumns().get("cola"));
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		int i=0;
		SqlOperation commandText = list.get(i++);
		assertEquals(
				"ALTER TABLE \"tableA\" ADD CONSTRAINT pk1 PRIMARY KEY ( cola )",
				commandText.getSqlText());
		System.out.println(list);
	}

	/**
	 * Indexテスト1
	 */
	@Test
	public void testGetDdlAddIndex1AndDefault() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().add(new Column("d").setDataType(DataType.INT).setNotNull(true).setDefaultValue("0"));
		table1.getIndexes().add("index1", table1.getColumns().get("cola"));
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		int i=0;
		System.out.println(list);
		SqlOperation operation = list.get(i++);
		String expected = getResource("alter_table_drop_index1.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_add_column1.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_add_column2.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_add_column3.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_add_column4.sql");
		assertEquals(
				expected,
				operation.getSqlText());
	}

	/**
	 * Indexテスト1
	 */
	@Test
	public void testGetDdlAlterTableProperties() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getSpecifics().put("oids", "true");
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		int i=0;
		System.out.println(list);
		SqlOperation operation = list.get(i++);
		String expected = getResource("alter_table_set_with_oids.sql");
		assertEquals(
				expected,
				operation.getSqlText());
	}
	
	/**
	 * Indexテスト1
	 */
	@Test
	public void testGetDdlAlterDefault() {
		Table table1 = getTable1("tableA");
		Table table2 = getTable1("tableA");
		table2.getColumns().get("cold").setNotNull(true).setDefaultValue("0");
		List<SqlOperation> list = sqlFactory.createDiffSql(table1.diff(table2));
		int i=0;
		System.out.println(list);
		SqlOperation operation = list.get(i++);
		String expected = getResource("alter_table_modify_column1.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_modify_column2.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_modify_column3.sql");
		assertEquals(
				expected,
				operation.getSqlText());
		operation = list.get(i++);
		expected = getResource("alter_table_modify_column4.sql");
		assertEquals(
				expected,
				operation.getSqlText());
	}

}
